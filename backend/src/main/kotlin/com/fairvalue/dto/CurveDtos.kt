package com.fairvalue.dto

import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.CurveOrigin
import com.fairvalue.domain.YieldCurvePoint
import com.fairvalue.domain.YieldCurveUpload
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate

/**
 * openapi Curve 계약 DTO. POST /curves(JSON 본문) / GET /curves(목록) / GET /curves/{id}(단건).
 */

/** JSON 업로드 본문의 만기점 1건. */
data class CurvePointInput(
    @JsonProperty("tenor_years") val tenorYears: BigDecimal? = null,
    @JsonProperty("rate_percent") val ratePercent: BigDecimal? = null,
)

/** POST /curves JSON 본문. (multipart 는 컨트롤러에서 별도 처리) */
data class CurveUploadJsonRequest(
    @JsonProperty("as_of") val asOf: LocalDate? = null,
    val kind: CurveKind? = null,
    val grade: String? = null,
    val source: String? = null,
    @JsonProperty("interpolation_method") val interpolationMethod: String? = null,
    val origin: CurveOrigin? = null,
    val points: List<CurvePointInput>? = null,
)

/** 정규화된 업로드 명령(파싱 결과). multipart/JSON 공통. */
data class CurveUploadCommand(
    val asOf: LocalDate?,
    val kind: CurveKind?,
    val grade: String?,
    val source: String?,
    val interpolationMethod: String?,
    val origin: CurveOrigin?,
    val points: List<Pair<BigDecimal?, BigDecimal?>>,
)

/** 저장된 만기점. */
data class CurvePointDto(
    @JsonProperty("tenor_years") val tenorYears: BigDecimal,
    @JsonProperty("rate_percent") val ratePercent: BigDecimal,
    val seq: Int,
) {
    companion object {
        fun from(p: YieldCurvePoint) = CurvePointDto(p.tenorYears, p.ratePercent, p.seq)
    }
}

/** POST /curves 응답. */
data class CurveUploadResult(
    @JsonProperty("upload_id") val uploadId: Long,
    val validation: List<ValidationIssue>,
)

/** 커브 헤더(메타). GET /curves 목록 아이템. */
data class CurveUploadDto(
    val id: Long,
    val kind: CurveKind,
    val grade: String?,
    @JsonProperty("as_of") val asOf: LocalDate,
    val version: Int,
    val source: String?,
    @JsonProperty("interpolation_method") val interpolationMethod: String,
    val origin: CurveOrigin,
) {
    companion object {
        fun from(u: YieldCurveUpload) = CurveUploadDto(
            id = u.id!!, kind = u.kind, grade = u.grade, asOf = u.asOf, version = u.version,
            source = u.source, interpolationMethod = u.interpolationMethod, origin = u.origin,
        )
    }
}

data class CurveListResponse(val items: List<CurveUploadDto>)

/** GET /curves/{id} 단건(포인트 포함). */
data class CurveDetailDto(
    val id: Long,
    val kind: CurveKind,
    val grade: String?,
    @JsonProperty("as_of") val asOf: LocalDate,
    val version: Int,
    val source: String?,
    @JsonProperty("interpolation_method") val interpolationMethod: String,
    val origin: CurveOrigin,
    val points: List<CurvePointDto>,
)

/** POST /curves/permission-request 응답(골격). */
data class PermissionRequestResponse(
    @JsonProperty("request_id") val requestId: Long,
)
