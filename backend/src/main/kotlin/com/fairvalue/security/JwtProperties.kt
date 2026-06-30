package com.fairvalue.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * jwt.* 설정 바인딩. secret 은 env(JWT_SECRET) 기반(application.yml).
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val expirationMinutes: Long = 120,
)
