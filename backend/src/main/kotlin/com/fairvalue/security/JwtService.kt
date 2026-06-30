package com.fairvalue.security

import com.fairvalue.domain.UserEntity
import com.fairvalue.domain.UserRole
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT 발급/검증. 클레임에 user_id·org_id·role·email 포함.
 * HS256, 시크릿은 JwtProperties(env JWT_SECRET).
 */
@Service
class JwtService(props: JwtProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(props.secret.toByteArray(StandardCharsets.UTF_8))
    private val expirationMs: Long = props.expirationMinutes * 60_000

    fun issue(user: UserEntity): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(user.id!!.toString())
            .claim("user_id", user.id)
            .claim("org_id", user.orgId)
            .claim("role", user.role.name)
            .claim("email", user.email)
            .issuedAt(Date(now))
            .expiration(Date(now + expirationMs))
            .signWith(key)
            .compact()
    }

    /** 유효하면 AuthPrincipal, 아니면(만료/변조/형식오류) null. */
    fun parse(token: String): AuthPrincipal? = try {
        val claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload
        AuthPrincipal(
            userId = (claims["user_id"] as Number).toLong(),
            orgId = (claims["org_id"] as Number).toLong(),
            role = UserRole.valueOf(claims["role"] as String),
            email = claims["email"] as String,
        )
    } catch (e: JwtException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
