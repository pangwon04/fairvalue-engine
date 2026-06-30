// FairValue Backend — Spring Boot 애플리케이션 (Phase 1-A 스캐폴딩)
// 목적: 골격 실행 + DB 연결 + /health 200. API 비즈니스 로직은 Phase 1-B.
// 주의: W7.5 의 InputHash.kt + InputHashTest 는 그대로 유지·통과해야 한다.
//       Jackson 은 Spring Boot BOM 이 관리하므로 명시 버전을 제거하고 BOM 에 맞춘다.

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    kotlin("plugin.jpa") version "1.9.24"
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.fairvalue"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // --- Spring Boot 스타터 ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // --- JWT (jjwt 0.12.x) ---
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // --- Jackson (Kotlin 모듈) — 버전은 Spring Boot BOM 이 관리 ---
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // --- DB / 마이그레이션 ---
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- OpenAPI (의존성만; openapi.yaml 노출/대조는 다음 단계) ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // --- 테스트 (기존 InputHashTest 유지) ---
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql:1.20.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // JUnit4(vintage) 제외 — 프로젝트는 JUnit5 만 사용
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

kotlin {
    jvmToolchain(21)
}
