package com.fairvalue.validation

import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.ValidationIssue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component

/**
 * rawForm 검증 오케스트레이터.
 *   (a) 구조 검증(draft 스키마) + (b) 룰 검증(폼 스키마/validation-rules) + parity 가드.
 *
 * 반환: 모든 ValidationIssue. error 가 1개라도 있으면 hasErrors=true(저장은 항상 수행).
 * ★ resolve(커브 ref→포인트, asset→spot/vol)는 수행하지 않는다(1-B-3 평가 단계).
 */
@Component
class RawFormValidator(
    private val draftValidator: DraftSchemaValidator,
    private val ruleValidator: FormRuleValidator,
) {

    data class Result(val issues: List<ValidationIssue>) {
        val hasErrors: Boolean = issues.any { it.severity == "error" }
    }

    /**
     * @param type 경로의 instrument 유형(서버 신뢰 입력). rawForm 에는 instrument_type 을 주입해 검증.
     * @param rawForm 사용자가 제출한 draft(JsonNode, 보통 ObjectNode).
     */
    fun validate(type: InstrumentType, rawForm: JsonNode): Result {
        val issues = mutableListOf<ValidationIssue>()

        // instrument_type 은 경로에서 주입(사용자 입력 신뢰 안 함) 후 구조 검증.
        val merged = rawForm.deepCopy<JsonNode>()
        if (merged is ObjectNode) merged.put("instrument_type", type.name)

        issues += draftValidator.validate(merged)
        issues += ruleValidator.validate(type, rawForm)
        issues += parityGuard(rawForm)
        return Result(issues)
    }

    /** parity 는 결과 전용(패치 2.1). 입력에 parity 키가 있으면 warning(저장 시 무시 권장). */
    private fun parityGuard(rawForm: JsonNode): List<ValidationIssue> {
        val found = mutableListOf<ValidationIssue>()
        fun scan(node: JsonNode, path: String) {
            if (node.isObject) {
                node.fields().forEach { (k, v) ->
                    val p = if (path.isEmpty()) k else "$path.$k"
                    if (k == "parity") {
                        // 패치 2.1: parity 는 결과 전용 파생값(validation-rules 규칙 아님).
                        found += ValidationIssue(p, "parity", "warning",
                            "parity 는 결과 전용 파생값입니다. 입력에서 무시됩니다.")
                    }
                    scan(v, p)
                }
            } else if (node.isArray) {
                node.forEachIndexed { i, v -> scan(v, "$path[$i]") }
            }
        }
        scan(rawForm, "")
        return found
    }
}
