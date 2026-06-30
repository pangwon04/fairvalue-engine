package com.fairvalue.pricing

import com.fairvalue.contracts.InputHash
import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.PricingTrigger
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component

/**
 * ★ 더미 ContextResolver — resolve(커브/자산 치환)를 수행하지 않는다(placeholder).
 *   - 커브 *_ref, asset_id 를 그대로 통과시킨다(포인트 배열·spot/vol 보강 없음).
 *   - instrument_type / model / seed / model_version / options 만 주입한다.
 *   - input_hash 는 더미가 아니라 ★진짜: 기존 InputHash 로직으로 산출한다.
 *
 * Phase 4-α 에서 RealContextResolver(@Primary)로 대체됨. 이 더미는 빈으로 남겨 두되
 * 주입은 Real 이 우선한다(테스트/대체용). 호출부 불변.
 */
@Component
class DummyContextResolver(private val mapper: ObjectMapper) : ContextResolver {

    override fun resolve(rawForm: JsonNode, trigger: PricingTrigger, type: InstrumentType, orgId: Long): ResolvedContext {
        val ctx: ObjectNode = if (rawForm is ObjectNode) rawForm.deepCopy() else mapper.createObjectNode()

        // 식별·모형 주입(사용자 입력보다 trigger·경로 우선).
        ctx.put("instrument_type", type.name)
        val model = trigger.model ?: ctx.path("model").asText(null) ?: "BSM"
        val seed = trigger.seed ?: (if (ctx.path("seed").isNumber) ctx.path("seed").asLong() else 20240101L)
        val modelVersion = "${type.schemaKey()}-1.0.0"
        ctx.put("model", model)
        ctx.put("seed", seed)
        ctx.put("model_version", modelVersion)
        trigger.options?.let { ctx.set<JsonNode>("options", it) }

        // ★ resolve 미수행: curves.*_ref·market.asset_id 는 그대로 둔다(Phase 3 에서 치환).

        // 진짜 input_hash (재현성·캐시 키).
        val inputHash = InputHash.ofJson(mapper.writeValueAsString(ctx))

        val valuationDate = ctx.path("valuation_date").asText(null)
        return ResolvedContext(
            type = type, valuationDate = valuationDate, model = model, seed = seed,
            modelVersion = modelVersion, inputHash = inputHash, contextJson = ctx,
        )
    }
}
