package com.fairvalue.web

import com.fairvalue.dto.UserDto
import com.fairvalue.error.NotFoundException
import com.fairvalue.repository.UserRepository
import com.fairvalue.security.AuthPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 보조 엔드포인트(현재 openapi 계약에는 없음): 현재 토큰의 사용자 정보.
 * 인증 토큰 검증·조직 격리 시연용. 다음 단계에 openapi 반영 예정.
 * 조직 격리: 자신의 orgId 범위에서만 조회.
 */
@RestController
class MeController(private val userRepo: UserRepository) {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal caller: AuthPrincipal): UserDto {
        val user = userRepo.findByIdAndOrgId(caller.userId, caller.orgId)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다.")
        return UserDto.from(user)
    }
}
