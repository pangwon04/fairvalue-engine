package com.fairvalue.pricing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Pricing Job 골격 통합테스트 — Testcontainers PostgreSQL + Flyway V1/V2.
 * HTTP 클라이언트는 1-B-1/1-B-2 와 동일(JDK java.net.http, 4xx 예외 없음).
 *
 * 입증: price→job→placeholder result(12 key·Σ=0·PLACEHOLDER) · cached 재요청 ·
 *       조직 격리(404) · VIEWER 403 · input_hash 64-hex 결정성.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PricingJobIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16")).apply {
                withDatabaseName("fairvalue"); withUsername("fairvalue"); withPassword("fairvalue")
            }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("jwt.secret") { "integration-test-secret-long-enough-32bytes!!" }
        }
    }

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mapper: ObjectMapper
    private val client: HttpClient = HttpClient.newHttpClient()

    private fun b(path: String, token: String?): HttpRequest.Builder {
        val r = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).header("Content-Type", "application/json")
        if (token != null) r.header("Authorization", "Bearer $token")
        return r
    }
    private fun body(v: Any) = HttpRequest.BodyPublishers.ofString(if (v is String) v else mapper.writeValueAsString(v))
    private fun post(path: String, payload: Any, token: String?) =
        client.send(b(path, token).POST(body(payload)).build(), HttpResponse.BodyHandlers.ofString())
    private fun put(path: String, payload: Any, token: String?) =
        client.send(b(path, token).method("PUT", body(payload)).build(), HttpResponse.BodyHandlers.ofString())
    private fun get(path: String, token: String?) =
        client.send(b(path, token).GET().build(), HttpResponse.BodyHandlers.ofString())
    private fun patch(path: String, payload: Any, token: String?) =
        client.send(b(path, token).method("PATCH", body(payload)).build(), HttpResponse.BodyHandlers.ofString())

    private fun uniq(p: String) = "$p-${System.nanoTime()}"
    private fun adminToken(org: String = uniq("ORG")): String {
        val res = post("/auth/signup", mapOf("email" to "${uniq("a")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("token").asText()
    }
    private fun createInstrument(token: String, type: String): Long {
        val res = post("/instruments", mapOf("type" to type, "name" to "$type-1", "issuer" to "issuer"), token)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("id").asLong()
    }
    private fun validCb(): Map<String, Any> = mapOf(
        "valuation_date" to "2024-06-26",
        "metadata" to mapOf("issuer" to "예시바이오", "instrument_name" to "CB1"),
        "terms" to mapOf("issue_date" to "2023-09-13", "maturity_date" to "2026-09-13",
            "issue_amount" to 3000000000L, "face_value" to 10000, "coupon_rate" to 2, "coupon_freq_month" to 3),
        "rights" to mapOf("conversion" to mapOf("strike" to 3260, "start" to "2024-09-13"), "refixing" to mapOf("enabled" to false)),
        "market" to mapOf("asset_id" to 880, "volatility" to 45, "spot" to 3260),
        "curves" to mapOf("risk_free_ref" to 1, "credit_ref" to 2),
        "model" to "TF_LATTICE", "seed" to 20240101,
    )

    private val STD_KEYS = listOf(
        "bond_value", "preferred_share_value", "conversion_option_value", "exchange_option_value",
        "warrant_value", "redemption_option_value", "issuer_call_value", "sale_claim_value",
        "stock_option_value", "conditional_option_value", "dilution_effect", "total_fair_value",
    )

    /** 커브 업로드(Phase 4-α resolve 가 실제 커브를 요구). upload_id 반환. */
    private fun uploadCurve(token: String, kind: String): Long {
        val body = mutableMapOf<String, Any>(
            "as_of" to "2024-06-26", "kind" to kind, "origin" to "UPLOAD",
            "points" to listOf(
                mapOf("tenor_years" to 1, "rate_percent" to 3.35),
                mapOf("tenor_years" to 3, "rate_percent" to if (kind == "CREDIT") 15.02 else 3.30),
            ),
        )
        if (kind == "CREDIT") body["grade"] = "BB"
        val res = post("/curves", body, token)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("upload_id").asLong()
    }

    /** CB 상품 생성 + (실제 커브 업로드 후 ref 로) terms 저장. instrumentId 반환. */
    private fun cbWithTerms(token: String): Long {
        val id = createInstrument(token, "CB")
        val rf = uploadCurve(token, "RISK_FREE")
        val cr = uploadCurve(token, "CREDIT")
        val terms = validCb().toMutableMap()
        terms["curves"] = mapOf("risk_free_ref" to rf, "credit_ref" to cr) // 실제 upload_id 로 resolve 가능
        assertEquals(200, put("/instruments/$id/terms", terms, token).statusCode())
        return id
    }

    @Test
    fun `price→job→placeholder result 형식이 계약과 일치`() {
        val token = adminToken()
        val id = cbWithTerms(token)

        val priceRes = post("/instruments/$id/price", emptyMap<String, Any>(), token)
        assertEquals(201, priceRes.statusCode(), priceRes.body())
        val priceJson = mapper.readTree(priceRes.body())
        assertEquals("DONE", priceJson.get("status").asText())
        assertEquals(false, priceJson.get("cached").asBoolean())
        val jobId = priceJson.get("job_id").asLong()

        // GET /jobs/{id}
        val jobRes = get("/jobs/$jobId", token)
        assertEquals(200, jobRes.statusCode(), jobRes.body())
        assertEquals("DONE", mapper.readTree(jobRes.body()).get("status").asText())

        // GET /jobs/{id}/result — placeholder 형식 검증
        val resultRes = get("/jobs/$jobId/result", token)
        assertEquals(200, resultRes.statusCode(), resultRes.body())
        val r = mapper.readTree(resultRes.body())
        // 표준 12 component key 전부 + 값 0
        val comp = r.get("components")
        for (k in STD_KEYS) {
            assertTrue(comp.has(k), "component key 누락: $k")
            assertEquals(0.0, comp.get(k).asDouble(), "$k 는 0 이어야 함")
        }
        assertEquals(0.0, r.get("total_fair_value").asDouble())
        // Σcomponents(echo 제외) = total = 0
        val sum = STD_KEYS.filter { it != "total_fair_value" }.sumOf { comp.get(it).asDouble() }
        assertEquals(0.0, sum, "Σcomponents != 0")
        // warnings PLACEHOLDER, errors 0
        assertEquals("PLACEHOLDER", r.get("warnings").get(0).get("code").asText())
        assertEquals(0, r.get("errors").size())
        // reproducibility.input_hash = 64 hex
        val hash = r.get("reproducibility").get("input_hash").asText()
        assertTrue(Regex("^[a-f0-9]{64}$").matches(hash), "input_hash 형식: $hash")
        // 필수 메타
        assertEquals("CB", r.get("instrument_type").asText())
        assertEquals(jobId, r.get("job_id").asLong())
    }

    @Test
    fun `동일 trigger 재요청은 cached=true, 같은 input_hash`() {
        val token = adminToken()
        val id = cbWithTerms(token)

        val first = mapper.readTree(post("/instruments/$id/price", emptyMap<String, Any>(), token).body())
        val firstJob = first.get("job_id").asLong()
        val firstHash = mapper.readTree(get("/jobs/$firstJob/result", token).body()).get("reproducibility").get("input_hash").asText()

        val second = mapper.readTree(post("/instruments/$id/price", emptyMap<String, Any>(), token).body())
        assertTrue(second.get("cached").asBoolean(), "재요청은 cached=true: $second")
        assertEquals(firstJob, second.get("job_id").asLong(), "캐시는 같은 Job 재사용")
        val secondHash = mapper.readTree(get("/jobs/${second.get("job_id").asLong()}/result", token).body())
            .get("reproducibility").get("input_hash").asText()
        assertEquals(firstHash, secondHash, "input_hash 결정성")
    }

    @Test
    fun `조직 격리 — 타 조직 instrument price_job_result는 404`() {
        val tokenA = adminToken(uniq("ORGA"))
        val instA = cbWithTerms(tokenA)
        val firstJob = mapper.readTree(post("/instruments/$instA/price", emptyMap<String, Any>(), tokenA).body()).get("job_id").asLong()

        val tokenB = adminToken(uniq("ORGB"))
        assertEquals(404, post("/instruments/$instA/price", emptyMap<String, Any>(), tokenB).statusCode())
        assertEquals(404, get("/jobs/$firstJob", tokenB).statusCode())
        assertEquals(404, get("/jobs/$firstJob/result", tokenB).statusCode())
    }

    @Test
    fun `권한 — VIEWER는 price 실행 403`() {
        val org = uniq("ORG")
        val adminTok = adminToken(org)
        val id = cbWithTerms(adminTok)
        val signup = post("/auth/signup", mapOf("email" to "${uniq("v")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        val userId = mapper.readTree(signup.body()).get("user").get("id").asLong()
        val vEmail = mapper.readTree(signup.body()).get("user").get("email").asText()
        assertEquals(200, patch("/admin/users/$userId", mapOf("role" to "VIEWER"), adminTok).statusCode())
        val vTok = mapper.readTree(post("/auth/login", mapOf("email" to vEmail, "pw" to "pw12345"), null).body()).get("token").asText()

        val res = post("/instruments/$id/price", emptyMap<String, Any>(), vTok)
        assertEquals(403, res.statusCode(), res.body())
    }

    @Test
    fun `terms 미저장 상태로 price는 409`() {
        val token = adminToken()
        val id = createInstrument(token, "CB") // terms 저장 안 함
        val res = post("/instruments/$id/price", emptyMap<String, Any>(), token)
        assertEquals(409, res.statusCode(), res.body())
    }
}
