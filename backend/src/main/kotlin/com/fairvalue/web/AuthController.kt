package com.fairvalue.web

import com.fairvalue.dto.AuthResult
import com.fairvalue.dto.LoginRequest
import com.fairvalue.dto.SignupRequest
import com.fairvalue.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * openapi: POST /auth/signup (201), POST /auth/login (200). 공개 엔드포인트.
 */
@RestController
class AuthController(private val authService: AuthService) {

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody req: SignupRequest): AuthResult = authService.signup(req)

    @PostMapping("/auth/login")
    fun login(@Valid @RequestBody req: LoginRequest): AuthResult = authService.login(req)
}
