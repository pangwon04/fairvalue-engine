# fairvalue-contracts (최소 Kotlin 모듈)

FairValue 3대 계약 v0.1 의 `InputHash.kt` 를 컴파일·테스트하기 위한 **독립 Kotlin/Gradle 모듈**입니다.
Spring Boot 앱이 아니며, Python(`reproducer.py`)과의 input_hash 교차 일치를 보장하는 것이 목적입니다.

## 구성

```
backend/
├─ settings.gradle.kts                         # rootProject = fairvalue-contracts
├─ build.gradle.kts                            # kotlin("jvm") + Jackson + JUnit5
├─ gradlew / gradlew.bat                       # wrapper 스크립트
├─ gradle/wrapper/gradle-wrapper.properties    # gradle 8.7
└─ src/
   ├─ main/kotlin/com/fairvalue/contracts/InputHash.kt    # §2.5 정규화 8단계
   └─ test/kotlin/com/fairvalue/contracts/InputHashTest.kt # Python expected_hash 교차검증
```

## 실행 (로컬 한 줄)

```bash
cd backend && ./gradlew test
```

> **gradle-wrapper.jar 가 없으면** `./gradlew` 가 동작하지 않습니다(이 저장소는 jar 미포함).
> 최초 1회 아래로 wrapper jar 를 생성한 뒤 커밋하세요(로컬에 gradle 필요):
>
> ```bash
> cd backend && gradle wrapper --gradle-version 8.7 && ./gradlew test
> ```
>
> CI(`.github/workflows/contracts-ci.yml`)는 jar 부재 시 자동으로 `gradle wrapper` 를 실행합니다.

## 검증 내용

`InputHashTest` 는 `../shared/schemas/hash-test-vectors.json` 의 TV1~TV5 를 읽어
`InputHash.ofJson(input)` 결과가 Python 정본(`reproducer.py`)이 동결한 `expected_hash`·`canonical_blob`
과 일치하는지 확인합니다. 즉 **Backend(Kotlin) ↔ Engine(Python) 해시 동일성**을 게이트합니다.
