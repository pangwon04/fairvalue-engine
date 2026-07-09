package com.fairvalue.dashboard

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
 * ★5-10 파트 B: GET /dashboard/summary 통합테스트.
 *   - 집계 정확성 + org 격리(타 org 데이터 미포함).
 *   - 숨김(hidden) job 은 jobs.done·recent_jobs 에서 제외.
 *   - report_issued = valuation_reports 에 job_id 존재 여부(정확).
 *   엔진 Dummy(app.engine.mode=dummy)로 DONE Job 생성.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DashboardSummaryTest {

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
            registry.add("app.engine.mode") { "dummy" }
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
    private fun uniq(p: String) = "$p-${System.nanoTime()}"

    private fun adminToken(org: String = uniq("ORG")): String {
        val res = post("/auth/signup", mapOf("email" to "${uniq("a")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("token").asText()
    }
    private fun uploadCurve(t: String, kind: String): Long {
        val body = mutableMapOf<String, Any>(
            "as_of" to "2024-06-26", "kind" to kind, "origin" to "UPLOAD",
            "points" to listOf(
                mapOf("tenor_years" to 1, "rate_percent" to 3.35),
                mapOf("tenor_years" to 3, "rate_percent" to if (kind == "CREDIT") 15.02 else 3.30),
            ),
        )
        if (kind == "CREDIT") body["grade"] = "BB"
        return mapper.readTree(post("/curves", body, t).body()).get("upload_id").asLong()
    }
    private fun registerVol(t: String): Long {
        val res = post("/volatilities", mapOf(
            "as_of" to "2024-06-26", "label" to "대상", "method" to "DIRECT", "annual_vol_percent" to 30.0,
        ), t)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("volatility_id").asLong()
    }
    private fun createInstrument(t: String, type: String = "CB"): Long {
        val res = post("/instruments", mapOf("type" to type, "name" to "$type-x", "issuer" to "issuer"), t)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("id").asLong()
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
    /** CB 생성 + 커브 + terms + 평가(DONE). job_id 반환. */
    private fun pricedCbJob(t: String): Long {
        val id = createInstrument(t)
        val rf = uploadCurve(t, "RISK_FREE"); val cr = uploadCurve(t, "CREDIT")
        assertEquals(200, put("/instruments/$id/terms", validCb(rf, cr), t).statusCode())
        val pr = post("/instruments/$id/price", emptyMap<String, Any>(), t)
        assertEquals(201, pr.statusCode(), pr.body())
        assertEquals("DONE", mapper.readTree(pr.body()).get("status").asText())
        return mapper.readTree(pr.body()).get("job_id").asLong()
    }
    private fun summary(t: String): JsonNode = mapper.readTree(get("/dashboard/summary", t).body())

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `대시보드 — 집계 정확 · org 격리`() {
        val a = adminToken()
        pricedCbJob(a)               // 상품 1 · 커브 2 · DONE job 1
        registerVol(a)               // 변동성 1

        val s = summary(a)
        assertTrue(s.get("instruments").get("active").asLong() >= 1, "활성 상품")
        assertEquals(0, s.get("instruments").get("archived").asLong())
        assertTrue(s.get("jobs").get("done").asLong() >= 1, "완료 job")
        assertEquals(0, s.get("jobs").get("failed").asLong())
        assertEquals(2, s.get("parameters").get("curves").asLong(), "커브 2")
        assertEquals(1, s.get("parameters").get("volatilities").asLong(), "변동성 1")
        assertTrue(s.get("by_type").any { it.get("type").asText() == "CB" && it.get("count").asLong() >= 1 }, "유형별 CB")
        val recent = s.get("recent_jobs")
        assertTrue(recent.size() >= 1)
        assertFalse(recent.get(0).get("report_issued").asBoolean(), "발급 전 report_issued=false")

        // 타 org 는 위 데이터 미포함(전부 0/빈).
        val b = summary(adminToken())
        assertEquals(0, b.get("instruments").get("active").asLong())
        assertEquals(0, b.get("jobs").get("done").asLong())
        assertEquals(0, b.get("reports").get("count").asLong())
        assertEquals(0, b.get("parameters").get("curves").asLong())
        assertEquals(0, b.get("by_type").size())
        assertEquals(0, b.get("recent_jobs").size())
    }

    @Test
    fun `대시보드 — 숨김 job 제외 · report_issued 정확`() {
        val a = adminToken()
        val jobId = pricedCbJob(a)

        // 보고서 발급 → report_issued=true, reports.count=1.
        assertEquals(201, post("/jobs/$jobId/report", emptyMap<String, Any>(), a).statusCode())
        val s1 = summary(a)
        assertEquals(1, s1.get("reports").get("count").asLong())
        assertEquals(1, s1.get("jobs").get("done").asLong())
        val row = s1.get("recent_jobs").first { it.get("job_id").asLong() == jobId }
        assertTrue(row.get("report_issued").asBoolean(), "발급 후 report_issued=true")

        // job 숨김(DONE→hidden) → done 집계·recent 에서 제외.
        assertEquals(200, post("/jobs/batch-delete", mapOf("job_ids" to listOf(jobId)), a).statusCode())
        val s2 = summary(a)
        assertEquals(0, s2.get("jobs").get("done").asLong(), "숨김 job 은 done 제외")
        assertFalse(s2.get("recent_jobs").any { it.get("job_id").asLong() == jobId }, "숨김 job 은 recent 제외")
        assertEquals(1, s2.get("reports").get("count").asLong(), "보고서는 숨김과 무관하게 유지")
    }
}
