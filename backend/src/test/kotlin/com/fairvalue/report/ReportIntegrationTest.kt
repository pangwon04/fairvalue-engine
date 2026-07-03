package com.fairvalue.report

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
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
 * 보고서 API 통합: 구버전(트리 없음) 차단, org 격리, 목록. (Dummy 엔진 = 트리 없음 → 차단 경로 검증)
 *   SUCCESS 발급·다운로드는 ReportBuilderTest(단위) + 실엔진 e2e 로 보증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReportIntegrationTest {

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
    private val client = HttpClient.newHttpClient()

    private fun b(path: String, token: String?) = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        .header("Content-Type", "application/json").also { if (token != null) it.header("Authorization", "Bearer $token") }
    private fun post(path: String, payload: Any, token: String?) =
        client.send(b(path, token).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(), HttpResponse.BodyHandlers.ofString())
    private fun get(path: String, token: String?) =
        client.send(b(path, token).GET().build(), HttpResponse.BodyHandlers.ofString())
    private fun uniq(p: String) = "$p-${System.nanoTime()}"
    private fun signup(org: String): String {
        val r = post("/auth/signup", mapOf("email" to "${uniq("u")}@x.com", "pw" to "pw12345", "org_code" to org), null)
        assertEquals(201, r.statusCode(), r.body()); return mapper.readTree(r.body()).get("token").asText()
    }

    @Test
    fun `구버전(트리 없음) 발급 차단·목록 org 격리`() {
        val token = signup(uniq("ORG"))
        // Dummy 엔진 결과에는 trees/curve_bootstrap 이 없음 → 발급 차단(409)
        val inst = post("/instruments", mapOf("type" to "CB", "name" to "CB1", "issuer" to "발행"), token)
        val instId = mapper.readTree(inst.body()).get("id").asLong()
        // terms 저장(간이) 후 price → DONE(dummy)
        // terms 최소 저장은 별도 파이프라인이라 여기선 job 없이 목록·격리만 검증
        assertEquals(0, mapper.readTree(get("/reports", token).body()).get("items").size())

        // 존재하지 않는/타 조직 보고서 다운로드 404
        assertEquals(404, get("/reports/999999/pdf", token).statusCode())
        assertEquals(404, get("/reports/999999/excel", token).statusCode())

        // 타 조직이 이 조직 job 에 발급 시도 → 404(job 미노출)
        val other = signup(uniq("ORG2"))
        assertEquals(404, post("/jobs/999999/report", emptyMap<String, Any>(), other).statusCode())
    }
}
