package com.fairvalue.service

import com.fairvalue.domain.JobStatus
import com.fairvalue.domain.ValuationReport
import com.fairvalue.dto.ReportIssueResult
import com.fairvalue.dto.ReportItemDto
import com.fairvalue.error.ConflictException
import com.fairvalue.error.NotFoundException
import com.fairvalue.repository.InstrumentRepository
import com.fairvalue.repository.PricingJobRepository
import com.fairvalue.repository.ValuationReportRepository
import com.fairvalue.security.AuthPrincipal
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.Year

/**
 * 평가보고서 발급·목록·다운로드 (5-8 파트 B).
 *   발급: DONE + 계산근거(trees·curve_bootstrap) 있는 job 만. 구버전은 차단(재평가 후).
 *   재발급=새 레코드(report_no 증가·이력 보존). org 격리. 목록은 blob 제외 projection.
 */
@Service
class ReportService(
    private val reportRepo: ValuationReportRepository,
    private val jobRepo: PricingJobRepository,
    private val instrumentRepo: InstrumentRepository,
    private val pdfBuilder: ReportPdfBuilder,
    private val excelBuilder: ReportExcelBuilder,
    private val mapper: ObjectMapper,
) {

    enum class Format { PDF, EXCEL }

    @Transactional
    fun issue(caller: AuthPrincipal, jobId: Long): ReportIssueResult {
        WriteAccess.require(caller)
        val job = jobRepo.findByIdAndOrgId(jobId, caller.orgId)
            ?: throw NotFoundException("Job 을 찾을 수 없습니다.")
        if (job.status != JobStatus.DONE) {
            throw ConflictException("DONE 상태의 평가만 보고서 발급이 가능합니다(현재 ${job.status}).")
        }
        val result = job.resultJson?.let { mapper.readTree(it) }
            ?: throw ConflictException("평가 결과가 없습니다.")
        // ★구버전 차단: 계산근거(트리·이자율 산정) 없으면 불완전 보고서 방지.
        if (!result.path("trees").isObject || !result.path("curve_bootstrap").isObject) {
            throw ConflictException("이 평가에는 계산근거(가격트리·이자율 산정)가 없어 보고서를 발급할 수 없습니다. 재평가 후 발급하세요.")
        }
        val inst = instrumentRepo.findByIdAndOrgId(job.instrumentId, caller.orgId)
            ?: throw NotFoundException("상품을 찾을 수 없습니다.")
        val context = job.contextJson?.let { runCatching { mapper.readTree(it) }.getOrNull() }
        val valDate = result.path("valuation_date").asText(null)
        val issuedAt = OffsetDateTime.now()

        // report_no 동시성: (org_id, report_no) UNIQUE + 충돌 시 재시도(seq 증가).
        var seq = reportRepo.countByOrgId(caller.orgId) + 1
        repeat(6) {
            val reportNo = "FVE-${Year.now().value}-${"%06d".format(seq)}"
            val pdf = pdfBuilder.build(reportNo, inst.name, inst.type.name, inst.issuer, result, context, issuedAt.toString())
            val excel = excelBuilder.build(reportNo, inst.name, inst.type.name, result, context)
            try {
                val saved = reportRepo.saveAndFlush(
                    ValuationReport(
                        orgId = caller.orgId, jobId = jobId, instrumentId = job.instrumentId,
                        reportNo = reportNo, valuationDate = valDate, issuedBy = caller.userId,
                        pdfBytes = pdf, excelBytes = excel,
                    ),
                )
                return ReportIssueResult(reportId = saved.id!!, reportNo = reportNo)
            } catch (e: DataIntegrityViolationException) {
                seq++   // report_no (org_id, report_no) 충돌 → 다음 번호로 재시도
            }
        }
        throw ConflictException("발급번호 생성 충돌로 발급에 실패했습니다. 다시 시도하세요.")
    }

    @Transactional(readOnly = true)
    fun list(caller: AuthPrincipal): List<ReportItemDto> {
        val instMap = instrumentRepo.findByOrgId(caller.orgId).associateBy { it.id }
        return reportRepo.findMetaByOrgId(caller.orgId).map { m ->
            val inst = instMap[m.instrumentId]
            ReportItemDto(
                reportId = m.id, reportNo = m.reportNo, jobId = m.jobId, instrumentId = m.instrumentId,
                instrumentName = inst?.name, instrumentType = inst?.type?.name,
                valuationDate = m.valuationDate, issuedAt = m.issuedAt?.toString(),
            )
        }
    }

    @Transactional(readOnly = true)
    fun download(caller: AuthPrincipal, reportId: Long, format: Format): Pair<ByteArray, String> {
        val rep = reportRepo.findByIdAndOrgId(reportId, caller.orgId)
            ?: throw NotFoundException("보고서를 찾을 수 없습니다.")
        return when (format) {
            Format.PDF -> rep.pdfBytes to "${rep.reportNo}.pdf"
            Format.EXCEL -> rep.excelBytes to "${rep.reportNo}.xlsx"
        }
    }
}
