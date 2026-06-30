package com.fairvalue.service

import com.fairvalue.dto.UpdateRoleRequest
import com.fairvalue.dto.UserDto
import com.fairvalue.error.NotFoundException
import com.fairvalue.repository.UserRepository
import com.fairvalue.security.AuthPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 조직 관리(ORG_ADMIN). ★ 모든 조회·수정은 호출자의 orgId(=JWT)로 스코프한다.
 * 타 조직 user 는 리포지토리에서 결과가 없으므로 누출 0(조회 제외 / 수정 404).
 */
@Service
class UserAdminService(
    private val userRepo: UserRepository,
) {

    @Transactional(readOnly = true)
    fun listUsers(caller: AuthPrincipal): List<UserDto> =
        userRepo.findByOrgId(caller.orgId).map { UserDto.from(it) }

    @Transactional
    fun updateRole(caller: AuthPrincipal, targetUserId: Long, req: UpdateRoleRequest): UserDto {
        // 같은 조직의 사용자만 조회됨 → 타 조직 id 는 null → 404(누출 차단).
        val user = userRepo.findByIdAndOrgId(targetUserId, caller.orgId)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다.")
        user.role = req.role
        return UserDto.from(userRepo.save(user))
    }
}
