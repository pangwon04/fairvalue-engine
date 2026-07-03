package com.fairvalue.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * volatility_data (V5) — 변동성 파라미터. 조직 격리(org_id).
 *   - method: DIRECT(직접 입력) | PEER_CSV(유사회사 주가 CSV 산출). varchar+CHECK.
 *   - annual_vol_percent: 채택 연변동성(%). trading_days_used: 연환산 거래일수(재현성).
 *   - detailJson(JSONB, String): 산출근거 스냅샷 — 상세 화면·보고서가 그대로 읽는다.
 *       필수 키: source_filenames, uploaded_at, companies[{name,observations,period,daily_vol,annual_vol}],
 *                computed_average, adopted_value, edited, trading_days_used, warnings.
 */
@Entity
@Table(name = "volatility_data")
class VolatilityData(
    @Column(name = "org_id", nullable = false)
    var orgId: Long,

    @Column(name = "as_of", nullable = false)
    var asOf: LocalDate,

    @Column(name = "label", length = 200, nullable = false)
    var label: String,

    @Column(name = "annual_vol_percent", nullable = false)
    var annualVolPercent: BigDecimal,

    @Column(name = "method", length = 20, nullable = false)
    var method: String = "DIRECT",

    @Column(name = "trading_days_used", nullable = false)
    var tradingDaysUsed: Int = 250,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    var detailJson: String? = null,

    @Column(name = "created_by")
    var createdBy: Long? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,
)
