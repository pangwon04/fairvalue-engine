package com.fairvalue.web

import com.fairvalue.dto.TermsDraftResponse
import com.fairvalue.dto.TermsSaveResponse
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.TermsService
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * openapi: PUT /instruments/{id}/terms — rawForm draft 저장 + 검증.
 *          (보조) GET /instruments/{id}/terms — 저장된 draft 조회.
 * 본문은 rawForm 자유 구조이므로 JsonNode 로 받는다(검증기가 구조·룰 검사).
 */
@RestController
class TermsController(private val termsService: TermsService) {

    @PutMapping("/instruments/{id}/terms")
    fun saveTerms(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
        @RequestBody rawForm: JsonNode,
    ): TermsSaveResponse = termsService.saveTerms(caller, id, rawForm)

    @GetMapping("/instruments/{id}/terms")
    fun getTerms(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
    ): TermsDraftResponse = termsService.getTerms(caller, id)
}
