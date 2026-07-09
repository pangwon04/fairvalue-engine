package com.fairvalue.pricing

import com.fairvalue.dto.PricingResult
import com.fasterxml.jackson.databind.JsonNode

/**
 * ★5-8fix(수정 A): 엔진 응답 원문 보존 + 타입 파싱 병행.
 *   - raw   : 엔진 응답 본문 원문(JsonNode). trees·curve_bootstrap·sensitivity 등 옵션 키 전부 보존.
 *             ★저장(pricing_jobs.result_json)은 이 raw 를 그대로 쓴다(하위호환·미지 키 소실 방지).
 *   - result: 검증·total 추출용 타입 DTO(동작 불변). 파싱 실패 시 엔진 오류로 처리.
 */
data class PricingOutcome(
    val raw: JsonNode,
    val result: PricingResult,
)
