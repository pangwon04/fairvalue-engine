package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.dto.PricingResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
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
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override fun price(context: ResolvedContext, instrument: InstrumentEntity, jobId: Long): PricingResult {
        // 식별자 주입(엔진 echo). contextJson 은 그대로 전송.
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

        val parsed = try {
            mapper.readValue(response.body(), PricingResult::class.java)
        } catch (e: Exception) {
            throw EngineCallException("엔진 응답 파싱 실패: ${e.message}", e)
        }

        // job_id/instrument_id/type 은 백엔드 권위값으로 확정.
        return parsed.copy(
            jobId = jobId,
            instrumentId = instrument.id!!,
            instrumentType = instrument.type.name,
        )
    }
}

/** 엔진 호출/응답 실패. JobService 가 catch → Job FAILED 로 전파. */
class EngineCallException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
