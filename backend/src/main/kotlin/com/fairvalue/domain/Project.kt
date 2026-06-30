package com.fairvalue.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * projects (V1) 매핑. 조직 격리: org_id 스코프.
 */
@Entity
@Table(name = "projects")
class Project(
    @Column(name = "org_id", nullable = false)
    var orgId: Long,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "created_by")
    var createdBy: Long? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,
)
