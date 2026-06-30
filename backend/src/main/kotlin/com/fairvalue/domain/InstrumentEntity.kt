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
 * instruments (V1) 매핑.
 *   - type/status 는 postgres enum(instrument_type / instrument_status).
 *   - org_id 는 조직 격리 기준.
 */
@Entity
@Table(name = "instruments")
class InstrumentEntity(
    @Column(name = "org_id", nullable = false)
    var orgId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "type", nullable = false, columnDefinition = "instrument_type")
    var type: InstrumentType,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "issuer", nullable = false)
    var issuer: String,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "status", nullable = false, columnDefinition = "instrument_status")
    var status: InstrumentStatus = InstrumentStatus.DRAFT,

    @Column(name = "project_id")
    var projectId: Long? = null,

    @Column(name = "created_by")
    var createdBy: Long? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,
)
