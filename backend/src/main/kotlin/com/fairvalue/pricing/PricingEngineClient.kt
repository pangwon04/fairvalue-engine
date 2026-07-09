package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentEntity

/**
 * ResolvedContext → PricingOutcome(원문 raw + 타입 result).
 *
 * ★5-8fix: 반환을 PricingOutcome 으로 변경 — 저장은 raw 원문(옵션 키 보존), 검증/total 은 result.
 * 호출부(JobService)는 이 인터페이스에만 의존.
 */
interface PricingEngineClient {
    fun price(context: ResolvedContext, instrument: InstrumentEntity, jobId: Long): PricingOutcome
}
