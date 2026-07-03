package com.fairvalue.web

import com.fairvalue.dto.VolatilityComputeResponse
import com.fairvalue.dto.VolatilityDetailDto
import com.fairvalue.dto.VolatilityListResponse
import com.fairvalue.dto.VolatilityRegisterRequest
import com.fairvalue.dto.VolatilityRegisterResult
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.VolatilityService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

/**
 * 변동성 파라미터 API (E3b).
 *   POST /volatilities/compute (multipart, file N + trading_days?) : 미리보기(저장 안 함).
 *   POST /volatilities         (JSON)                              : 등록(DIRECT | PEER_CSV).
 *   GET  /volatilities         (as_of?, label?)                    : 목록.
 *   GET  /volatilities/{id}                                        : 단건(detail_json 포함).
 * 등록/산출 권한은 서비스에서 강제(CURVE_MANAGER/ORG_ADMIN), 조회 인증 전원. org_id 격리.
 */
@RestController
class VolatilityController(
    private val volatilityService: VolatilityService,
) {

    // --- 산출(미리보기, 저장 안 함) ---
    @PostMapping("/volatilities/compute", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun compute(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @RequestParam("files") files: List<MultipartFile>,
        @RequestParam("trading_days", required = false) tradingDays: Int?,
    ): VolatilityComputeResponse {
        val named = files.map { (it.originalFilename ?: "peer.csv") to it.bytes }
        return volatilityService.compute(caller, named, tradingDays)
    }

    // --- 등록(JSON) ---
    @PostMapping("/volatilities", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @RequestBody req: VolatilityRegisterRequest,
    ): VolatilityRegisterResult = volatilityService.register(caller, req)

    // --- 목록 ---
    @GetMapping("/volatilities")
    fun list(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @RequestParam(name = "as_of", required = false) asOf: LocalDate?,
        @RequestParam(required = false) label: String?,
    ): VolatilityListResponse = VolatilityListResponse(items = volatilityService.list(caller, asOf, label))

    // --- 단건(detail 포함) ---
    @GetMapping("/volatilities/{id}")
    fun get(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
    ): VolatilityDetailDto = volatilityService.getDetail(caller, id)
}
