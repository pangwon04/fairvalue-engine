package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.PricingTrigger
import com.fasterxml.jackson.databind.JsonNode

/**
 * rawForm(draft) + trigger → ResolvedContext.
 *
 * Phase 3 교체 지점: RealContextResolver 가 커브 *_ref → 포인트 배열 스냅샷,
 * asset_id → market.{spot,volatility,dividend_yield} 보강(수동값 우선)을 수행한다.
 * 호출부(JobService)는 이 인터페이스에만 의존하므로 교체 시 흐름·테스트 불변.
 */
interface ContextResolver {
    fun resolve(rawForm: JsonNode, trigger: PricingTrigger, type: InstrumentType): ResolvedContext
}
