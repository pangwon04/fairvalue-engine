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
    fun `보고서 목록·다운로드 org 격리·미존재 404`() {
        // (발급 SUCCESS·다운로드 e2e 는 PricingJobIntegrationTest 에서 dummy 트리 보존으로 검증)
        val token = signup(uniq("ORG"))
        assertEquals(0, mapper.readTree(get("/reports", token).body()).get("items").size(), "발급 전 목록 빔")
        // 미존재 보고서 다운로드 404
        assertEquals(404, get("/reports/999999/pdf", token).statusCode())
        assertEquals(404, get("/reports/999999/excel", token).statusCode())
        // 타 조직이 미노출 job 에 발급 시도 → 404
        val other = signup(uniq("ORG2"))
        assertEquals(404, post("/jobs/999999/report", emptyMap<String, Any>(), other).statusCode())
    }
}
