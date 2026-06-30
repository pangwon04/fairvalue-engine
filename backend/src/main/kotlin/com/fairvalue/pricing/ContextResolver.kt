package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.PricingTrigger
import com.fasterxml.jackson.databind.JsonNode

/**
 * rawForm(draft) + trigger → ResolvedContext.
 *
 * Phase 4-α: RealContextResolver 가 커브 *_ref → 포인트 배열 스냅샷으로 resolve 한다.
 *   - orgId 는 커브 조회의 조직 격리 기준(타 조직 커브 resolve 불가).
 *   - 호출부(JobService)는 caller.orgId 를 전달한다.
 * (asset_id → market.{spot,volatility,dividend_yield} 보강은 이후 묶음)
 */
interface ContextResolver {
    fun resolve(rawForm: JsonNode, trigger: PricingTrigger, type: InstrumentType, orgId: Long): ResolvedContext
}
