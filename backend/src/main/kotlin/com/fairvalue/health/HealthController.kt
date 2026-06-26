package com.fairvalue.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 헬스체크 엔드포인트 (Phase 1-A).
 *
 * GET /health → 200 { "status": "UP", "version": "<build version>" }
 *
 * 이 컨트롤러는 DB 의존 없이 항상 응답한다(앱 기동 자체의 생존 신호).
 * DB·외부 의존 상태까지 포함한 상세 헬스는 actuator(후속)로 다룬다.
 */
@RestController
class HealthController(
    @Value("\${spring.application.version:0.1.0}") private val version: String,
) {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf(
        "status" to "UP",
        "version" to version,
    )
}
