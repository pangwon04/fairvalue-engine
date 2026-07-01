package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.dto.Components
import com.fairvalue.dto.Issue
import com.fairvalue.dto.KeyParameters
import com.fairvalue.dto.PricingResult
import com.fairvalue.dto.Reproducibility
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * ★ 더미 PricingEngineClient — 실제 계산 없이 placeholder PricingResult 생성.
 *   - 형식은 진짜처럼: pricing-result.schema.json 1:1, 표준 component key 12종.
 *   - 값은 placeholder: components 전부 0, total_fair_value 0 → Σcomponents=total(0=0) 불변식 충족.
 *   - warnings 에 PLACEHOLDER 1건. errors 0건.
 *   - input_hash/seed/model_version 은 ResolvedContext 의 진짜 값을 그대로 담는다.
 *
 * Phase 3 에서 실제 엔진 호출 구현으로 교체 시 결과 형식이 바뀌지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "app.engine", name = ["mode"], havingValue = "dummy")
class DummyPricingEngineClient : PricingEngineClient {

    override fun price(context: ResolvedContext, instrument: InstrumentEntity, jobId: Long): PricingResult =
        PricingResult(
            jobId = jobId,
            instrumentId = instrument.id!!,
            instrumentType = instrument.type.name,
            valuationDate = context.valuationDate,
            status = "DONE",
            totalFairValue = 0.0,
            perUnitValue = 0.0,
            components = Components.zeros(),
            keyParameters = KeyParameters(
                modelName = context.model,
                modelVersion = context.modelVersion,
            ),
            reproducibility = Reproducibility(
                inputHash = context.inputHash,
                seed = context.seed,
                modelVersion = context.modelVersion,
                computedAt = OffsetDateTime.now().toString(),
            ),
            warnings = listOf(
                Issue(code = "PLACEHOLDER", message = "엔진 미구현 - 자리표시 결과", stage = "engine"),
            ),
            errors = emptyList(),
        )
}
