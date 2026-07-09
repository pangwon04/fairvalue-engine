package com.fairvalue.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * ★5-10 대시보드 요약(GET /dashboard/summary). 순수 현황 집계 — 계산·설정 기능 없음.
 *   org 격리. 숨김(hidden_at != null) job 은 jobs.done/failed·recent_jobs 에서 제외.
 */
data class DashboardSummaryDto(
    val instruments: InstrumentsBlock,
    val jobs: JobsBlock,
    val reports: ReportsBlock,
    val parameters: ParametersBlock,
    @JsonProperty("by_type") val byType: List<TypeCountDto>,
    @JsonProperty("recent_jobs") val recentJobs: List<RecentJobDto>,
)

/** 등록 상품: 활성(비ARCHIVED)·보관(ARCHIVED). */
data class InstrumentsBlock(val active: Long, val archived: Long)

/** 평가 수행(숨김 제외): 완료·실패. */
data class JobsBlock(val done: Long, val failed: Long)

data class ReportsBlock(val count: Long)

/** 등록 파라미터: 커브·변동성 수. */
data class ParametersBlock(val curves: Long, val volatilities: Long)

/** 상품 유형별 현황(활성 기준). */
data class TypeCountDto(val type: String, val count: Long)

/** 최근 평가(숨김 제외, 최신순). report_issued=valuation_reports 에 job_id 존재. */
data class RecentJobDto(
    @JsonProperty("job_id") val jobId: Long,
    @JsonProperty("instrument_name") val instrumentName: String?,
    val type: String?,
    @JsonProperty("valuation_date") val valuationDate: String?,
    val model: String?,
    @JsonProperty("total_fair_value") val totalFairValue: Double?,
    val status: String,
    @JsonProperty("report_issued") val reportIssued: Boolean,
)
