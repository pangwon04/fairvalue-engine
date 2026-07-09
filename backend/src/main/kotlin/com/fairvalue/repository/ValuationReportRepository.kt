package com.fairvalue.repository

import com.fairvalue.domain.ValuationReport
import com.fairvalue.dto.ReportMetaDto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ValuationReportRepository : JpaRepository<ValuationReport, Long> {

    fun findByIdAndOrgId(id: Long, orgId: Long): ValuationReport?

    fun countByOrgId(orgId: Long): Long

    /** ★목록: blob(pdf/excel bytea) 제외 메타만 SELECT — 성능·메모리 보호. */
    @Query(
        """
        SELECT new com.fairvalue.dto.ReportMetaDto(
            r.id, r.jobId, r.instrumentId, r.reportNo, r.valuationDate, r.issuedAt, r.issuedBy)
        FROM ValuationReport r
        WHERE r.orgId = :orgId
        ORDER BY r.id DESC
        """,
    )
    fun findMetaByOrgId(@Param("orgId") orgId: Long): List<ReportMetaDto>

    /** ★5-10 대시보드: 보고서가 발급된 job_id 집합(중복 제거). recent_jobs 의 report_issued 를 일괄 판정(N+1 금지). */
    @Query("SELECT DISTINCT r.jobId FROM ValuationReport r WHERE r.orgId = :orgId")
    fun findDistinctJobIdsByOrgId(@Param("orgId") orgId: Long): List<Long>
}
