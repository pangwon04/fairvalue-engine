package com.fairvalue.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

/** JPQL projection(blob 제외). 목록 성능·메모리 보호. */
data class ReportMetaDto(
    val id: Long,
    val jobId: Long,
    val instrumentId: Long,
    val reportNo: String,
    val valuationDate: String?,
    val issuedAt: OffsetDateTime?,
    val issuedBy: Long?,
)

/** POST /jobs/{id}/report 응답. */
data class ReportIssueResult(
    @JsonProperty("report_id") val reportId: Long,
    @JsonProperty("report_no") val reportNo: String,
)

/** GET /reports 목록 아이템(상품명·기준일 enrich). */
data class ReportItemDto(
    @JsonProperty("report_id") val reportId: Long,
    @JsonProperty("report_no") val reportNo: String,
    @JsonProperty("job_id") val jobId: Long,
    @JsonProperty("instrument_id") val instrumentId: Long,
    @JsonProperty("instrument_name") val instrumentName: String?,
    @JsonProperty("instrument_type") val instrumentType: String?,
    @JsonProperty("valuation_date") val valuationDate: String?,
    @JsonProperty("issued_at") val issuedAt: String?,
)

data class ReportListResponse(val items: List<ReportItemDto>)
