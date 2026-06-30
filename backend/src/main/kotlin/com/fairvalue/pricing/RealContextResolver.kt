package com.fairvalue.pricing

import com.fairvalue.contracts.InputHash
import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.InstrumentType
import com.fairvalue.domain.YieldCurveUpload
import com.fairvalue.dto.PricingTrigger
import com.fairvalue.error.NotFoundException
import com.fairvalue.repository.YieldCurvePointRepository
import com.fairvalue.repository.YieldCurveUploadRepository
import com.fairvalue.service.CurveMappingService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * ★ 실제 ContextResolver (Phase 4-α) — DummyContextResolver 를 대체(@Primary).
 *
 * rawForm draft 의 curves.risk_free_ref / credit_ref 를 Phase 2 저장 커브로 resolve 해서
 * ValuationContext 의 Curves 스냅샷(명세 §10)을 채운다:
 *   curves.risk_free_curve / credit_curve  = [[tenor_years, rate_percent], ...]
 *   curves.curve_source / curve_version / interpolation_method
 *
 * 조회 규칙:
 *   - *_ref 가 upload_id(숫자)면 그 커브를 직접 로드(org 스코프, findByIdAndOrgId).
 *   - *_ref 가 없으면 우선순위 selector(findByPriority: MANUAL>UPLOAD>BOOTSTRAP, 최신 version)로
 *     valuation_date·kind·grade 매칭 커브 자동 선택.
 *   - org_id 격리: 타 조직 커브는 조회되지 않아 매칭 실패(404).
 *
 * input_hash 는 채워진 커브 스냅샷을 포함한다(InputHash 정규화가 *_curve·curve_version 을 해시).
 * → 같은 ref·같은 version = 같은 hash, version 변경 = hash 변경(정규화 로직 불변).
 */
@Component
@Primary
class RealContextResolver(
    private val mapper: ObjectMapper,
    private val uploadRepo: YieldCurveUploadRepository,
    private val pointRepo: YieldCurvePointRepository,
    private val mappingService: CurveMappingService,
) : ContextResolver {

    override fun resolve(rawForm: JsonNode, trigger: PricingTrigger, type: InstrumentType, orgId: Long): ResolvedContext {
        val ctx: ObjectNode = if (rawForm is ObjectNode) rawForm.deepCopy() else mapper.createObjectNode()

        // 식별·모형 주입(Dummy 와 동일).
        ctx.put("instrument_type", type.name)
        val model = trigger.model ?: ctx.path("model").asText(null) ?: "BSM"
        val seed = trigger.seed ?: (if (ctx.path("seed").isNumber) ctx.path("seed").asLong() else 20240101L)
        val modelVersion = "${type.schemaKey()}-1.0.0"
        ctx.put("model", model)
        ctx.put("seed", seed)
        ctx.put("model_version", modelVersion)
        trigger.options?.let { ctx.set<JsonNode>("options", it) }

        // ★ 커브 resolve: *_ref → 포인트 스냅샷.
        val valuationDateStr = ctx.path("valuation_date").asText(null)
        val asOf = valuationDateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val curves: ObjectNode = (ctx.get("curves") as? ObjectNode) ?: mapper.createObjectNode().also { ctx.set<JsonNode>("curves", it) }

        val rf = resolveOne(curves, "risk_free_ref", orgId, asOf, CurveKind.RISK_FREE, gradeOf(curves))
            ?: throw NotFoundException("무위험 커브를 resolve 할 수 없습니다(ref/조건 미매칭).")
        applySnapshot(curves, "risk_free_curve", rf)
        // 무위험 커브의 source/version/interpolation 을 대표로 기록(§10).
        curves.put("curve_source", rf.source ?: "uploaded")
        curves.put("curve_version", "v${rf.version}")
        curves.put("interpolation_method", rf.interpolation)

        val cr = resolveOne(curves, "credit_ref", orgId, asOf, CurveKind.CREDIT, gradeOf(curves))
        if (cr != null) {
            applySnapshot(curves, "credit_curve", cr)
        } else {
            curves.set<JsonNode>("credit_curve", mapper.nullNode())
        }

        // 최종 컨텍스트에서는 *_ref 제거(final ValuationContext 규약). 해시는 *_curve 만 대상.
        curves.remove("risk_free_ref")
        curves.remove("credit_ref")

        val inputHash = InputHash.ofJson(mapper.writeValueAsString(ctx))
        return ResolvedContext(
            type = type, valuationDate = valuationDateStr, model = model, seed = seed,
            modelVersion = modelVersion, inputHash = inputHash, contextJson = ctx,
        )
    }

    private data class ResolvedCurve(
        val points: List<Pair<java.math.BigDecimal, java.math.BigDecimal>>,
        val source: String?, val version: Int, val interpolation: String,
    )

    private fun gradeOf(curves: ObjectNode): String? =
        curves.path("grade").asText(null)?.takeIf { it.isNotBlank() }

    /** ref(upload_id) 직접 로드, 없으면 우선순위 자동매핑. org 스코프. */
    private fun resolveOne(
        curves: ObjectNode, refKey: String, orgId: Long, asOf: LocalDate?, kind: CurveKind, grade: String?,
    ): ResolvedCurve? {
        val refNode = curves.path(refKey)
        val upload: YieldCurveUpload? = when {
            refNode.isNumber -> uploadRepo.findByIdAndOrgId(refNode.asLong(), orgId)
            refNode.isTextual && refNode.asText().toLongOrNull() != null ->
                uploadRepo.findByIdAndOrgId(refNode.asText().toLong(), orgId)
            asOf != null -> mappingService.findByPriority(orgId, asOf, kind, grade)
            else -> null
        }
        upload ?: return null
        val pts = pointRepo.findByUploadIdOrderBySeqAsc(upload.id!!).map { it.tenorYears to it.ratePercent }
        if (pts.isEmpty()) return null
        return ResolvedCurve(pts, upload.source, upload.version, upload.interpolationMethod)
    }

    private fun applySnapshot(curves: ObjectNode, key: String, rc: ResolvedCurve) {
        val arr = mapper.createArrayNode()
        for ((t, r) in rc.points) {
            val pair = mapper.createArrayNode()
            pair.add(t.toDouble())
            pair.add(r.toDouble())
            arr.add(pair)
        }
        curves.set<JsonNode>(key, arr)
    }
}
