package com.fairvalue.service

import com.fairvalue.domain.InstrumentTermsEntity
import com.fairvalue.dto.TermsDraftResponse
import com.fairvalue.dto.TermsSaveResponse
import com.fairvalue.error.NotFoundException
import com.fairvalue.repository.InstrumentTermsRepository
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.validation.RawFormValidator
import com.fairvalue.validation.nodeAtPath
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * rawForm draft 저장·검증.
 *   - 검증: RawFormValidator((a)구조 + (b)룰 + parity 가드).
 *   - 저장: 항상 instrument_terms 에 upsert(saved=true). error 유무는 has_errors.
 *   - instrument.status 는 DRAFT 유지(평가 전).
 *   - ★ resolve 미수행(커브 *_ref 그대로 저장). parity 키는 저장 시 제거(무시).
 *   - 조직 격리: instrument 가 같은 조직일 때만(없으면 404).
 */
@Service
class TermsService(
    private val instrumentService: InstrumentService,
    private val termsRepo: InstrumentTermsRepository,
    private val validator: RawFormValidator,
    private val mapper: ObjectMapper,
) {

    @Transactional
    fun saveTerms(caller: AuthPrincipal, instrumentId: Long, rawForm: JsonNode): TermsSaveResponse {
        WriteAccess.require(caller)
        val instrument = instrumentService.requireInstrument(caller, instrumentId)

        val result = validator.validate(instrument.type, rawForm)

        // parity 제거(무시) 후 저장.
        val toStore = stripParity(rawForm.deepCopy())
        val issue = parseDate(nodeAtPath(rawForm, "terms.issue_date"))
        val maturity = parseDate(nodeAtPath(rawForm, "terms.maturity_date"))
        // 만기<=발행이면 컬럼은 비워 둔다(DB CHECK 위반 회피). 값은 terms_json 에 그대로 남는다.
        val datesValid = issue == null || maturity == null || maturity.isAfter(issue)

        val existing = termsRepo.findByInstrumentIdAndOrgId(instrumentId, caller.orgId)
        if (existing != null) {
            existing.termsJson = mapper.writeValueAsString(toStore)
            existing.valuationDate = parseDate(nodeAtPath(rawForm, "valuation_date"))
            existing.issueDate = if (datesValid) issue else null
            existing.maturityDate = if (datesValid) maturity else null
            termsRepo.save(existing)
        } else {
            termsRepo.save(
                InstrumentTermsEntity(
                    instrumentId = instrumentId,
                    orgId = caller.orgId,
                    termsJson = mapper.writeValueAsString(toStore),
                    valuationDate = parseDate(nodeAtPath(rawForm, "valuation_date")),
                    issueDate = if (datesValid) issue else null,
                    maturityDate = if (datesValid) maturity else null,
                ),
            )
        }

        return TermsSaveResponse(saved = true, hasErrors = result.hasErrors, validation = result.issues)
    }

    @Transactional(readOnly = true)
    fun getTerms(caller: AuthPrincipal, instrumentId: Long): TermsDraftResponse {
        val instrument = instrumentService.requireInstrument(caller, instrumentId)
        val terms = termsRepo.findByInstrumentIdAndOrgId(instrumentId, caller.orgId)
            ?: throw NotFoundException("저장된 계약조건이 없습니다.")
        val rawForm = mapper.readTree(terms.termsJson)
        val result = validator.validate(instrument.type, rawForm)
        return TermsDraftResponse(instrumentId = instrumentId, rawForm = rawForm, validation = result.issues)
    }

    private fun stripParity(node: JsonNode): JsonNode {
        if (node is ObjectNode) {
            node.remove("parity")
            node.fields().forEach { stripParity(it.value) }
        } else if (node.isArray) {
            node.forEach { stripParity(it) }
        }
        return node
    }

    private fun parseDate(v: JsonNode?): LocalDate? =
        if (v != null && v.isTextual) runCatching { LocalDate.parse(v.asText()) }.getOrNull() else null
}
