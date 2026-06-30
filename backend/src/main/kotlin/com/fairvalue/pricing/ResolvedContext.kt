package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentType
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * ContextResolver 산출물 = 엔진에 넘길 평가 컨텍스트.
 *   - contextJson: ValuationContext 자리표시(JSON). Phase 1-B-3 에서는 rawForm 통과 + 식별/모형 주입.
 *   - inputHash: 진짜 InputHash 산출값(재현성·캐시 키).
 * Phase 3 에서 RealContextResolver 가 같은 타입을 채우면 호출부 불변.
 */
data class ResolvedContext(
    val type: InstrumentType,
    val valuationDate: String?,
    val model: String,
    val seed: Long,
    val modelVersion: String,
    val inputHash: String,
    val contextJson: ObjectNode,
)
