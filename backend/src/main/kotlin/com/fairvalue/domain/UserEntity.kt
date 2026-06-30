package com.fairvalue.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.OffsetDateTime

/**
 * users (V1) 매핑.
 *   - role 은 postgres enum user_role 에 매핑(Hibernate 6 PostgreSQLEnumJdbcType).
 *   - org_id 는 조직 격리의 기준 컬럼.
 *   - password_hash 에 BCrypt 해시만 저장(평문 금지).
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Column(name = "org_id", nullable = false)
    var orgId: Long,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "role", nullable = false, columnDefinition = "user_role")
    var role: UserRole,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,
)
