package com.fairvalue.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * organizations (V1) 매핑. 격리 루트 단위.
 */
@Entity
@Table(name = "organizations")
class Organization(
    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "org_code", nullable = false, unique = true)
    var orgCode: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,
)
