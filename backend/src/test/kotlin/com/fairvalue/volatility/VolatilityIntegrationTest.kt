package com.fairvalue.volatility

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
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 변동성 API 통합테스트: compute 미저장·등록/상세·org 격리·권한 403. (CI/Testcontainers)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class VolatilityIntegrationTest {

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
            registry.add("app.engine.mode") { "dummy" }
        }
    }

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mapper: ObjectMapper
    private val client: HttpClient = HttpClient.newHttpClient()

    private fun b(path: String, token: String?): HttpRequest.Builder {
        val r = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        if (token != null) r.header("Authorization", "Bearer $token")
        return r
    }
    private fun postJson(path: String, payload: Any, token: String?): HttpResponse<String> =
        client.send(
            b(path, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    private fun get(path: String, token: String?): HttpResponse<String> =
        client.send(b(path, token).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun uniq(p: String) = "$p-${System.nanoTime()}"
    private fun signup(orgCode: String): String {
        val res = postJson("/auth/signup", mapOf("email" to "${uniq("u")}@x.com", "pw" to "pw12345", "org_code" to orgCode), null)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("token").asText()
    }

    // multipart(files[] + trading_days) 수동 조립
    private fun postCompute(token: String, files: List<Pair<String, String>>, tradingDays: Int?): HttpResponse<String> {
        val boundary = "----fvB${System.nanoTime()}"
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        tradingDays?.let {
            w("--$boundary\r\nContent-Disposition: form-data; name=\"trading_days\"\r\n\r\n$it\r\n")
        }
        for ((name, content) in files) {
            w("--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"$name\"\r\nContent-Type: text/csv\r\n\r\n")
            out.write(content.toByteArray(Charsets.UTF_8))
            w("\r\n")
        }
        w("--$boundary--\r\n")
        val req = b("/volatilities/compute", token)
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build()
        return client.send(req, HttpResponse.BodyHandlers.ofString())
    }

    private val CSV_A = "date,close\n2024-01-02,100\n2024-01-03,102\n2024-01-04,101\n2024-01-05,103\n2024-01-08,105"
    private val CSV_B = "date,close\n2024-01-02,50\n2024-01-03,51\n2024-01-04,49\n2024-01-05,52\n2024-01-08,50"

    @Test
    fun `compute 미리보기는 저장하지 않는다`() {
        val token = signup(uniq("VOL"))
        val res = postCompute(token, listOf("A.csv" to CSV_A, "B.csv" to CSV_B), 250)
        assertEquals(200, res.statusCode(), res.body())
        val node = mapper.readTree(res.body())
        assertEquals(2, node.get("companies").size())
        assertTrue(node.get("average_percent").asDouble() > 0)
        // ★ 저장 안 됨 — 목록 비어 있음
        val list = mapper.readTree(get("/volatilities", token).body()).get("items")
        assertEquals(0, list.size())
    }

    @Test
    fun `직접 등록 후 목록·상세`() {
        val token = signup(uniq("VOL"))
        val reg = postJson("/volatilities", mapOf(
            "as_of" to "2024-06-26", "label" to "예시바이오", "method" to "DIRECT",
            "annual_vol_percent" to 45.0, "source_note" to "내부 추정",
        ), token)
        assertEquals(201, reg.statusCode(), reg.body())
        val id = mapper.readTree(reg.body()).get("volatility_id").asLong()
        val list = mapper.readTree(get("/volatilities", token).body()).get("items")
        assertEquals(1, list.size())
        val detail = mapper.readTree(get("/volatilities/$id", token).body())
        assertEquals("DIRECT", detail.get("method").asText())
        assertEquals(45.0, detail.get("annual_vol_percent").asDouble(), 1e-6)
    }

    @Test
    fun `PEER_CSV 등록 detail_json 필수 키 보존`() {
        val token = signup(uniq("VOL"))
        val detail = mapOf(
            "companies" to listOf(mapOf("name" to "A.csv", "observations" to 5, "annual_vol_percent" to 23.24)),
            "computed_average_percent" to 23.24, "edited" to true, "source_filenames" to listOf("A.csv"),
        )
        val reg = postJson("/volatilities", mapOf(
            "as_of" to "2024-06-26", "label" to "유사3사평균", "method" to "PEER_CSV",
            "annual_vol_percent" to 25.0, "trading_days_used" to 248, "detail" to detail,
        ), token)
        assertEquals(201, reg.statusCode(), reg.body())
        val id = mapper.readTree(reg.body()).get("volatility_id").asLong()
        val d = mapper.readTree(get("/volatilities/$id", token).body()).get("detail")
        assertEquals(25.0, d.get("adopted_value").asDouble(), 1e-6)
        assertEquals(true, d.get("edited").asBoolean())
        assertEquals(248, d.get("trading_days_used").asInt())
        assertTrue(d.has("uploaded_at") && d.has("source_filenames") && d.has("companies"))
    }

    @Test
    fun `조직 격리 — 타 조직 변동성 상세는 404`() {
        val orgA = uniq("ORGA"); val tokenA = signup(orgA)
        val id = mapper.readTree(postJson("/volatilities", mapOf(
            "as_of" to "2024-06-26", "label" to "A사", "method" to "DIRECT", "annual_vol_percent" to 40.0), tokenA).body())
            .get("volatility_id").asLong()
        val tokenB = signup(uniq("ORGB"))
        assertEquals(404, get("/volatilities/$id", tokenB).statusCode())
        assertEquals(0, mapper.readTree(get("/volatilities", tokenB).body()).get("items").size())
    }

    @Test
    fun `권한 — VALUATOR 는 등록·산출 403`() {
        val org = uniq("ORG")
        signup(org)                             // 첫 사용자 = ORG_ADMIN(조직 생성)
        val valuator = signup(org)              // 같은 org_code 재가입 = VALUATOR(비쓰기)
        val reg = postJson("/volatilities", mapOf(
            "as_of" to "2024-06-26", "label" to "x", "method" to "DIRECT", "annual_vol_percent" to 30.0), valuator)
        assertEquals(403, reg.statusCode(), reg.body())
        val comp = postCompute(valuator, listOf("A.csv" to CSV_A), 250)
        assertEquals(403, comp.statusCode(), comp.body())
        // 조회는 인증 전원 허용(200)
        assertEquals(200, get("/volatilities", valuator).statusCode())
    }
}
