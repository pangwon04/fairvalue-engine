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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * pricing_jobs (V1 + V2.result_json/completed_at) 매핑.
 *   - status: postgres enum job_status.
 *   - input_hash: CHAR(64) SHA-256 hex(진짜 InputHash 산출). seed: BIGINT.
 *   - result_json: placeholder PricingResult(jsonb). 조직 격리 기준 org_id.
 */
@Entity
@Table(name = "pricing_jobs")
class PricingJobEntity(
    @Column(name = "org_id", nullable = false)
    var orgId: Long,

    @Column(name = "instrument_id", nullable = false)
    var instrumentId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "status", nullable = false, columnDefinition = "job_status")
    var status: JobStatus = JobStatus.QUEUED,

    // DB 는 CHAR(64)(bpchar) — 의도적 고정(^[a-f0-9]{64}$ CHECK). 엔티티를 DB 에 맞춘다.
    @Column(name = "input_hash", columnDefinition = "char(64)", length = 64)
    var inputHash: String? = null,

    @Column(name = "seed")
    var seed: Long? = null,

    @Column(name = "model_version")
    var modelVersion: String? = null,

    @Column(name = "cached", nullable = false)
    var cached: Boolean = false,

    @Column(name = "created_by")
    var createdBy: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    var resultJson: String? = null,

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,
)
