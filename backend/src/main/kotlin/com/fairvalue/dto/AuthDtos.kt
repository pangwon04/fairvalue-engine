package com.fairvalue.dto

import com.fairvalue.domain.UserEntity
import com.fairvalue.domain.UserRole
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * openapi.yaml 의 Auth/Org 스키마와 1:1 DTO.
 *   - SignupRequest {email, pw, org_code}
 *   - LoginRequest  {email, pw}
 *   - AuthResult    {token, user}
 *   - User          {id, email, role, organization_id}
 */

data class SignupRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String = "",

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val pw: String = "",

    @field:NotBlank(message = "조직 코드는 필수입니다.")
    @field:JsonProperty("org_code")
    val orgCode: String = "",
)

data class LoginRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String = "",

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val pw: String = "",
)

data class UserDto(
    val id: Long,
    val email: String,
    val role: UserRole,
    @JsonProperty("organization_id")
    val organizationId: Long,
) {
    companion object {
        fun from(u: UserEntity): UserDto =
            UserDto(id = u.id!!, email = u.email, role = u.role, organizationId = u.orgId)
    }
}

data class AuthResult(
    val token: String,
    val user: UserDto,
)

/** PATCH /admin/users/{id} 본문. */
data class UpdateRoleRequest(
    val role: UserRole = UserRole.VIEWER,
)

/** GET /admin/users 응답. */
data class UserListResponse(
    val items: List<UserDto>,
)

/** PATCH /admin/users/{id} 응답. */
data class UserWrapper(
    val user: UserDto,
)
