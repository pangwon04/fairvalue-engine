package com.fairvalue.validation

import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.ValidationIssue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * (b) 룰 검증 — 상품별 폼 스키마(classpath:/contracts/productSchemas/{type}.json)를 읽어
 *   1) required(showWhen/enableWhen 게이팅) 누락 → error rule="required"
 *   2) field.validations[].rule(= validation-rules.ts 표준 20종)을 rawForm bind 경로에 적용
 * 한다. 규칙 이름은 폼 스키마에서 그대로 가져오므로 임의 규칙이 추가되지 않는다.
 *
 * rawForm 은 bind 경로(terms.*, rights.*, market.*, curves.*_ref ...) 기준이다.
 */
@Component
class FormRuleValidator(private val mapper: ObjectMapper) {

    private val schemaCache = HashMap<InstrumentType, JsonNode>()

    private fun formSchema(type: InstrumentType): JsonNode = schemaCache.getOrPut(type) {
        ClassPathResource("contracts/productSchemas/${type.schemaKey()}.json").inputStream
            .use { mapper.readTree(it) }
    }

    fun validate(type: InstrumentType, rawForm: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val form = formSchema(type)

        // field.key -> 제출값(bind 경로로 rawForm 에서 추출). showWhen 평가에 사용.
        val valueByKey = HashMap<String, JsonNode?>()
        forEachField(form) { f ->
            val bind = f.path("bind").asTextOrNull()
            if (bind != null) valueByKey[f.path("key").asText()] = rawForm.at(bind)
        }

        forEachField(form) { f ->
            val key = f.path("key").asText()
            val type0 = f.path("type").asText()
            val bind = f.path("bind").asTextOrNull()
            val active = isActive(f, valueByKey)
            if (!active) return@forEachField

            val value = if (bind != null) rawForm.at(bind) else null

            // 1) required
            if (f.path("required").asBoolean(false) && isEmpty(value)) {
                issues += ValidationIssue(bind ?: key, "required", "error",
                    "${f.path("label").asText(key)}은(는) 필수입니다.")
            }

            // 2) validations[]
            for (v in f.path("validations").orEmpty()) {
                val rule = v.path("rule").asText()
                val severity = v.path("severity").asText("error")
                val msg = v.path("message").asText("검증 실패: $rule")
                val ok = evalRule(rule, v.path("params"), value, key, bind, valueByKey, type0)
                if (!ok) issues += ValidationIssue(bind ?: key, rule, severity, msg)
            }
        }
        return issues
    }

    /** showWhen(노출)·enableWhen(활성) 둘 다 충족해야 active. 조건 없으면 active. */
    private fun isActive(field: JsonNode, vals: Map<String, JsonNode?>): Boolean {
        val show = field.path("showWhen")
        val enable = field.path("enableWhen")
        if (!show.isMissingNode && !evalCondition(show, vals)) return false
        if (!enable.isMissingNode && !evalCondition(enable, vals)) return false
        return true
    }

    /**
     * 룰 평가. value 가 비어있으면(required 가 따로 잡으므로) 대부분 통과 처리한다.
     * 구현 대상: validation-rules.ts 표준 규칙. 미구현 규칙은 통과(오탐 0).
     */
    private fun evalRule(
        rule: String, params: JsonNode, value: JsonNode?, key: String, bind: String?,
        vals: Map<String, JsonNode?>, fieldType: String,
    ): Boolean {
        if (isEmpty(value) && rule != "assetRequired" && rule != "curveRequired" &&
            rule != "showWhenRequired"
        ) return true
        return when (rule) {
            "positive", "pricePositive", "volatilityPositive" -> num(value) != null && num(value)!! > 0
            "min" -> num(value) == null || num(value)!! >= params.path("min").asDouble(Double.NEGATIVE_INFINITY)
            "max" -> num(value) == null || num(value)!! <= params.path("max").asDouble(Double.POSITIVE_INFINITY)
            "percentageRange" -> {
                val n = num(value) ?: return true
                n >= params.path("min").asDouble(0.0) && n <= params.path("max").asDouble(100.0)
            }
            "maturityAfterIssueDate" -> {
                val issue = date(vals[params.path("issueField").asText("issue_date")])
                val mat = date(value)
                issue == null || mat == null || mat.isAfter(issue)
            }
            "refixingFloorCheck" -> {
                val floor = num(value) ?: return true
                val init = num(vals[params.path("initField").asText("conv_price")])
                floor >= 0.0 && (init == null || floor <= init)
            }
            "exercisePeriodWithinMaturity" -> {
                val d = date(value) ?: return true
                val issue = date(vals["issue_date"]); val mat = date(vals["maturity_date"])
                (issue == null || !d.isBefore(issue)) && (mat == null || !d.isAfter(mat))
            }
            "assetRequired", "curveRequired", "showWhenRequired" -> !isEmpty(value)
            "dilutionRange" -> { val n = num(value) ?: return true; n in 0.0..1.0 }
            // dateOrder/dateWithin/dependencyRequired/mutuallyExclusive/modelCompatibility/enum 등은
            // 이번 단계에서 미구현(오탐 방지로 통과). 구조 검증/후속 단계에서 보강.
            else -> true
        }
    }

