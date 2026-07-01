package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.domain.InstrumentType
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/**
 * RealPricingEngineClient 단위테스트 — JDK 내장 HttpServer 로 가짜 엔진 응답을 서빙.
 *   - 엔진 응답(JSON) → PricingResult 파싱, job_id/instrument_id 권위값 세팅.
 *   - input_hash echo(요청의 input_hash 를 응답이 되돌려줌) → 재현성 정합.
 *   - key_parameters 의 미지 키(credit_spread 등)는 무시(FAIL_ON_UNKNOWN=false).
 *   - 연결 실패 → EngineCallException.
 */
class RealPricingEngineClientTest {

    // 프로덕션 Spring ObjectMapper 와 동일: 미지 키(credit_spread 등) 무시.
    private val mapper = jacksonObjectMapper()
        .apply { configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) }

    private fun instrument() = InstrumentEntity(
        orgId = 1, type = InstrumentType.CB, name = "테스트CB", issuer = "발행사", id = 42,
    )

    private fun ctx(inputHash: String): ResolvedContext {
        val node = mapper.createObjectNode()
        node.put("instrument_type", "CB")
        node.put("model", "TF_LATTICE")
        node.put("input_hash", inputHash)
        return ResolvedContext(
            type = InstrumentType.CB, valuationDate = "2022-10-13", model = "TF_LATTICE",
            seed = 20240101, modelVersion = "cb-1.0.0", inputHash = inputHash, contextJson = node,
        )
    }

    @Test
    fun `엔진 응답 파싱 - 12키·input_hash echo·job_id 권위값`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/price") { ex ->
            val reqBody = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val ih = mapper.readTree(reqBody).get("input_hash").asText()
            // ★ 응답: 실계산값 모사 + 요청 input_hash echo + key_parameters 에 미지 키(credit_spread)
            val resp = """
                {"job_id":0,"instrument_id":0,"instrument_type":"CB","valuation_date":"2022-10-13",
                 "status":"DONE","total_fair_value":10394.67,"per_unit_value":10394.67,
                 "components":{"bond_value":4620.84,"preferred_share_value":null,"conversion_option_value":4326.72,
                   "exchange_option_value":null,"warrant_value":null,"redemption_option_value":1447.12,
                   "issuer_call_value":0.0,"sale_claim_value":0.0,"stock_option_value":null,
                   "conditional_option_value":null,"dilution_effect":0.0,"total_fair_value":10394.67},
                 "key_parameters":{"model_name":"TF_LATTICE","model_version":"cb-1.0.0","risk_free_rate":4.21,
                   "volatility":50.0,"credit_spread":18.5,"parity":8000.0,"lattice_steps":300},
                 "reproducibility":{"input_hash":"$ih","seed":20240101,"model_version":"cb-1.0.0",
                   "computed_at":"2022-10-13T00:00:00Z"},
                 "warnings":[],"errors":[]}
            """.trimIndent().toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        server.start()
        try {
            val client = RealPricingEngineClient(mapper, "http://localhost:${server.address.port}")
            val r = client.price(ctx("hash_abc_64hex"), instrument(), jobId = 99)

            assertEquals(99, r.jobId, "job_id 권위값")
            assertEquals(42, r.instrumentId, "instrument_id 권위값")
            assertEquals("CB", r.instrumentType)
            assertEquals(10394.67, r.totalFairValue)
            assertEquals(4620.84, r.components.bondValue)
            assertEquals(4326.72, r.components.conversionOptionValue)
            assertEquals(1447.12, r.components.redemptionOptionValue)
            assertEquals("hash_abc_64hex", r.reproducibility.inputHash, "input_hash echo 정합")
            assertEquals(50.0, r.keyParameters.volatility)   // 채워짐(placeholder null 아님)
            assertTrue(r.warnings.isEmpty(), "PLACEHOLDER 없음")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `엔진 연결 실패 - EngineCallException`() {
        // 사용하지 않는 포트(연결 거부) → 예외 전파(JobService 가 FAILED 로).
        val client = RealPricingEngineClient(mapper, "http://localhost:1")
        assertThrows(EngineCallException::class.java) {
            client.price(ctx("h"), instrument(), jobId = 1)
        }
    }
}
