package com.fairvalue.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

/**
 * PUT /instruments/{id}/terms
 *   - 요청: rawForm draft 전체(terms/rights/market/curves(*_ref)/model/seed/options/metadata).
 *           자유 형태이므로 JsonNode 로 받는다(구조·룰 검증은 검증기가 수행).
 *   - 응답: { saved, has_errors, validation:[{field,rule,severity,message}] }
 */

/** 검증 이슈 1건. severity: "error"(저장하되 has_errors) | "warning". */
data class ValidationIssue(
    val field: String?,
    val rule: String,
    val severity: String,
    val message: String,
)

data class TermsSaveResponse(
    val saved: Boolean,
    @JsonProperty("has_errors")
    val hasErrors: Boolean,
    val validation: List<ValidationIssue>,
)

/** 보조 GET /instruments/{id}/terms 응답. rawForm 원본 + 검증 결과(참고). */
data class TermsDraftResponse(
    @JsonProperty("instrument_id")
    val instrumentId: Long,
    val rawForm: JsonNode,
    val validation: List<ValidationIssue>,
)
