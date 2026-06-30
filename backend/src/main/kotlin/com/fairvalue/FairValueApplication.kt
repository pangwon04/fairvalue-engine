package com.fairvalue

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * FairValue Engine 백엔드 진입점 (Phase 1-A 스캐폴딩).
 *
 * 이 단계의 목표는 "골격이 실행되고 DB에 연결되며 /health 가 200 을 반환한다"까지다.
 * 실제 API 비즈니스 로직(Auth, Pricing Job 오케스트레이션 등)은 Phase 1-B 에서 추가한다.
 *
 * 컴포넌트 스캔 기준 패키지는 com.fairvalue 이다. 기존 com.fairvalue.contracts.InputHash
 * 는 Spring 빈이 아니므로 영향을 받지 않는다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class FairValueApplication

fun main(args: Array<String>) {
    runApplication<FairValueApplication>(*args)
}
