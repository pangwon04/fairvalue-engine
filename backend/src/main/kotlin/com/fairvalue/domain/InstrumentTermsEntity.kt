package com.fairvalue.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * instrument_terms (V1) 매핑.
 *   - terms_json: rawForm draft 전체(jsonb). 문자열(JSON)로 보관.
 *   - valuation_date/issue_date/maturity_date: 주요 조회 컬럼(rawForm 에서 추출).
 *   - instrument 당 1행(uq_instrument_terms_instrument). 저장은 upsert(있으면 갱신).
 */
@Entity
@Table(name = "instrument_terms")
class InstrumentTermsEntity(
    @Column(name = "instrument_id", nullable = false)
    var instrumentId: Long,

    @Column(name = "org_id", nullable = false)
    var orgId: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "terms_json", nullable = false, columnDefinition = "jsonb")
    var termsJson: String,

    @Column(name = "valuation_date")
    var valuationDate: LocalDate? = null,

    @Column(name = "issue_date")
    var issueDate: LocalDate? = null,

    @Column(name = "maturity_date")
    var maturityDate: LocalDate? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: OffsetDateTime? = null,
)
