package com.fairvalue.dto

import com.fairvalue.domain.VolatilityData
import com.fairvalue.service.VolatilityCalculator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 변동성 API DTO (E3b). 표시값은 % 단위(커브와 일관). 산출 내부는 소수 → DTO 매핑 시 ×100.
 */

/** 회사별 산출표 1행(미리보기·상세·detail_json 공통). */
data class CompanyVolDto(
    val name: String,
    val observations: Int,
    @JsonProperty("period_start") val periodStart: LocalDate? = null,
    @JsonProperty("period_end") val periodEnd: LocalDate? = null,
    @JsonProperty("daily_vol_percent") val dailyVolPercent: Double = 0.0,
    @JsonProperty("annual_vol_percent") val annualVolPercent: Double = 0.0,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun from(c: VolatilityCalculator.Company) = CompanyVolDto(
            name = c.name, observations = c.observations,
            periodStart = c.periodStart, periodEnd = c.periodEnd,
            dailyVolPercent = c.dailyVol * 100.0, annualVolPercent = c.annualVol * 100.0,
            warnings = c.warnings,
        )
    }
}

/** POST /volatilities/compute 응답(미리보기, 저장 안 함). */
data class VolatilityComputeResponse(
    val companies: List<CompanyVolDto>,
    @JsonProperty("average_percent") val averagePercent: Double,
    @JsonProperty("trading_days_used") val tradingDaysUsed: Int,
    val warnings: List<String>,
)

/** POST /volatilities 등록 시 산출근거 입력(PEER_CSV). 미리보기 확인값을 그대로 싣는다. */
data class VolatilityDetailInput(
    val companies: List<CompanyVolDto>? = null,
    @JsonProperty("computed_average_percent") val computedAveragePercent: Double? = null,
    val edited: Boolean? = null,
    @JsonProperty("source_filenames") val sourceFilenames: List<String>? = null,
    val warnings: List<String>? = null,
)

/** POST /volatilities 본문. DIRECT: source_note. PEER_CSV: detail(회사표) + trading_days_used. */
data class VolatilityRegisterRequest(
    @JsonProperty("as_of") val asOf: LocalDate? = null,
    val label: String? = null,
    val method: String? = null,                        // DIRECT | PEER_CSV
    @JsonProperty("annual_vol_percent") val annualVolPercent: BigDecimal? = null,   // 채택값
    @JsonProperty("trading_days_used") val tradingDaysUsed: Int? = null,
    @JsonProperty("source_note") val sourceNote: String? = null,
    val detail: VolatilityDetailInput? = null,
)

data class VolatilityRegisterResult(
    @JsonProperty("volatility_id") val volatilityId: Long,
)

/** GET /volatilities 목록 아이템. */
data class VolatilityDto(
    val id: Long,
    @JsonProperty("as_of") val asOf: LocalDate,
    val label: String,
    @JsonProperty("annual_vol_percent") val annualVolPercent: BigDecimal,
    val method: String,
    @JsonProperty("trading_days_used") val tradingDaysUsed: Int,
    @JsonProperty("created_at") val createdAt: OffsetDateTime?,
) {
    companion object {
        fun from(v: VolatilityData) = VolatilityDto(
            id = v.id!!, asOf = v.asOf, label = v.label,
            annualVolPercent = v.annualVolPercent, method = v.method,
            tradingDaysUsed = v.tradingDaysUsed, createdAt = v.createdAt,
        )
    }
}

data class VolatilityListResponse(val items: List<VolatilityDto>)

/** GET /volatilities/{id} 단건(detail_json 포함). */
data class VolatilityDetailDto(
    val id: Long,
    @JsonProperty("as_of") val asOf: LocalDate,
    val label: String,
    @JsonProperty("annual_vol_percent") val annualVolPercent: BigDecimal,
    val method: String,
    @JsonProperty("trading_days_used") val tradingDaysUsed: Int,
    val detail: JsonNode?,
    @JsonProperty("created_by") val createdBy: Long?,
    @JsonProperty("created_at") val createdAt: OffsetDateTime?,
)
