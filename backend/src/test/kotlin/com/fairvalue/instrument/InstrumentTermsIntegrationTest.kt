package com.fairvalue.instrument

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
 * Instrument/Terms 통합테스트 — Testcontainers PostgreSQL + Flyway V1.
 * HTTP 클라이언트는 1-B-1 과 동일하게 JDK java.net.http.HttpClient(4xx 예외 없음).
 *
 * 입증: 정상 terms(error 0) · 필수 누락(error) · refixing floor>init(error) ·
 *       SO credit_ref 무시 · 조직 격리(타 조직 404) · VIEWER 쓰기 403 · 7종 저장 스모크.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InstrumentTermsIntegrationTest {

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
        val r = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
        if (token != null) r.header("Authorization", "Bearer $token")
        return r
    }

    private fun body(v: Any) = HttpRequest.BodyPublishers.ofString(
        if (v is String) v else mapper.writeValueAsString(v),
    )

    private fun post(path: String, payload: Any, token: String?) =
        client.send(b(path, token).POST(body(payload)).build(), HttpResponse.BodyHandlers.ofString())

    private fun put(path: String, payload: Any, token: String?) =
        client.send(b(path, token).method("PUT", body(payload)).build(), HttpResponse.BodyHandlers.ofString())

    private fun get(path: String, token: String?) =
        client.send(b(path, token).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun patch(path: String, payload: Any, token: String?) =
        client.send(b(path, token).method("PATCH", body(payload)).build(), HttpResponse.BodyHandlers.ofString())

    private fun uniq(p: String) = "$p-${System.nanoTime()}"

    /** 신규 조직 가입(ORG_ADMIN, 쓰기권한) → token. */
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

    private fun errorsOf(node: JsonNode) =
        node.get("validation").filter { it.get("severity").asText() == "error" }

    // ---- 정상 CB rawForm (검증 error 0) ----
    private fun validCb(): Map<String, Any> = mapOf(
        "valuation_date" to "2024-06-26",
        "metadata" to mapOf("issuer" to "예시바이오", "instrument_name" to "CB1"),
        "terms" to mapOf(
            "issue_date" to "2023-09-13", "maturity_date" to "2026-09-13",
            "issue_amount" to 3000000000L, "face_value" to 10000, "coupon_rate" to 2, "coupon_freq_month" to 3,
        ),
        "rights" to mapOf(
            "conversion" to mapOf("strike" to 3260, "start" to "2024-09-13"),
            "refixing" to mapOf("enabled" to false),
        ),
        "market" to mapOf("asset_id" to 880, "volatility" to 45, "spot" to 3260),
        "curves" to mapOf("risk_free_ref" to 1, "credit_ref" to 2),
        "model" to "TF_LATTICE", "seed" to 20240101,
    )

    @Test
    fun `프로젝트→상품→정상 terms 저장은 error 0`() {
        val token = adminToken()
        val proj = post("/projects", mapOf("name" to "P1"), token)
        assertEquals(201, proj.statusCode(), proj.body())
        val id = createInstrument(token, "CB")

        val res = put("/instruments/$id/terms", validCb(), token)
        assertEquals(200, res.statusCode(), res.body())
        val json = mapper.readTree(res.body())
        assertTrue(json.get("saved").asBoolean(), res.body())
        assertEquals(0, errorsOf(json).size, "정상 rawForm 인데 error: ${res.body()}")
        assertFalse(json.get("has_errors").asBoolean(), res.body())
    }

    @Test
    fun `CB 전환가 누락은 required error`() {
        val token = adminToken()
        val id = createInstrument(token, "CB")
        val raw = validCb().toMutableMap()
        // rights.conversion.strike 제거
        raw["rights"] = mapOf("conversion" to mapOf("start" to "2024-09-13"), "refixing" to mapOf("enabled" to false))
        val res = put("/instruments/$id/terms", raw, token)
        assertEquals(200, res.statusCode(), res.body())
        val json = mapper.readTree(res.body())
        assertTrue(json.get("saved").asBoolean())  // 저장은 됨
        assertTrue(json.get("has_errors").asBoolean(), res.body())
        val hit = errorsOf(json).any {
            it.get("rule").asText() == "required" && it.get("field").asText().contains("rights.conversion.strike")
        }
        assertTrue(hit, "전환가 required error 누락: ${res.body()}")
    }

    @Test
    fun `refixing floor가 init보다 크면 refixingFloorCheck error`() {
        val token = adminToken()
        val id = createInstrument(token, "CB")
        val raw = validCb().toMutableMap()
        raw["rights"] = mapOf(
            "conversion" to mapOf("strike" to 3260, "start" to "2024-09-13"),
            "refixing" to mapOf("enabled" to true, "floor" to 5000, "direction" to "DOWN"),
        )
        val res = put("/instruments/$id/terms", raw, token)
        val json = mapper.readTree(res.body())
        val hit = errorsOf(json).any { it.get("rule").asText() == "refixingFloorCheck" }
        assertTrue(hit, "refixingFloorCheck error 누락: ${res.body()}")
    }

    @Test
    fun `SO에 credit_ref를 넣어도 credit 관련 error는 없다(무시)`() {
        val token = adminToken()
        val id = createInstrument(token, "SO")
        val raw = mutableMapOf<String, Any>(
            "valuation_date" to "2024-06-26",
            "metadata" to mapOf("issuer" to "예시소프트", "instrument_name" to "SO1"),
            "terms" to mapOf("grant_date" to "2024-01-02", "grant_quantity" to 100000, "exercise_price" to 15000, "expected_term" to 4),
            "rights" to mapOf("vesting" to mapOf("schedule" to listOf(
                mapOf("date" to "2026-01-02", "ratio" to 50), mapOf("date" to "2027-01-02", "ratio" to 50),
            ))),
            "market" to mapOf("asset_id" to 882, "volatility" to 40, "spot" to 15000),
            "curves" to mapOf("risk_free_ref" to 1, "credit_ref" to 2), // ← SO 에 불필요한 credit_ref
            "model" to "BSM", "seed" to 20240101,
        )
        val res = put("/instruments/$id/terms", raw, token)
        assertEquals(200, res.statusCode(), res.body())
        val json = mapper.readTree(res.body())
        assertTrue(json.get("saved").asBoolean())
        val creditError = json.get("validation").any {
            it.get("severity").asText() == "error" && it.get("field").asText().contains("credit")
        }
        assertFalse(creditError, "SO 의 credit_ref 가 error 를 유발하면 안 됨: ${res.body()}")
        assertEquals(0, errorsOf(json).size, "SO 정상 입력인데 error: ${res.body()}")
    }

    @Test
    fun `조직 격리 — 타 조직 instrument의 terms PUT_GET은 404`() {
        val tokenA = adminToken(uniq("ORGA"))
        val instA = createInstrument(tokenA, "CB")
        val tokenB = adminToken(uniq("ORGB"))

        val putRes = put("/instruments/$instA/terms", validCb(), tokenB)
        assertEquals(404, putRes.statusCode(), putRes.body())
        val getRes = get("/instruments/$instA/terms", tokenB)
        assertEquals(404, getRes.statusCode(), getRes.body())
        // 단건 조회도 타 조직은 404
        assertEquals(404, get("/instruments/$instA", tokenB).statusCode())
    }

    @Test
    fun `권한 — VIEWER는 상품 생성 403`() {
        val org = uniq("ORG")
        val adminTok = adminToken(org)
        // 같은 조직 추가 사용자(VALUATOR) 가입
        val signup = post("/auth/signup", mapOf("email" to "${uniq("v")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        val userId = mapper.readTree(signup.body()).get("user").get("id").asLong()
        val vEmail = mapper.readTree(signup.body()).get("user").get("email").asText()
        // 관리자가 VIEWER 로 강등
        assertEquals(200, patch("/admin/users/$userId", mapOf("role" to "VIEWER"), adminTok).statusCode())
        // VIEWER 재로그인 → 상품 생성 403
        val vTok = mapper.readTree(post("/auth/login", mapOf("email" to vEmail, "pw" to "pw12345"), null).body()).get("token").asText()
        val res = post("/instruments", mapOf("type" to "CB", "name" to "x", "issuer" to "y"), vTok)
        assertEquals(403, res.statusCode(), res.body())
    }

    @Test
    fun `7종 각각 terms 저장 스모크 — saved=true`() {
        val token = adminToken()
        val minimal = mapOf(
            "valuation_date" to "2024-06-26", "terms" to emptyMap<String, Any>(),
            "rights" to emptyMap<String, Any>(), "market" to emptyMap<String, Any>(),
            "curves" to emptyMap<String, Any>(), "model" to "BSM", "seed" to 1,
        )
        for (type in listOf("RCPS", "CPS", "CB", "EB", "BW", "SO", "CSO")) {
            val id = createInstrument(token, type)
            val res = put("/instruments/$id/terms", minimal, token)
            assertEquals(200, res.statusCode(), "$type: ${res.body()}")
            assertTrue(mapper.readTree(res.body()).get("saved").asBoolean(), "$type 저장 실패")
        }
    }

    @Test
    fun `parity 입력은 warning + 저장본에서 제거`() {
        val token = adminToken()
        val id = createInstrument(token, "CB")
        val raw = validCb().toMutableMap()
        raw["rights"] = mapOf(
            "conversion" to mapOf("strike" to 3260, "start" to "2024-09-13", "parity" to 1.0),
            "refixing" to mapOf("enabled" to false),
        )
        val res = put("/instruments/$id/terms", raw, token)
        val json = mapper.readTree(res.body())
        val parityWarn = json.get("validation").any { it.get("rule").asText() == "parity" && it.get("severity").asText() == "warning" }
        assertTrue(parityWarn, "parity warning 누락: ${res.body()}")
        // 저장본 조회 시 parity 제거됨
        val stored = mapper.readTree(get("/instruments/$id/terms", token).body()).get("rawForm")
        assertTrue(stored.get("rights").get("conversion").get("parity") == null, "저장본에 parity 잔존")
    }
}
