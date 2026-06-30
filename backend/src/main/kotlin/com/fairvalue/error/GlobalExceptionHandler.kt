package com.fairvalue.error

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전 컨트롤러 공통 예외 → openapi Error 스키마({code,message,fields[]}) 변환.
 *   - ApiException: 정의된 status/code 그대로.
 *   - MethodArgumentNotValidException(@Valid 실패): 422 + 필드 오류 목록.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status)
            .body(ErrorResponse(code = ex.code, message = ex.message, fields = ex.fields))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fields = ex.bindingResult.fieldErrors.map {
            FieldErrorDto(field = it.field, message = it.defaultMessage ?: "유효하지 않은 값입니다.")
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(code = "E422", message = "입력값 검증에 실패했습니다.", fields = fields))
    }
}
