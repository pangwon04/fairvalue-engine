package com.fairvalue.web

import com.fairvalue.dto.JobDto
import com.fairvalue.dto.PriceJobResponse
import com.fairvalue.dto.PricingTrigger
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.JobService
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * openapi: POST /instruments/{id}/price(trigger), GET /jobs/{job_id}, GET /jobs/{job_id}/result.
 * 실행은 VALUATOR+(서비스에서 강제), 조회는 인증된 전원. 모두 org_id 격리.
 */
@RestController
class PricingController(private val jobService: JobService) {

    @PostMapping("/instruments/{id}/price")
    @ResponseStatus(HttpStatus.CREATED)
    fun price(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
        @RequestBody(required = false) trigger: PricingTrigger?,
    ): PriceJobResponse = jobService.price(caller, id, trigger ?: PricingTrigger())

    @GetMapping("/jobs/{jobId}")
    fun getJob(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable jobId: Long,
    ): JobDto = jobService.getJob(caller, jobId)

    @GetMapping("/jobs/{jobId}/result")
    fun getResult(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable jobId: Long,
    ): JsonNode = jobService.getResult(caller, jobId)
}
