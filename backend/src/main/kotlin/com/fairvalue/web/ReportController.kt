package com.fairvalue.web

import com.fairvalue.dto.ReportIssueResult
import com.fairvalue.dto.ReportListResponse
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.ReportService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 평가보고서 API (5-8 파트 B).
 *   POST /jobs/{jobId}/report : 발급(DONE·계산근거 필요, 구버전 차단). 재발급=새 레코드.
 *   GET  /reports             : 발급 이력(org 격리, blob 제외).
 *   GET  /reports/{id}/pdf|excel : 다운로드(org 격리, 한글 파일명 RFC5987).
 */
@RestController
class ReportController(private val reportService: ReportService) {

    @PostMapping("/jobs/{jobId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable jobId: Long,
    ): ReportIssueResult = reportService.issue(caller, jobId)

    @GetMapping("/reports")
    fun list(
        @AuthenticationPrincipal caller: AuthPrincipal,
    ): ReportListResponse = ReportListResponse(items = reportService.list(caller))

    @GetMapping("/reports/{id}/pdf")
    fun pdf(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<ByteArray> {
        val (bytes, filename) = reportService.download(caller, id, ReportService.Format.PDF)
        return fileResponse(bytes, filename, MediaType.APPLICATION_PDF)
    }

    @GetMapping("/reports/{id}/excel")
    fun excel(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<ByteArray> {
        val (bytes, filename) = reportService.download(caller, id, ReportService.Format.EXCEL)
        return fileResponse(bytes, filename, MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    private fun fileResponse(bytes: ByteArray, filename: String, type: MediaType): ResponseEntity<ByteArray> {
        // RFC5987: 한글 파일명 대비 filename* 병기(현재 report_no 는 ASCII).
        val enc = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20")
        val cd = "attachment; filename=\"$filename\"; filename*=UTF-8''$enc"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, cd)
            .contentType(type)
            .body(bytes)
    }
}
