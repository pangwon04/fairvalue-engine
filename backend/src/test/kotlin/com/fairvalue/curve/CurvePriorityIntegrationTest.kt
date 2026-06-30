package com.fairvalue.curve

import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.CurveOrigin
import com.fairvalue.service.CurveMappingService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
 * 커브 우선순위(2-B): origin MANUAL>UPLOAD>BOOTSTRAP, 동일 origin 내 최신 version.
 * Testcontainers + java.net.http (기존 패턴).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CurvePriorityIntegrationTest {

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

    private fun post(path: String, payload: Any, token: String?): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).header("Content-Type", "application/json")
        if (token != null) b.header("Authorization", "Bearer $token")
        return client.send(b.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build(),
            HttpResponse.BodyHandlers.ofString())
    }

    private fun uniq(p: String) = "$p-${System.nanoTime()}"
    private fun signup(): JsonNode {
        val res = post("/auth/signup", mapOf("email" to "${uniq("a")}@x.com", "pw" to "pw12345", "org_code" to uniq("ORG")), null)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body())
    }

    private val ASOF = "2024-06-26"
    private fun upload(token: String, origin: String) {
        val body = mapOf(
            "as_of" to ASOF, "kind" to "RISK_FREE", "origin" to origin,
            "points" to listOf(mapOf("tenor_years" to 1, "rate_percent" to 3.0), mapOf("tenor_years" to 3, "rate_percent" to 3.2)),
        )
        assertEquals(201, post("/curves", body, token).statusCode())
    }

    @Test
    fun `우선순위 — 동일 origin 최신 version, MANUAL이 UPLOAD보다 우선`() {
        val node = signup()
        val token = node.get("token").asText()
        val orgId = node.get("user").get("organization_id").asLong()

        // UPLOAD v1, v2 → 우선순위 결과는 UPLOAD 최신(v2)
        upload(token, "UPLOAD")
        upload(token, "UPLOAD")
        val afterUploads = mappingService.findByPriority(orgId, LocalDate.parse(ASOF), CurveKind.RISK_FREE, null)
        assertNotNull(afterUploads)
        assertEquals(CurveOrigin.UPLOAD, afterUploads!!.origin)
        assertEquals(2, afterUploads.version, "동일 origin 내 최신 version")

        // MANUAL v3 추가 → MANUAL 우선
        upload(token, "MANUAL")
        val afterManual = mappingService.findByPriority(orgId, LocalDate.parse(ASOF), CurveKind.RISK_FREE, null)
        assertEquals(CurveOrigin.MANUAL, afterManual!!.origin, "MANUAL > UPLOAD")

        // 2-A findActiveCurve 는 origin 무관 최신 version(=3) — 무손상 확인
        val active = mappingService.findActiveCurve(orgId, LocalDate.parse(ASOF), CurveKind.RISK_FREE, null)
        assertEquals(3, active!!.version)
    }
}
