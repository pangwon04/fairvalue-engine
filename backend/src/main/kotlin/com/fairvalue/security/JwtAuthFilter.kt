package com.fairvalue.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization: Bearer <JWT> 를 검증해 SecurityContext 에 AuthPrincipal 을 싣는다.
 * authority 는 "ROLE_<role>" 형태(예: ROLE_ORG_ADMIN)로 RBAC(hasRole)에 사용.
 */
class JwtAuthFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            val principal = jwtService.parse(token)
            if (principal != null && SecurityContextHolder.getContext().authentication == null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))
                val auth = UsernamePasswordAuthenticationToken(principal, null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
