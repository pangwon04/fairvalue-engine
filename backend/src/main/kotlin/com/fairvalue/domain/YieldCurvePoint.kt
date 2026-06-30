package com.fairvalue.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * yield_curve_points (V4) — 만기점. seq 로 순서 보존. (upload_id, tenor_years) 유일.
 *   - tenor_years/rate_percent: NUMERIC ↔ BigDecimal.
 */
@Entity
@Table(name = "yield_curve_points")
class YieldCurvePoint(
    @Column(name = "upload_id", nullable = false)
    var uploadId: Long,

    @Column(name = "tenor_years", nullable = false)
    var tenorYears: BigDecimal,

    @Column(name = "rate_percent", nullable = false)
    var ratePercent: BigDecimal,

    @Column(name = "seq", nullable = false)
    var seq: Int,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
)
