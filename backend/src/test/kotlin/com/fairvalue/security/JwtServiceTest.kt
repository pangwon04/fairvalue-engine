package com.fairvalue.security

import com.fairvalue.domain.UserEntity
import com.fairvalue.domain.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * JWT 발급/검증 단위 테스트(Spring 컨텍스트 불필요).
 */
class JwtServiceTest {

    private val secret = "unit-test-secret-key-which-is-long-enough-32+"
    private fun service(expMinutes: Long = 120) =
        JwtService(JwtProperties(secret = secret, expirationMinutes = expMinutes))

    private fun user() = UserEntity(
        orgId = 7L, email = "a@x.com", passwordHash = "h", role = UserRole.ORG_ADMIN, id = 42L,
    )

    @Test
    fun `발급한 토큰은 동일 클레임으로 복원된다`() {
        val token = service().issue(user())
        val p = service().parse(token)
        requireNotNull(p)
        assertEquals(42L, p.userId)
        assertEquals(7L, p.orgId)
        assertEquals(UserRole.ORG_ADMIN, p.role)
        assertEquals("a@x.com", p.email)
    }

    @Test
    fun `만료된 토큰은 null 을 반환한다`() {
        val expired = service(expMinutes = -1).issue(user()) // 이미 만료
        assertNull(service().parse(expired))
    }

    @Test
    fun `변조된 토큰은 null 을 반환한다`() {
        val token = service().issue(user())
        val tampered = token.dropLast(2) + "xy"
        assertNull(service().parse(tampered))
    }

    @Test
    fun `다른 시크릿으로 검증하면 null 을 반환한다`() {
        val token = service().issue(user())
        val other = JwtService(JwtProperties(secret = "another-secret-also-long-enough-to-pass-32", expirationMinutes = 120))
        assertNull(other.parse(token))
    }
}
