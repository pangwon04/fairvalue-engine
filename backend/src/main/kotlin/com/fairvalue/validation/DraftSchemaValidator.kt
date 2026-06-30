package com.fairvalue.validation

import com.fairvalue.dto.ValidationIssue
import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * (a) 구조 검증 — rawForm 이 shared 의 valuation-context.draft.schema.json(draft 2020-12)에
 * 부합하는지(필수 필드·타입·enum). 위반은 severity=error.
 *
 * 검증 기준 스키마는 build 시 shared/schemas 에서 classpath:/contracts/ 로 복사된 동일 파일이다
 * (build.gradle.kts processResources). 임의 규칙을 더하지 않는다.
 */
@Component
class DraftSchemaValidator {

    private val schema: JsonSchema = JsonSchemaFactory
        .getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(ClassPathResource("contracts/valuation-context.draft.schema.json").inputStream)

    /** merged = rawForm + {instrument_type}(경로의 instrument 에서 주입). */
    fun validate(merged: JsonNode): List<ValidationIssue> =
        schema.validate(merged).map { msg ->
            ValidationIssue(
                field = msg.instanceLocation?.toString()?.ifBlank { null },
                rule = "schema:${msg.type}",
                severity = "error",
                message = msg.message,
            )
        }
}
