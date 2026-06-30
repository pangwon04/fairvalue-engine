package com.fairvalue.service

import com.fairvalue.domain.Organization
import com.fairvalue.domain.UserEntity
import com.fairvalue.domain.UserRole
import com.fairvalue.dto.AuthResult
import com.fairvalue.dto.LoginRequest
import com.fairvalue.dto.SignupRequest
import com.fairvalue.dto.UserDto
import com.fairvalue.error.ConflictException
import com.fairvalue.error.UnauthorizedException
import com.fairvalue.repository.OrganizationRepository
import com.fairvalue.repository.UserRepository
import com.fairvalue.security.JwtService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 가입/로그인.
 *
 * 가입 정책:
 *   - org_code 가 기존 조직과 일치 → 그 조직 사용자(기본 역할 VALUATOR).
 *   - org_code 미존재 → 신규 조직 생성 + 첫 사용자 ORG_ADMIN.
 * 이메일은 앱 레벨 전역 유니크(로그인 {email,pw} 를 결정적으로 만들기 위함).
 */
@Service
class AuthService(
    private val orgRepo: OrganizationRepository,
    private val userRepo: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {

    @Transactional
    fun signup(req: SignupRequest): AuthResult {
        // 이메일 전역 중복 차단(409).
        if (userRepo.existsByEmail(req.email)) {
            throw ConflictException("이미 사용 중인 이메일입니다.")
        }

        val existing = orgRepo.findByOrgCode(req.orgCode)
        val (org, role) = if (existing != null) {
            existing to UserRole.VALUATOR
        } else {
            // 신규 조직: 이름은 org_code 로 초기화(이후 변경 가능).
            val created = orgRepo.save(Organization(name = req.orgCode, orgCode = req.orgCode))
            created to UserRole.ORG_ADMIN
        }

        val user = userRepo.save(
            UserEntity(
                orgId = org.id!!,
                email = req.email,
                passwordHash = passwordEncoder.encode(req.pw),
                role = role,
            ),
        )
        return AuthResult(token = jwtService.issue(user), user = UserDto.from(user))
    }

    @Transactional(readOnly = true)
    fun login(req: LoginRequest): AuthResult {
        val user = userRepo.findByEmail(req.email)
            ?: throw UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.")
        if (!passwordEncoder.matches(req.pw, user.passwordHash)) {
            throw UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.")
        }
        return AuthResult(token = jwtService.issue(user), user = UserDto.from(user))
    }
}
