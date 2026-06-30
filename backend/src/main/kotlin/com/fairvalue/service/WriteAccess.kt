package com.fairvalue.service

import com.fairvalue.domain.UserRole
import com.fairvalue.error.ForbiddenException
import com.fairvalue.security.AuthPrincipal

/**
 * 쓰기 권한 = VALUATOR 이상(ORG_ADMIN, VALUATOR). AUDITOR/VIEWER/CURVE_MANAGER 는 읽기만.
 * SecurityConfig(1-B-1)를 변경하지 않고 서비스 레이어에서 강제한다(403).
 */
object WriteAccess {
    private val WRITE_ROLES = setOf(UserRole.ORG_ADMIN, UserRole.VALUATOR)

    fun require(caller: AuthPrincipal) {
        if (caller.role !in WRITE_ROLES) {
            throw ForbiddenException("쓰기 권한이 없습니다(VALUATOR 이상 필요).")
        }
    }
}
