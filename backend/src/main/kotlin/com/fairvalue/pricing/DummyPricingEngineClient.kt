package com.fairvalue.pricing

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.dto.PricingResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * ★ 더미 PricingEngineClient — 실제 계산 없이 placeholder 결과 생성. app.engine.mode=dummy(테스트).
 *   - 5-8fix: raw ObjectNode 로 구성 + ★옵션 키(trees·curve_bootstrap·sensitivity) 샘플 포함
 *     → passthrough 통합 테스트가 원문 보존을 검증할 수 있고, 보고서 발급 SUCCESS 경로도 테스트 가능.
 *   - 12키는 전부 0(Σ=total=0 불변식 유지 — 기존 테스트 불변). input_hash 는 context 값 echo.
 */
@Component
@ConditionalOnProperty(prefix = "app.engine", name = ["mode"], havingValue = "dummy")
class DummyPricingEngineClient(
    private val mapper: ObjectMapper,
) : PricingEngineClient {

    override fun price(context: ResolvedContext, instrument: InstrumentEntity, jobId: Long): PricingOutcome {
        val raw: ObjectNode = mapper.createObjectNode()
        raw.put("job_id", jobId)
        raw.put("instrument_id", instrument.id!!)
        raw.put("instrument_type", instrument.type.name)
        raw.put("valuation_date", context.valuationDate)
        raw.put("status", "DONE")
        raw.put("total_fair_value", 0.0)
        raw.put("per_unit_value", 0.0)

        val comp = raw.putObject("components")
        for (k in COMP_KEYS) comp.put(k, 0.0)

        val kp = raw.putObject("key_parameters")
        kp.put("model_name", context.model)
        kp.put("model_version", context.modelVersion)
        kp.put("u", 1.25); kp.put("d", 0.8); kp.put("lattice_steps", 100)

        val repro = raw.putObject("reproducibility")
        repro.put("input_hash", context.inputHash)   // ★echo: resolver 주입값 그대로
        repro.put("seed", context.seed)
        repro.put("model_version", context.modelVersion)
        repro.put("computed_at", OffsetDateTime.now().toString())

        raw.putArray("warnings").add(
            mapper.createObjectNode().put("code", "PLACEHOLDER").put("message", "엔진 미구현 - 자리표시 결과").put("stage", "engine"),
        )
        raw.putArray("errors")

        // ★옵션 키 샘플(원문 보존·보고서 발급 검증용). 3스텝 소형 트리.
        addSampleExtensions(raw)

        val parsed = mapper.treeToValue(raw, PricingResult::class.java)   // 12키 검증(기존 동작)
        return PricingOutcome(raw = raw, result = parsed)
    }

    private fun addSampleExtensions(raw: ObjectNode) {
        val trees = raw.putObject("trees")
        fun mat(name: String, rows: List<List<Double?>>) {
            val arr = trees.putArray(name)
            rows.forEach { row -> val r = arr.addArray(); row.forEach { if (it == null) r.addNull() else r.add(it) } }
        }
        mat("underlying_tree", listOf(listOf(3260.0, null, null), listOf(2600.0, 4080.0, null), listOf(2080.0, 3260.0, 5110.0)))
        mat("equity_tree", listOf(listOf(0.0, null, null), listOf(0.0, 0.0, null), listOf(0.0, 0.0, 0.0)))
        mat("debt_tree", listOf(listOf(0.0, null, null), listOf(0.0, 0.0, null), listOf(0.0, 0.0, 0.0)))
        mat("composite_tree", listOf(listOf(0.0, null, null), listOf(0.0, 0.0, null), listOf(0.0, 0.0, 0.0)))
        val prob = trees.putArray("risk_neutral_prob")
        for (st in 0..1) prob.add(mapper.createObjectNode().put("step", st).put("p", 0.46).put("q", 0.54))
        trees.putObject("tree_meta").put("steps_used", 2).put("dt", 0.25).put("u", 1.25).put("d", 0.8)
            .put("display_nodes", 11).put("rate_mode", "BOOTSTRAPPED_FORWARD").put("model", "TF")

        val cb = raw.putObject("curve_bootstrap")
        cb.put("rate_mode", "BOOTSTRAPPED_FORWARD")
        fun leg(name: String) {
            val t = cb.putObject(name)
            for (row in listOf("ytm", "spot", "forward")) {
                val a = t.putArray(row); listOf(0.5 to 3.4, 1.0 to 3.3).forEach { (x, y) -> a.addArray().add(x).add(y) }
            }
            t.put("grid", 0.5).put("assumption", "연속 zero(dummy)")
        }
        leg("rf"); leg("rd")

        val sens = raw.putObject("sensitivity")
        sens.putArray("vol_axis").add(0.40).add(0.45).add(0.50)
        sens.putArray("spot_axis").add(3097.0).add(3260.0).add(3423.0)
        val g = sens.putArray("total_grid")
        listOf(listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0)).forEach { row -> val r = g.addArray(); row.forEach { r.add(it) } }
        val pu = sens.putArray("per_unit_grid")
        listOf(listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0)).forEach { row -> val r = pu.addArray(); row.forEach { r.add(it) } }
        sens.putObject("meta").put("steps_used", 2).put("model", "TF_LATTICE").put("vol_bump", 0.05).put("spot_bump", 0.05).put("vol_floor_applied", false)
    }

    companion object {
        val COMP_KEYS = listOf(
            "bond_value", "preferred_share_value", "conversion_option_value", "exchange_option_value",
            "warrant_value", "redemption_option_value", "issuer_call_value", "sale_claim_value",
            "stock_option_value", "conditional_option_value", "dilution_effect", "total_fair_value",
        )
    }
}
