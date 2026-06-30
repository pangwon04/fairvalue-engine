package com.fairvalue.security

import com.fairvalue.domain.UserRole

/**
 * 인증된 호출자. JWT 클레임에서 복원되며, 조직 격리(orgId)와 RBAC(role)의 신뢰 입력.
 * 컨트롤러는 org_id 를 본문/경로가 아니라 이 principal 에서만 읽는다.
 */
data class AuthPrincipal(
    val userId: Long,
    val orgId: Long,
    val role: UserRole,
    val email: String,
)
