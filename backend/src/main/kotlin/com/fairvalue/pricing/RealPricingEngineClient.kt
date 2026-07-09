package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.dto.PricingResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * ★ 실제 Python 파이싱 엔진(FastAPI) 호출 클라이언트 (Phase 5-2, Dummy→Real).
 *   - ResolvedContext.contextJson(ValuationContext) 을 ★그대로 POST {base-url}/price (어댑터 없음).
 *     RealContextResolver 산출 키가 엔진 registry.calculate 입력과 정합함이 확인됨.
 *   - 응답 PricingResult(JSON) 을 DTO 로 파싱. job_id/instrument_id 는 권위값으로 세팅.
 *   - input_hash: contextJson 에 담긴 백엔드 산출 해시를 엔진이 echo → 재현성 정합(엔진 재계산 안 함).
 *   - 엔진 연결 실패/4xx/5xx → 예외 → JobService 가 Job FAILED 로 전파(자리표시 0 대신 실제 에러).
 *
 * app.engine.mode=real(기본) 일 때 주입. mode=dummy 면 Dummy(테스트). Dummy 는 삭제 안 함(폴백/테스트용).
 * base-url 은 app.engine.base-url 프로퍼티(기본 http://localhost:8000).
 * HTTP 는 JDK 내장 java.net.http(새 의존성 없음).
 */
@Component
@ConditionalOnProperty(prefix = "app.engine", name = ["mode"], havingValue = "real", matchIfMissing = true)
class RealPricingEngineClient(
    private val mapper: ObjectMapper,
    @Value("\${app.engine.base-url:http://localhost:8000}") private val baseUrl: String,
) : PricingEngineClient {

    private val http: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
	.build()

    private val log = LoggerFactory.getLogger(RealPricingEngineClient::class.java)

    override fun price(context: ResolvedContext, instrument: InstrumentEntity, jobId: Long): PricingOutcome {
        // 식별자 주입(엔진 echo). contextJson 은 그대로 전송(input_hash 포함 — resolver 가 주입).
        val ctx: ObjectNode = context.contextJson.deepCopy()
        ctx.put("job_id", jobId)
        ctx.put("instrument_id", instrument.id!!)
        val body = mapper.writeValueAsString(ctx)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/price"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response: HttpResponse<String> = try {
            http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            throw EngineCallException("파이싱 엔진 호출 실패($baseUrl/price): ${e.message}", e)
        }

        if (response.statusCode() !in 200..299) {
            throw EngineCallException(
                "파이싱 엔진 오류 HTTP ${response.statusCode()}: ${response.body().take(500)}",
            )
        }

        // ★수정 A: 원문 보존. 미지 옵션 키(trees·curve_bootstrap·sensitivity 등)를 소실시키지 않는다.
        val raw: ObjectNode = try {
            mapper.readTree(response.body()) as ObjectNode
        } catch (e: Exception) {
            throw EngineCallException("엔진 응답 파싱 실패: ${e.message}", e)
        }
        // 식별자는 백엔드 권위값으로 확정(원문에도 반영해 저장 일관성 유지).
        raw.put("job_id", jobId)
        raw.put("instrument_id", instrument.id!!)
        raw.put("instrument_type", instrument.type.name)

        // 검증·total 추출용 타입 파싱(동작 불변). 실패 시 엔진 오류.
        val parsed = try {
            mapper.treeToValue(raw, PricingResult::class.java)
        } catch (e: Exception) {
            throw EngineCallException("엔진 응답 스키마 불일치: ${e.message}", e)
        }

        // ★수정 B: input_hash echo 검증(하드 실패 금지 — 관측 우선). 불일치 시 로그 + warnings 기록.
        val echoed = raw.path("reproducibility").path("input_hash").asText("")
        if (echoed != context.inputHash) {
            log.warn("input_hash echo 불일치 job={} 주입={} echo={}", jobId, context.inputHash, echoed)
            (raw.withArray("warnings")).add(
                mapper.createObjectNode()
                    .put("code", "W301")
                    .put("message", "엔진 echo input_hash 불일치(주입=${context.inputHash}, echo=$echoed)")
                    .put("stage", "engine"),
            )
        }
        return PricingOutcome(raw = raw, result = parsed)
    }
}

/** 엔진 호출/응답 실패. JobService 가 catch → Job FAILED 로 전파. */
class EngineCallException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
