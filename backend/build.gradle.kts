// FairValue 3대 계약 — 최소 Kotlin 모듈 (W7.5)
// 목적: InputHash.kt 컴파일 + Python 교차해시 JUnit 테스트.
// Spring Boot 앱이 아니다. 독립 모듈이므로 Jackson 버전을 명시한다.

plugins {
    kotlin("jvm") version "1.9.24"
}

group = "com.fairvalue"
version = "0.1.0"

repositories {
    mavenCentral()
}

val jacksonVersion = "2.17.1"

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
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
