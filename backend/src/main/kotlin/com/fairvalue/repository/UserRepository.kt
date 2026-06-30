package com.fairvalue.repository

import com.fairvalue.domain.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 조직 격리 전제: 조회는 항상 orgId 로 스코프한다.
 *   - findByEmail: 로그인용(이메일 전역 유니크 — 앱 레벨 강제).
 *   - findByIdAndOrgId / findByOrgId: 관리자 조회·수정은 같은 조직만.
 */
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun findByOrgId(orgId: Long): List<UserEntity>
    fun findByIdAndOrgId(id: Long, orgId: Long): UserEntity?
}
