package com.fairvalue.error

import org.springframework.http.HttpStatus

/**
 * openapi.yaml 의 공통 Error 스키마: { code, message, fields[] }.
 */
data class FieldErrorDto(
    val field: String,
    val message: String,
)

data class ErrorResponse(
    val code: String,
    val message: String,
    val fields: List<FieldErrorDto>? = null,
)

/**
 * API 예외 기반. status + code(에러코드) + message 를 들고 다닌다.
 *   - 409 중복: ConflictException (code=E409)
 *   - 401 미인증: UnauthorizedException (code=E401) — 보통 보안 필터/엔트리포인트에서 처리
 *   - 403 권한없음: ForbiddenException (code=E403)
 *   - 404 없음(타 조직 리소스 포함): NotFoundException (code=E404)
 *   - 422 검증: ValidationException (code=E422, fields 포함)
 */
sealed class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
    val fields: List<FieldErrorDto>? = null,
) : RuntimeException(message)

class ConflictException(message: String) :
    ApiException(HttpStatus.CONFLICT, "E409", message)

class UnauthorizedException(message: String) :
    ApiException(HttpStatus.UNAUTHORIZED, "E401", message)

class ForbiddenException(message: String) :
    ApiException(HttpStatus.FORBIDDEN, "E403", message)

class NotFoundException(message: String) :
    ApiException(HttpStatus.NOT_FOUND, "E404", message)

class ValidationException(message: String, fields: List<FieldErrorDto>? = null) :
    ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "E422", message, fields)
