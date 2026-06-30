package com.fairvalue.web

import com.fairvalue.domain.InstrumentStatus
import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.CreateInstrumentRequest
import com.fairvalue.dto.InstrumentDto
import com.fairvalue.dto.InstrumentListResponse
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.InstrumentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** openapi: POST /instruments(201), GET /instruments, GET /instruments/{id}. 조직 격리. */
@RestController
class InstrumentController(private val instrumentService: InstrumentService) {

    @PostMapping("/instruments")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @Valid @RequestBody req: CreateInstrumentRequest,
    ): InstrumentDto = instrumentService.create(caller, req)

    @GetMapping("/instruments")
    fun list(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @RequestParam(required = false) type: InstrumentType?,
        @RequestParam(required = false) status: InstrumentStatus?,
    ): InstrumentListResponse =
        InstrumentListResponse(items = instrumentService.list(caller, type, status))

    @GetMapping("/instruments/{id}")
    fun get(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
    ): InstrumentDto = instrumentService.get(caller, id)
}
