package com.fairvalue.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * valuation_reports (V8) — 발급된 평가보고서(PDF+엑셀 bytea). org 격리. 재발급=새 레코드.
 *   report_no: org 별 발급번호(FVE-연도-6자리). (org_id, report_no) UNIQUE.
 */
@Entity
@Table(name = "valuation_reports")
class ValuationReport(
    @Column(name = "org_id", nullable = false)
    var orgId: Long,

    @Column(name = "job_id", nullable = false)
    var jobId: Long,

    @Column(name = "instrument_id", nullable = false)
    var instrumentId: Long,

    @Column(name = "report_no", length = 40, nullable = false)
    var reportNo: String,

    @Column(name = "valuation_date", length = 20)
    var valuationDate: String? = null,

    @Column(name = "issued_by")
    var issuedBy: Long? = null,

    @Column(name = "pdf_bytes", columnDefinition = "bytea", nullable = false)
    var pdfBytes: ByteArray,

    @Column(name = "excel_bytes", columnDefinition = "bytea", nullable = false)
    var excelBytes: ByteArray,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "issued_at", insertable = false, updatable = false)
    var issuedAt: OffsetDateTime? = null,
)
