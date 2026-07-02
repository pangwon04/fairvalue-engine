package com.fairvalue.pricing

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
 * Phase 5-5: GET /jobs(평가 이력) + DELETE /instruments/{id}(soft/hard) 통합테스트.
 *   ★ hard delete 판정은 BLOCKING — 결과 있는 상품이 hard 되면 안 됨.
 *   엔진은 Dummy(app.engine.mode=dummy)로 DONE Job 생성.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class JobHistoryDeleteTest {

    companion object {
        @Container @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16")).apply {
                withDatabaseName("fairvalue"); withUsername("fairvalue"); withPassword("fairvalue")
            }

        @JvmStatic @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("jwt.secret") { "integration-test-secret-long-enough-32bytes!!" }
            registry.add("app.engine.mode") { "dummy" }   // 실 엔진 HTTP 없이 DONE Job 생성
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
    private fun post(p: String, v: Any, t: String?) = client.send(b(p, t).POST(body(v)).build(), HttpResponse.BodyHandlers.ofString())
    private fun put(p: String, v: Any, t: String?) = client.send(b(p, t).method("PUT", body(v)).build(), HttpResponse.BodyHandlers.ofString())
    private fun get(p: String, t: String?) = client.send(b(p, t).GET().build(), HttpResponse.BodyHandlers.ofString())
    private fun del(p: String, t: String?) = client.send(b(p, t).DELETE().build(), HttpResponse.BodyHandlers.ofString())
    private fun uniq(p: String) = "$p-${System.nanoTime()}"

    private fun adminToken(org: String = uniq("ORG")): String {
        val res = post("/auth/signup", mapOf("email" to "${uniq("a")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("token").asText()
    }
    private fun createInstrument(t: String, type: String = "CB"): Long {
        val res = post("/instruments", mapOf("type" to type, "name" to "$type-x", "issuer" to "issuer"), t)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("id").asLong()
    }
    private fun uploadCurve(t: String, kind: String): Long {
        val body = mutableMapOf<String, Any>(
            "as_of" to "2024-06-26", "kind" to kind, "origin" to "UPLOAD",
            "points" to listOf(mapOf("tenor_years" to 1, "rate_percent" to 3.35),
                mapOf("tenor_years" to 3, "rate_percent" to if (kind == "CREDIT") 15.02 else 3.30)),
        )
        if (kind == "CREDIT") body["grade"] = "BB"
        val res = post("/curves", body, t)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("upload_id").asLong()
    }
    private fun validCb(rf: Long, cr: Long): Map<String, Any> = mapOf(
        "valuation_date" to "2024-06-26",
        "metadata" to mapOf("issuer" to "예시바이오", "instrument_name" to "CB1"),
        "terms" to mapOf("issue_date" to "2023-09-13", "maturity_date" to "2026-09-13",
            "issue_amount" to 3000000000L, "face_value" to 10000, "coupon_rate" to 2, "coupon_freq_month" to 3),
        "rights" to mapOf("conversion" to mapOf("strike" to 3260, "start" to "2024-09-13"), "refixing" to mapOf("enabled" to false)),
        "market" to mapOf("asset_id" to 880, "volatility" to 45, "spot" to 3260),
        "curves" to mapOf("risk_free_ref" to rf, "credit_ref" to cr),
        "model" to "TF_LATTICE", "seed" to 20240101,
    )
    /** CB 생성 + 커브 + terms 저장 + 평가(DONE Job). instrumentId 반환. */
    private fun pricedCb(t: String): Long {
        val id = createInstrument(t)
        val rf = uploadCurve(t, "RISK_FREE"); val cr = uploadCurve(t, "CREDIT")
        assertEquals(200, put("/instruments/$id/terms", validCb(rf, cr), t).statusCode())
        val pr = post("/instruments/$id/price", emptyMap<String, Any>(), t)
        assertEquals(201, pr.statusCode(), pr.body())
        assertEquals("DONE", mapper.readTree(pr.body()).get("status").asText())
        return id
    }

    @Test
    fun `GET jobs — 평가 후 이력에 DONE Job, org 격리`() {
        val a = adminToken(); val id = pricedCb(a)
        val jobs = mapper.readTree(get("/jobs", a).body()).get("items")
        assertTrue(jobs.size() >= 1)
        val row = jobs.first { it.get("instrument_id").asLong() == id }
        assertEquals("DONE", row.get("status").asText())
        assertEquals("CB", row.get("instrument_type").asText())
        assertTrue(row.has("total_fair_value"))    // DONE 이면 요약 채움
        // 다른 org 는 이 Job 안 보임
        val b = adminToken()
        assertEquals(0, mapper.readTree(get("/jobs", b).body()).get("items").size())
    }

    @Test
    fun `DELETE — DONE 있는 상품은 soft(ARCHIVED) 보존`() {
        val a = adminToken(); val id = pricedCb(a)
        val res = del("/instruments/$id", a)
        assertEquals(200, res.statusCode(), res.body())
        val node = mapper.readTree(res.body())
        assertEquals("soft", node.get("deleted").asText())     // ★ 결과 있으면 soft
        assertEquals("ARCHIVED", node.get("status").asText())
        // 데이터 보존: 상세 조회 가능, Job 이력 유지, 기본 목록엔 미노출
        assertEquals(200, get("/instruments/$id", a).statusCode())
        assertTrue(mapper.readTree(get("/jobs", a).body()).get("items").any { it.get("instrument_id").asLong() == id })
        val activeList = mapper.readTree(get("/instruments", a).body()).get("items")
        assertFalse(activeList.any { it.get("id").asLong() == id }, "ARCHIVED 는 기본 목록 제외")
        val archivedList = mapper.readTree(get("/instruments?include_archived=true", a).body()).get("items")
        assertTrue(archivedList.any { it.get("id").asLong() == id }, "include_archived 로 조회")
    }

    @Test
    fun `DELETE — 결과 없는 DRAFT 는 hard 완전삭제`() {
        val a = adminToken(); val id = createInstrument(a)   // DRAFT, 평가 안 함
        val res = del("/instruments/$id", a)
        assertEquals(200, res.statusCode(), res.body())
        assertEquals("hard", mapper.readTree(res.body()).get("deleted").asText())  // ★ 결과 없으면 hard
        assertEquals(404, get("/instruments/$id", a).statusCode(), "완전 삭제됨")
    }

    @Test
    fun `DELETE — 이미 ARCHIVED 는 멱등 soft`() {
        val a = adminToken(); val id = pricedCb(a)
        assertEquals("soft", mapper.readTree(del("/instruments/$id", a).body()).get("deleted").asText())
        val again = del("/instruments/$id", a)
        assertEquals(200, again.statusCode())
        assertEquals("soft", mapper.readTree(again.body()).get("deleted").asText())   // 멱등
    }

    @Test
    fun `DELETE — 타 org 상품은 404`() {
        val a = adminToken(); val id = pricedCb(a)
        val b = adminToken()
        assertEquals(404, del("/instruments/$id", b).statusCode())   // org 격리
    }
}
