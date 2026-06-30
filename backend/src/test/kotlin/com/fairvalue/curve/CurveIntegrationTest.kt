package com.fairvalue.curve

import com.fairvalue.domain.CurveKind
import com.fairvalue.service.CurveMappingService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.time.LocalDate

/**
 * Curve 업로드·저장·버전·자동매핑 통합테스트 — Testcontainers PostgreSQL + Flyway V1~V4.
 * HTTP 클라이언트는 기존과 동일(JDK java.net.http, 4xx 예외 없음).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CurveIntegrationTest {

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
    @Autowired private lateinit var mappingService: CurveMappingService
    private val client: HttpClient = HttpClient.newHttpClient()

    private fun b(path: String, token: String?): HttpRequest.Builder {
        val r = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).header("Content-Type", "application/json")
        if (token != null) r.header("Authorization", "Bearer $token")
        return r
    }
    private fun postJson(path: String, payload: Any, token: String?) =
        client.send(b(path, token).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(),
            HttpResponse.BodyHandlers.ofString())
    private fun get(path: String, token: String?) =
        client.send(b(path, token).GET().build(), HttpResponse.BodyHandlers.ofString())
    private fun patch(path: String, payload: Any, token: String?) =
        client.send(b(path, token).method("PATCH", HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(),
            HttpResponse.BodyHandlers.ofString())

    /** multipart/form-data POST (java.net.http 수동 구성). */
    private fun postMultipart(path: String, fields: Map<String, String>, fileField: String, fileName: String, fileContent: String, token: String?): HttpResponse<String> {
        val boundary = "----fvBoundary${System.nanoTime()}"
        val sb = StringBuilder()
        for ((k, v) in fields) {
            sb.append("--$boundary\r\n")
            sb.append("Content-Disposition: form-data; name=\"$k\"\r\n\r\n")
            sb.append("$v\r\n")
        }
        sb.append("--$boundary\r\n")
        sb.append("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n")
        sb.append("Content-Type: text/csv\r\n\r\n")
        sb.append(fileContent).append("\r\n")
        sb.append("--$boundary--\r\n")
        val req = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .also { if (token != null) it.header("Authorization", "Bearer $token") }
            .POST(HttpRequest.BodyPublishers.ofString(sb.toString())).build()
        return client.send(req, HttpResponse.BodyHandlers.ofString())
    }

    private fun uniq(p: String) = "$p-${System.nanoTime()}"
    /** 신규 조직 가입(ORG_ADMIN — 쓰기권한). 반환 node 에 token + user.organization_id. */
    private fun signup(org: String = uniq("ORG")): JsonNode {
        val res = postJson("/auth/signup", mapOf("email" to "${uniq("a")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body())
    }
    private fun tokenOf(n: JsonNode) = n.get("token").asText()
    private fun orgIdOf(n: JsonNode) = n.get("user").get("organization_id").asLong()

    private fun riskFree(asOf: String = "2024-06-26") = mapOf(
        "as_of" to asOf, "kind" to "RISK_FREE", "source" to "test",
        "points" to listOf(
            mapOf("tenor_years" to 0.25, "rate_percent" to 3.41),
            mapOf("tenor_years" to 1, "rate_percent" to 3.35),
            mapOf("tenor_years" to 3, "rate_percent" to 3.30),
        ),
    )

    @Test
    fun `무위험 커브 JSON 업로드 후 단건·목록 조회`() {
        val token = tokenOf(signup())
        val up = postJson("/curves", riskFree(), token)
        assertEquals(201, up.statusCode(), up.body())
        val uploadId = mapper.readTree(up.body()).get("upload_id").asLong()

        val detail = mapper.readTree(get("/curves/$uploadId", token).body())
        assertEquals("RISK_FREE", detail.get("kind").asText())
        assertEquals(1, detail.get("version").asInt())
        assertEquals(3, detail.get("points").size())
        assertEquals(0.25, detail.get("points").get(0).get("tenor_years").asDouble())

        val list = mapper.readTree(get("/curves", token).body()).get("items")
        assertTrue(list.any { it.get("id").asLong() == uploadId }, "목록에 업로드가 보여야 함")
    }

    @Test
    fun `multipart CSV 업로드`() {
        val token = tokenOf(signup())
        val csv = "#as_of:2024-06-26\ntenor_years,rate_percent\n0.25,3.41\n1,3.35\n3,3.30\n"
        val res = postMultipart("/curves",
            mapOf("as_of" to "2024-06-26", "kind" to "RISK_FREE", "source" to "csv"),
            "file", "rf.csv", csv, token)
        assertEquals(201, res.statusCode(), res.body())
        val id = mapper.readTree(res.body()).get("upload_id").asLong()
        assertEquals(3, mapper.readTree(get("/curves/$id", token).body()).get("points").size())
    }

    @Test
    fun `신용 커브 grade 누락은 422`() {
        val token = tokenOf(signup())
        val body = mapOf("as_of" to "2024-06-26", "kind" to "CREDIT",
            "points" to listOf(mapOf("tenor_years" to 1, "rate_percent" to 9.1)))
        val res = postJson("/curves", body, token)
        assertEquals(422, res.statusCode(), res.body())
        assertEquals("E422", mapper.readTree(res.body()).get("code").asText())
        assertTrue(mapper.readTree(res.body()).get("fields").any { it.get("field").asText() == "grade" })
    }

    @Test
    fun `tenor 역순·중복은 422`() {
        val token = tokenOf(signup())
        val desc = mapOf("as_of" to "2024-06-26", "kind" to "RISK_FREE",
            "points" to listOf(mapOf("tenor_years" to 3, "rate_percent" to 3.3), mapOf("tenor_years" to 1, "rate_percent" to 3.35)))
        assertEquals(422, postJson("/curves", desc, token).statusCode())
        val dup = mapOf("as_of" to "2024-06-26", "kind" to "RISK_FREE",
            "points" to listOf(mapOf("tenor_years" to 1, "rate_percent" to 3.3), mapOf("tenor_years" to 1, "rate_percent" to 3.35)))
        assertEquals(422, postJson("/curves", dup, token).statusCode())
    }

    @Test
    fun `같은 키 재업로드는 version 증가, 이력 보존`() {
        val token = tokenOf(signup())
        val first = mapper.readTree(postJson("/curves", riskFree(), token).body())
        val second = mapper.readTree(postJson("/curves", riskFree(), token).body())
        val v1 = mapper.readTree(get("/curves/${first.get("upload_id").asLong()}", token).body()).get("version").asInt()
        val v2 = mapper.readTree(get("/curves/${second.get("upload_id").asLong()}", token).body()).get("version").asInt()
        assertEquals(1, v1)
        assertEquals(2, v2)
        // 이력 2건 유지(목록에 v1·v2 둘 다)
        val versions = mapper.readTree(get("/curves?kind=RISK_FREE", token).body()).get("items").map { it.get("version").asInt() }
        assertTrue(versions.containsAll(listOf(1, 2)), "이력 2건 유지: $versions")
    }

    @Test
    fun `자동매핑은 최신 version 을 선택`() {
        val node = signup()
        val token = tokenOf(node); val orgId = orgIdOf(node)
        postJson("/curves", riskFree("2024-06-26"), token) // v1
        postJson("/curves", riskFree("2024-06-26"), token) // v2
        val mapped = mappingService.findActiveCurve(orgId, LocalDate.parse("2024-06-26"), CurveKind.RISK_FREE, null)
        assertNotNull(mapped)
        assertEquals(2, mapped!!.version, "자동매핑은 최신 version")
    }

    @Test
    fun `조직 격리 — 타 조직 커브 GET은 404`() {
        val tokenA = tokenOf(signup(uniq("ORGA")))
        val idA = mapper.readTree(postJson("/curves", riskFree(), tokenA).body()).get("upload_id").asLong()
        val tokenB = tokenOf(signup(uniq("ORGB")))
        assertEquals(404, get("/curves/$idA", tokenB).statusCode())
        // 목록도 타 조직 커브 비노출
        assertTrue(mapper.readTree(get("/curves", tokenB).body()).get("items").none { it.get("id").asLong() == idA })
    }

    @Test
    fun `권한 — VIEWER는 커브 업로드 403`() {
        val org = uniq("ORG")
        val node = signup(org); val adminTok = tokenOf(node)
        val signup2 = postJson("/auth/signup", mapOf("email" to "${uniq("v")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        val userId = mapper.readTree(signup2.body()).get("user").get("id").asLong()
        val vEmail = mapper.readTree(signup2.body()).get("user").get("email").asText()
        assertEquals(200, patch("/admin/users/$userId", mapOf("role" to "VIEWER"), adminTok).statusCode())
        val vTok = mapper.readTree(postJson("/auth/login", mapOf("email" to vEmail, "pw" to "pw12345"), null).body()).get("token").asText()
        assertEquals(403, postJson("/curves", riskFree(), vTok).statusCode())
    }
}
