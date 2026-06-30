package com.fairvalue.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Auth/Org/RBAC 통합테스트 — Testcontainers PostgreSQL 16 + Flyway V1.
 *
 * 핵심 입증:
 *   - 가입(신규조직=ORG_ADMIN / 기존조직=VALUATOR), 로그인, /me
 *   - ★ 조직 격리: 타 조직 사용자 조회 누출 0, 수정 404
 *   - 권한: VIEWER 의 /admin/users 접근 403
 *   - 중복 이메일 409, 미인증 401
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16")).apply {
                withDatabaseName("fairvalue")
                withUsername("fairvalue")
                withPassword("fairvalue")
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

    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var mapper: ObjectMapper

    // --- helpers ---
    private fun postJson(path: String, body: Map<String, Any?>, token: String? = null) =
        rest.exchange(path, HttpMethod.POST, jsonEntity(body, token), String::class.java)

    private fun jsonEntity(body: Map<String, Any?>?, token: String?): HttpEntity<String> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            if (token != null) setBearerAuth(token)
        }
        return if (body != null) HttpEntity(mapper.writeValueAsString(body), headers)
        else HttpEntity(headers)
    }

    private fun signup(email: String, pw: String, orgCode: String): JsonNode {
        val res = postJson("/auth/signup", mapOf("email" to email, "pw" to pw, "org_code" to orgCode))
        assertEquals(HttpStatus.CREATED, res.statusCode, res.body)
        return mapper.readTree(res.body)
    }

    private fun tokenOf(node: JsonNode) = node.get("token").asText()
    private fun roleOf(node: JsonNode) = node.get("user").get("role").asText()
    private fun userIdOf(node: JsonNode) = node.get("user").get("id").asLong()

    private fun uniq(prefix: String) = "$prefix-${System.nanoTime()}"

    @Test
    fun `신규 조직 가입자는 ORG_ADMIN, 같은 조직 후속 가입자는 VALUATOR`() {
        val orgCode = uniq("ORG")
        val admin = signup("admin-${uniq("a")}@x.com", "pw12345", orgCode)
        assertEquals("ORG_ADMIN", roleOf(admin))

        val second = signup("u-${uniq("b")}@x.com", "pw12345", orgCode)
        assertEquals("VALUATOR", roleOf(second))
    }

    @Test
    fun `중복 이메일 가입은 409`() {
        val email = "dup-${uniq("d")}@x.com"
        signup(email, "pw12345", uniq("ORG"))
        val res = postJson("/auth/signup", mapOf("email" to email, "pw" to "pw12345", "org_code" to uniq("ORG")))
        assertEquals(HttpStatus.CONFLICT, res.statusCode, res.body)
        assertEquals("E409", mapper.readTree(res.body).get("code").asText())
    }

    @Test
    fun `로그인 성공 토큰으로 me 조회, 잘못된 비번은 401`() {
        val email = "login-${uniq("l")}@x.com"
        signup(email, "pw12345", uniq("ORG"))

        val ok = postJson("/auth/login", mapOf("email" to email, "pw" to "pw12345"))
        assertEquals(HttpStatus.OK, ok.statusCode, ok.body)
        val token = tokenOf(mapper.readTree(ok.body))

        val me = rest.exchange("/me", HttpMethod.GET, jsonEntity(null, token), String::class.java)
        assertEquals(HttpStatus.OK, me.statusCode, me.body)
        assertEquals(email, mapper.readTree(me.body).get("email").asText())

        val bad = postJson("/auth/login", mapOf("email" to email, "pw" to "WRONGpw"))
        assertEquals(HttpStatus.UNAUTHORIZED, bad.statusCode)
    }

    @Test
    fun `조직 격리 — 타 조직 사용자 조회 누출 0, 수정 404`() {
        val orgA = uniq("ORGA")
        val orgB = uniq("ORGB")
        val adminA = signup("adminA-${uniq("a")}@x.com", "pw12345", orgA)
        val adminB = signup("adminB-${uniq("b")}@x.com", "pw12345", orgB)
        val userBEmail = "userB-${uniq("ub")}@x.com"
        val userB = signup(userBEmail, "pw12345", orgB) // orgB VALUATOR

        // A 관리자 목록: orgA 사용자만(adminA 1명), orgB 이메일 누출 없음
        val listA = rest.exchange("/admin/users", HttpMethod.GET, jsonEntity(null, tokenOf(adminA)), String::class.java)
        assertEquals(HttpStatus.OK, listA.statusCode, listA.body)
        val items = mapper.readTree(listA.body).get("items")
        assertEquals(1, items.size())
        val emails = items.map { it.get("email").asText() }
        assertFalse(emails.contains(userBEmail), "타 조직 이메일 누출")
        assertFalse(emails.contains(adminB.get("user").get("email").asText()), "타 조직 admin 누출")

        // A 관리자가 orgB 사용자 역할 변경 시도 → 404(누출 차단)
        val patch = rest.exchange(
            "/admin/users/${userIdOf(userB)}", HttpMethod.PATCH,
            jsonEntity(mapOf("role" to "AUDITOR"), tokenOf(adminA)), String::class.java,
        )
        assertEquals(HttpStatus.NOT_FOUND, patch.statusCode, patch.body)
    }

    @Test
    fun `권한 — VIEWER 토큰의 admin users 접근은 403`() {
        val orgCode = uniq("ORG")
        val admin = signup("admin-${uniq("a")}@x.com", "pw12345", orgCode)
        val vEmail = "viewer-${uniq("v")}@x.com"
        val viewer = signup(vEmail, "pw12345", orgCode) // VALUATOR

        // 관리자가 해당 사용자를 VIEWER 로 강등
        val patch = rest.exchange(
            "/admin/users/${userIdOf(viewer)}", HttpMethod.PATCH,
            jsonEntity(mapOf("role" to "VIEWER"), tokenOf(admin)), String::class.java,
        )
        assertEquals(HttpStatus.OK, patch.statusCode, patch.body)

        // VIEWER 로 재로그인 → /admin/users 403
        val login = postJson("/auth/login", mapOf("email" to vEmail, "pw" to "pw12345"))
        val vToken = tokenOf(mapper.readTree(login.body))
        val res = rest.exchange("/admin/users", HttpMethod.GET, jsonEntity(null, vToken), String::class.java)
        assertEquals(HttpStatus.FORBIDDEN, res.statusCode, res.body)
    }

    @Test
    fun `미인증 admin users 접근은 401`() {
        val res = rest.exchange("/admin/users", HttpMethod.GET, jsonEntity(null, null), String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, res.statusCode)
    }

    @Test
    fun `이메일 형식 오류 가입은 422`() {
        val res = postJson("/auth/signup", mapOf("email" to "not-an-email", "pw" to "pw12345", "org_code" to uniq("ORG")))
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, res.statusCode, res.body)
        assertEquals("E422", mapper.readTree(res.body).get("code").asText())
    }
}
