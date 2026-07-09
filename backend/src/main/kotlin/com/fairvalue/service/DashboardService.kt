package com.fairvalue.service

import com.fairvalue.domain.InstrumentStatus
import com.fairvalue.domain.JobStatus
import com.fairvalue.dto.DashboardSummaryDto
import com.fairvalue.dto.InstrumentsBlock
import com.fairvalue.dto.JobsBlock
import com.fairvalue.dto.ParametersBlock
import com.fairvalue.dto.RecentJobDto
import com.fairvalue.dto.ReportsBlock
import com.fairvalue.dto.TypeCountDto
import com.fairvalue.repository.InstrumentRepository
import com.fairvalue.repository.PricingJobRepository
import com.fairvalue.repository.ValuationReportRepository
import com.fairvalue.repository.YieldCurveUploadRepository
import com.fairvalue.repository.VolatilityDataRepository
import com.fairvalue.security.AuthPrincipal
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ★5-10 대시보드 집계(순수 현황). 조직 격리. 기존 repository 재사용 + count.
 *   ★N+1 금지: recent_jobs 의 report_issued 는 job_id 집합 일괄 조회로 판정.
 *   ★숨김 제외: jobs.done/failed·recent_jobs 는 hidden_at == null 만 집계.
 *   ★의미 없는 장식 없음 — 실데이터 집계만.
 */
@Service
class DashboardService(
    private val instrumentRepo: InstrumentRepository,
    private val jobRepo: PricingJobRepository,
    private val reportRepo: ValuationReportRepository,
    private val curveRepo: YieldCurveUploadRepository,
    private val volatilityRepo: VolatilityDataRepository,
    private val mapper: ObjectMapper,
) {

    @Transactional(readOnly = true)
    fun summary(caller: AuthPrincipal): DashboardSummaryDto {
        val orgId = caller.orgId

        // 상품(단일 조회) → 활성/보관 + 유형별.
        val insts = instrumentRepo.findByOrgId(orgId)
        val archived = insts.count { it.status == InstrumentStatus.ARCHIVED }.toLong()
        val active = insts.size.toLong() - archived
        val byType = insts.filter { it.status != InstrumentStatus.ARCHIVED }
            .groupingBy { it.type.name }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { TypeCountDto(it.first, it.second.toLong()) }

        // 평가 Job(단일 조회) → 숨김 제외 done/failed + 최근 8.
        val visible = jobRepo.findByOrgIdOrderByIdDesc(orgId).filter { it.hiddenAt == null }
        val done = visible.count { it.status == JobStatus.DONE }.toLong()
        val failed = visible.count { it.status == JobStatus.FAILED }.toLong()

        val reportedJobIds = reportRepo.findDistinctJobIdsByOrgId(orgId).toSet()   // 일괄(N+1 금지)
        val instMap = insts.associateBy { it.id }
        val recent = visible.take(8).map { j ->
            val jid = j.id!!
            var total: Double? = null
            var valDate: String? = null
            var model: String? = null
            if (j.status == JobStatus.DONE && !j.resultJson.isNullOrBlank()) {
                runCatching {
                    val node = mapper.readTree(j.resultJson)
                    node.get("total_fair_value")?.takeIf { it.isNumber }?.let { total = it.asDouble() }
                    node.get("valuation_date")?.takeIf { it.isTextual }?.let { valDate = it.asText() }
                    node.path("key_parameters").get("model_name")?.takeIf { it.isTextual }?.let { model = it.asText() }
                }
            }
            val inst = instMap[j.instrumentId]
            RecentJobDto(
                jobId = jid,
                instrumentName = inst?.name,
                type = inst?.type?.name,
                valuationDate = valDate,
                model = model,
                totalFairValue = total,
                status = j.status.name,
                reportIssued = jid in reportedJobIds,
            )
        }

        return DashboardSummaryDto(
            instruments = InstrumentsBlock(active = active, archived = archived),
            jobs = JobsBlock(done = done, failed = failed),
            reports = ReportsBlock(count = reportRepo.countByOrgId(orgId)),
            parameters = ParametersBlock(
                curves = curveRepo.countByOrgId(orgId),
                volatilities = volatilityRepo.countByOrgId(orgId),
            ),
            byType = byType,
            recentJobs = recent,
        )
    }
}
