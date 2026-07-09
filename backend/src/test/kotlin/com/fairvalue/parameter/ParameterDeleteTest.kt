package com.fairvalue.parameter

import com.fairvalue.repository.YieldCurvePointRepository
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
 * ★5-10 파트 A: 파라미터(커브·변동성) hard delete 통합테스트.
 *   - 삭제 → 목록 제외 · 헤더+만기점 완전 삭제(잔존 없음).
 *   - org 격리(타 org 404) · 권한(VALUATOR 403).
 *   - ★BLOCKING: 삭제된 커브를 참조하던 과거 job 의 결과·계산근거(context)·보고서 다운로드가 정상.
 *       평가시점 contextJson 스냅샷(V6) 보존 → 마스터 삭제가 재현성을 훼손하지 않음(감사-안전 실증).
 *   엔진은 Dummy(app.engine.mode=dummy)로 DONE Job 생성.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ParameterDeleteTest {

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
    @Autowired private lateinit var pointRepo: YieldCurvePointRepository
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

    /** 신규 org 첫 가입 = ORG_ADMIN(쓰기). */
    private fun adminToken(org: String = uniq("ORG")): String {
        val res = post("/auth/signup", mapOf("email" to "${uniq("a")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("token").asText()
    }
    /** 같은 org 재가입 = VALUATOR(비쓰기). */
    private fun valuatorToken(org: String): String {
        val res = post("/auth/signup", mapOf("email" to "${uniq("v")}@x.com", "pw" to "pw12345", "org_code" to org), null)
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
        val res = post("/curves", body, t)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("upload_id").asLong()
    }
    private fun registerVol(t: String, label: String = "테스트대상"): Long {
        val res = post("/volatilities", mapOf(
            "as_of" to "2024-06-26", "label" to label, "method" to "DIRECT", "annual_vol_percent" to 30.0,
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

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `커브 삭제 — 목록 제외·헤더·만기점 완전 삭제`() {
        val a = adminToken()
        val id = uploadCurve(a, "RISK_FREE")
        assertEquals(2, pointRepo.findByUploadIdOrderBySeqAsc(id).size, "삭제 전 만기점 존재")

        assertEquals(204, del("/curves/$id", a).statusCode())

        assertEquals(404, get("/curves/$id", a).statusCode(), "헤더 삭제됨")
        assertTrue(pointRepo.findByUploadIdOrderBySeqAsc(id).isEmpty(), "만기점 잔존 없음")
        assertFalse(mapper.readTree(get("/curves", a).body()).get("items").any { it.get("id").asLong() == id }, "목록 제외")
    }

    @Test
    fun `커브 삭제 — 타 org 404 · VALUATOR 403`() {
        val org = uniq("ORG")
        val admin = adminToken(org)
        val id = uploadCurve(admin, "RISK_FREE")

        val other = adminToken()   // 다른 org
        assertEquals(404, del("/curves/$id", other).statusCode(), "타 org 삭제 404")

        val valuator = valuatorToken(org)   // 같은 org, 비쓰기
        assertEquals(403, del("/curves/$id", valuator).statusCode(), "VALUATOR 삭제 403")
        assertEquals(200, get("/curves/$id", admin).statusCode(), "403·404 후에도 원본 보존")
    }

    @Test
    fun `변동성 삭제 — 목록 제외 · org 404 · VALUATOR 403`() {
        val org = uniq("ORG")
        val admin = adminToken(org)
        val id = registerVol(admin)
        assertTrue(mapper.readTree(get("/volatilities", admin).body()).get("items").any { it.get("id").asLong() == id })

        // 권한·격리
        assertEquals(404, del("/volatilities/$id", adminToken()).statusCode(), "타 org 404")
        assertEquals(403, del("/volatilities/$id", valuatorToken(org)).statusCode(), "VALUATOR 403")

        // 삭제 성공 → 목록 제외 + 단건 404
        assertEquals(204, del("/volatilities/$id", admin).statusCode())
        assertEquals(404, get("/volatilities/$id", admin).statusCode())
        assertFalse(mapper.readTree(get("/volatilities", admin).body()).get("items").any { it.get("id").asLong() == id }, "목록 제외")
    }

    @Test
    fun `★BLOCKING — 삭제된 커브 참조 과거 job 의 결과·계산근거·보고서 정상(스냅샷 보존)`() {
        val a = adminToken()
        val id = createInstrument(a)
        val rf = uploadCurve(a, "RISK_FREE")
        val cr = uploadCurve(a, "CREDIT")
        assertEquals(200, put("/instruments/$id/terms", validCb(rf, cr), a).statusCode())
        val pr = post("/instruments/$id/price", emptyMap<String, Any>(), a)
        assertEquals(201, pr.statusCode(), pr.body())
        assertEquals("DONE", mapper.readTree(pr.body()).get("status").asText())
        val jobId = mapper.readTree(pr.body()).get("job_id").asLong()

        // 보고서 발급(계산근거 포함 — dummy 트리 보존)
        val rep = post("/jobs/$jobId/report", emptyMap<String, Any>(), a)
        assertEquals(201, rep.statusCode(), rep.body())
        val reportId = mapper.readTree(rep.body()).get("report_id").asLong()

        // 참조 커브 완전 삭제
        assertEquals(204, del("/curves/$rf", a).statusCode())
        assertEquals(204, del("/curves/$cr", a).statusCode())
        assertEquals(404, get("/curves/$rf", a).statusCode(), "마스터 삭제됨")

        // ★스냅샷 보존 실증: 과거 job 의 결과·계산근거·보고서가 전부 정상.
        assertEquals(200, get("/jobs/$jobId/result", a).statusCode(), "평가 결과 보존")
        val ctx = get("/jobs/$jobId/context", a)
        assertEquals(200, ctx.statusCode(), "계산근거(context) 조회 정상")
        assertTrue(mapper.readTree(ctx.body()).get("has_context").asBoolean(), "평가시점 입력 스냅샷 보존")
        assertEquals(200, get("/reports/$reportId/pdf", a).statusCode(), "보고서 PDF 다운로드 정상")
        assertEquals(200, get("/reports/$reportId/excel", a).statusCode(), "보고서 Excel 다운로드 정상")
    }
}
