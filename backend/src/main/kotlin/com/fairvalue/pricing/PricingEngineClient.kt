package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.dto.PricingResult

/**
 * ResolvedContext → PricingResult.
 *
 * Phase 3 교체 지점: FastApiPricingEngineClient 가 pricing-engine(FastAPI /price)을 호출해
 * 실제 계산 결과를 받는다. 호출부(JobService)는 이 인터페이스에만 의존.
 */
interface PricingEngineClient {
    fun price(context: ResolvedContext, instrument: InstrumentEntity, jobId: Long): PricingResult
}
