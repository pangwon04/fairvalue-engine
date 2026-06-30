package com.fairvalue.web

import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.CurveOrigin
import com.fairvalue.dto.CurveDetailDto
import com.fairvalue.dto.CurveListResponse
import com.fairvalue.dto.CurveUploadCommand
import com.fairvalue.dto.CurveUploadJsonRequest
import com.fairvalue.dto.CurveUploadResult
import com.fairvalue.dto.PermissionRequestResponse
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.CurveService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

/**
 * openapi Curve 계약.
 *   POST /curves            : 업로드(JSON 본문 또는 multipart CSV). 응답 {upload_id, validation}.
 *   GET  /curves            : 목록(필터 kind/grade/as_of). [프롬프트 설계 — openapi 단건과 차이는 보고함]
 *   GET  /curves/{id}       : 단건(포인트 포함). [프롬프트 추가]
 *   POST /curves/permission-request : 권한 요청 골격.
 * 업로드 권한은 서비스에서 강제(CURVE_MANAGER/ORG_ADMIN), 조회는 인증 전원. org_id 격리.
 */
@RestController
class CurveController(private val curveService: CurveService) {

    // --- 업로드: JSON 본문 ---
    @PostMapping("/curves", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadJson(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @RequestBody req: CurveUploadJsonRequest,
    ): CurveUploadResult {
        val points = (req.points ?: emptyList()).map { it.tenorYears to it.ratePercent }
        val cmd = CurveUploadCommand(
            asOf = req.asOf, kind = req.kind, grade = req.grade, source = req.source,
            interpolationMethod = req.interpolationMethod, origin = req.origin, points = points,
        )
        return curveService.upload(caller, cmd)
    }

    // --- 업로드: multipart CSV (openapi 형식: as_of,kind,grade,file) ---
    @PostMapping("/curves", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadMultipart(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @RequestParam("as_of", required = false) asOf: String?,
        @RequestParam("kind", required = false) kind: String?,
        @RequestParam("grade", required = false) grade: String?,
        @RequestParam("source", required = false) source: String?,
        @RequestParam("interpolation_method", required = false) interpolation: String?,
        @RequestParam("origin", required = false) origin: String?,
        @RequestParam("file") file: MultipartFile,
    ): CurveUploadResult {
        val points = curveService.parsePointsCsv(file.bytes.toString(Charsets.UTF_8))
        val cmd = CurveUploadCommand(
            asOf = asOf?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            kind = kind?.let { runCatching { CurveKind.valueOf(it) }.getOrNull() },
            grade = grade,
            source = source,
            interpolationMethod = interpolation,
            origin = origin?.let { runCatching { CurveOrigin.valueOf(it) }.getOrNull() },
            points = points,
        )
        return curveService.upload(caller, cmd)
    }

    @GetMapping("/curves")
    fun list(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @RequestParam(required = false) kind: CurveKind?,
        @RequestParam(required = false) grade: String?,
        @RequestParam(name = "as_of", required = false) asOf: LocalDate?,
    ): CurveListResponse = CurveListResponse(items = curveService.list(caller, kind, grade, asOf))

    @GetMapping("/curves/{id}")
    fun get(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
    ): CurveDetailDto = curveService.getDetail(caller, id)

    @PostMapping("/curves/permission-request")
    @ResponseStatus(HttpStatus.CREATED)
    fun permissionRequest(
        @AuthenticationPrincipal caller: AuthPrincipal,
    ): PermissionRequestResponse =
        // 골격: 승인 큐는 후속. 추적용 request_id 만 반환(미영속).
        PermissionRequestResponse(requestId = System.nanoTime())
}
