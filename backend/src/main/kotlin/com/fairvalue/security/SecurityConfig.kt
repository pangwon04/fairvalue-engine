package com.fairvalue.security

import com.fairvalue.error.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Stateless 보안 체인.
 *   - 공개: health, auth(로그인·가입), swagger·openapi 문서
 *   - admin 이하 경로: ROLE_ORG_ADMIN 만
 *   - 그 외 : 인증 필요
 *   - 401(미인증)/403(권한없음) 은 공통 Error JSON 으로 응답.
 *   - ★ CORS: 프론트(localhost:3000) 브라우저 요청의 preflight(OPTIONS) 통과. app.cors.allowed-origins 로 분리.
 */
@Configuration
class SecurityConfig(
    private val jwtService: JwtService,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }   // ★ CORS 연결
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // ★ preflight(OPTIONS) 는 인증 없이 통과(이중 안전).
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers(
                    "/health",
                    "/auth/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                ).permitAll()
                it.requestMatchers("/admin/**").hasRole("ORG_ADMIN")
                it.anyRequest().authenticated()
            }
            .addFilterBefore(
                JwtAuthFilter(jwtService),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    writeError(response, HttpStatus.UNAUTHORIZED, "E401", "인증이 필요합니다.")
                }
                it.accessDeniedHandler { _, response, _ ->
                    writeError(response, HttpStatus.FORBIDDEN, "E403", "권한이 없습니다.")
                }
            }
        return http.build()
    }

    /**
     * ★ 로컬 dev용 CORS. 배포 시 실제 origin 을 app.cors.allowed-origins(콤마구분)로 추가.
     *   allowCredentials=true 이므로 allowedOrigins 에 "*" 는 사용 불가(명시적 origin).
     */
    @Bean
    fun corsConfigurationSource(
        @Value("\${app.cors.allowed-origins:http://localhost:3000}") origins: String,
    ): CorsConfigurationSource {
        val cfg = CorsConfiguration()
        cfg.allowedOrigins = origins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        cfg.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")  // OPTIONS 필수(preflight)
        cfg.allowedHeaders = listOf("Authorization", "Content-Type")                      // 최소
        cfg.exposedHeaders = listOf("Authorization")
        cfg.allowCredentials = true                                                       // JWT Authorization 헤더
        cfg.maxAge = 3600L                                                                // preflight 캐시
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", cfg)
        return source
    }

    private fun writeError(
        response: jakarta.servlet.http.HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(objectMapper.writeValueAsString(ErrorResponse(code, message)))
    }
}
