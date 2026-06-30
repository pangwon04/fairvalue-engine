package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.PricingTrigger
import com.fairvalue.error.NotFoundException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
 * Phase 4-α: RealContextResolver — curves.*_ref → 포인트 스냅샷 resolve, org 격리, 우선순위, input_hash.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RealContextResolverIntegrationTest {

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
    @Autowired private lateinit var resolver: ContextResolver  // @Primary → RealContextResolver
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

    private fun uploadCurve(token: String, kind: String, asOf: String, origin: String = "UPLOAD", grade: String? = null): Long {
        val body = mutableMapOf<String, Any>(
            "as_of" to asOf, "kind" to kind, "origin" to origin,
            "points" to listOf(mapOf("tenor_years" to 1, "rate_percent" to 3.0), mapOf("tenor_years" to 3, "rate_percent" to 3.3)),
        )
        if (grade != null) body["grade"] = grade
        val res = post("/curves", body, token)
        assertEquals(201, res.statusCode(), res.body())
        return mapper.readTree(res.body()).get("upload_id").asLong()
    }

    private fun rawForm(rfRef: Long?, crRef: Long?): JsonNode {
        val curves = mutableMapOf<String, Any>()
        rfRef?.let { curves["risk_free_ref"] = it }
        crRef?.let { curves["credit_ref"] = it }
        return mapper.valueToTree(mapOf("valuation_date" to "2024-06-26", "curves" to curves, "model" to "TF_LATTICE", "seed" to 20240101))
    }

    @Test
    fun `ref 로 커브 조회해 ValuationContext 스냅샷을 채운다`() {
        val node = signup(); val token = node.get("token").asText(); val orgId = node.get("user").get("organization_id").asLong()
        val rf = uploadCurve(token, "RISK_FREE", "2024-06-26")
        val cr = uploadCurve(token, "CREDIT", "2024-06-26", grade = "BB")

        val ctx = resolver.resolve(rawForm(rf, cr), PricingTrigger(), InstrumentType.CB, orgId).contextJson
        val curves = ctx.get("curves")
        // *_curve 포인트 배열로 채워짐, *_ref 제거
        assertTrue(curves.get("risk_free_curve").isArray && curves.get("risk_free_curve").size() == 2)
        assertEquals(1.0, curves.get("risk_free_curve").get(0).get(0).asDouble())
        assertEquals(3.0, curves.get("risk_free_curve").get(0).get(1).asDouble())
        assertTrue(curves.get("credit_curve").isArray)
        assertEquals("v1", curves.get("curve_version").asText())
        assertTrue(curves.path("risk_free_ref").isMissingNode, "*_ref 는 최종 컨텍스트에서 제거")
    }

    @Test
    fun `input_hash 는 커브 스냅샷을 반영한다 (같은 ref 같은 hash, 다른 커브 다른 hash)`() {
        val node = signup(); val token = node.get("token").asText(); val orgId = node.get("user").get("organization_id").asLong()
        val rf = uploadCurve(token, "RISK_FREE", "2024-06-26")
        val h1 = resolver.resolve(rawForm(rf, null), PricingTrigger(), InstrumentType.CB, orgId).inputHash
        val h1b = resolver.resolve(rawForm(rf, null), PricingTrigger(), InstrumentType.CB, orgId).inputHash
        assertEquals(h1, h1b, "같은 커브 → 같은 input_hash(결정성)")
        assertTrue(Regex("^[a-f0-9]{64}$").matches(h1))

        // 다른 포인트의 커브(다른 ref) → 다른 hash
        val rf2body = mapOf("as_of" to "2024-06-26", "kind" to "RISK_FREE", "origin" to "UPLOAD",
            "points" to listOf(mapOf("tenor_years" to 1, "rate_percent" to 9.9), mapOf("tenor_years" to 3, "rate_percent" to 9.9)))
        val rf2 = mapper.readTree(post("/curves", rf2body, token).body()).get("upload_id").asLong()
        val h2 = resolver.resolve(rawForm(rf2, null), PricingTrigger(), InstrumentType.CB, orgId).inputHash
        assertNotEquals(h1, h2, "커브 포인트가 다르면 input_hash 변경")
    }

    @Test
    fun `우선순위 — ref 없으면 MANUAL이 UPLOAD보다 우선(version 낮아도)`() {
        val node = signup(); val token = node.get("token").asText(); val orgId = node.get("user").get("organization_id").asLong()
        // MANUAL 먼저(v1), UPLOAD 나중(v2) — 우선순위는 MANUAL(v1)
        uploadCurve(token, "RISK_FREE", "2024-06-26", origin = "MANUAL")
        uploadCurve(token, "RISK_FREE", "2024-06-26", origin = "UPLOAD")
        val ctx = resolver.resolve(rawForm(null, null), PricingTrigger(), InstrumentType.CB, orgId).contextJson
        assertEquals("v1", ctx.get("curves").get("curve_version").asText(), "MANUAL(v1) 우선 선택")
    }

    @Test
    fun `조직 격리 — 타 조직 커브 ref 는 resolve 불가(404)`() {
        val a = signup(); val tokenA = a.get("token").asText()
        val rfA = uploadCurve(tokenA, "RISK_FREE", "2024-06-26")
        val b = signup(); val orgB = b.get("user").get("organization_id").asLong()
        assertThrows(NotFoundException::class.java) {
            resolver.resolve(rawForm(rfA, null), PricingTrigger(), InstrumentType.CB, orgB)
        }
    }
}
