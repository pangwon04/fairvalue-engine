package com.fairvalue.web

import com.fairvalue.dto.DashboardSummaryDto
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.DashboardService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ★5-10 대시보드 API. 로그인 후 첫 화면의 순수 현황 요약.
 *   GET /dashboard/summary : org 격리 집계(상품·평가·보고서·파라미터·유형별·최근평가).
 *   계산·설정 기능 없음(읽기 전용).
 */
@RestController
class DashboardController(private val dashboardService: DashboardService) {

    @GetMapping("/dashboard/summary")
    fun summary(
        @AuthenticationPrincipal caller: AuthPrincipal,
    ): DashboardSummaryDto = dashboardService.summary(caller)
}