    // --- 조건식 평가 (form-schema.ts Condition) ---
    private fun evalCondition(cond: JsonNode, vals: Map<String, JsonNode?>): Boolean {
        cond.path("all").let { if (it.isArray) return it.all { c -> evalCondition(c, vals) } }
        cond.path("any").let { if (it.isArray) return it.any { c -> evalCondition(c, vals) } }
        cond.path("not").let { if (!it.isMissingNode) return !evalCondition(it, vals) }
        val field = cond.path("field").asTextOrNull() ?: return true
        val v = vals[field]
        val target = cond.path("value")
        return when (cond.path("op").asText()) {
            "truthy" -> truthy(v)
            "falsy" -> !truthy(v)
            "eq" -> jsonEq(v, target)
            "neq" -> !jsonEq(v, target)
            "in" -> target.isArray && target.any { jsonEq(v, it) }
            "nin" -> !(target.isArray && target.any { jsonEq(v, it) })
            "gt" -> (num(v) ?: return false) > target.asDouble()
            "gte" -> (num(v) ?: return false) >= target.asDouble()
            "lt" -> (num(v) ?: return false) < target.asDouble()
            "lte" -> (num(v) ?: return false) <= target.asDouble()
            else -> true
        }
    }

    // --- helpers ---
    private fun forEachField(form: JsonNode, action: (JsonNode) -> Unit) {
        for (step in form.path("steps").orEmpty()) {
            for (f in step.path("fields").orEmpty()) action(f)
        }
    }

    private fun isEmpty(v: JsonNode?): Boolean =
        v == null || v.isNull || (v.isTextual && v.asText().isBlank())

    private fun truthy(v: JsonNode?): Boolean = when {
        v == null || v.isNull -> false
        v.isBoolean -> v.asBoolean()
        v.isNumber -> v.asDouble() != 0.0
        v.isTextual -> v.asText().isNotBlank()
        else -> true
    }

    private fun num(v: JsonNode?): Double? =
        if (v != null && v.isNumber) v.asDouble()
        else if (v != null && v.isTextual) v.asText().toDoubleOrNull() else null

    private fun date(v: JsonNode?): java.time.LocalDate? =
        if (v != null && v.isTextual) runCatching { java.time.LocalDate.parse(v.asText()) }.getOrNull() else null

    private fun jsonEq(v: JsonNode?, target: JsonNode): Boolean = when {
        v == null || v.isNull -> target.isNull
        target.isNumber && v.isNumber -> v.asDouble() == target.asDouble()
        else -> v.asText() == target.asText()
    }

    private fun JsonNode.asTextOrNull(): String? =
        if (isMissingNode || isNull) null else asText().ifBlank { null }

    private fun JsonNode?.orEmpty(): Iterable<JsonNode> =
        if (this != null && isArray) this else emptyList()

    /** dot 경로로 값 추출(rights.conversion.strike 등). 없으면 null. */
    private fun JsonNode.at(path: String): JsonNode? {
        var cur: JsonNode = this
        for (seg in path.split('.')) {
            cur = cur.path(seg)
            if (cur.isMissingNode) return null
        }
        return if (cur.isNull) null else cur
    }
}
