# FairValue Backend (Spring Boot)

FairValue Engine 의 백엔드 모듈입니다. 현재 단계는 **Phase 1-A 스캐폴딩**으로,
"골격이 실행되고 DB 에 연결되며 `/health` 가 200 을 반환"하는 데까지를 목표로 합니다.

> **API 비즈니스 로직(Auth, Pricing Job 오케스트레이션, 엔진 호출·결과 저장 등)은 Phase 1-B 부터입니다.**
> 이번 단계에는 컨트롤러가 `/health` 하나뿐이며, REST 계약은 `src/main/resources/openapi.yaml`(W9)에 정의돼 있습니다.

W7.5 의 `InputHash.kt` + `InputHashTest`(Backend↔Engine 해시 교차검증)는 그대로 유지·통과합니다.

## 구성

```
backend/
├─ settings.gradle.kts                          # rootProject = fairvalue-contracts
├─ build.gradle.kts                             # Spring Boot 3.3 + Kotlin + JPA/Flyway/PostgreSQL
├─ docker-compose.yml                           # 로컬 PostgreSQL 16
├─ .env.example                                 # DB 접속 변수 템플릿
├─ gradlew / gradlew.bat                        # wrapper 스크립트
└─ src/
   ├─ main/kotlin/com/fairvalue/
   │  ├─ FairValueApplication.kt                # @SpringBootApplication 진입점
   │  ├─ health/HealthController.kt             # GET /health → {status:UP, version}
   │  └─ contracts/InputHash.kt                 # §2.5 정규화 8단계 (유지)
   ├─ main/resources/
   │  ├─ application.yml                        # 공통 설정(env 기반 datasource)
   │  ├─ application-local.yml                  # local 프로필 기본값
   │  ├─ openapi.yaml                           # REST 계약(W9)
   │  └─ db/migration/V1__init.sql              # Phase 1 7테이블 + enum 3종
   └─ test/kotlin/com/fairvalue/contracts/InputHashTest.kt
```

## 실행 (로컬)

```bash
cd backend

# 1) DB 기동 (PostgreSQL 16)
docker compose up -d db

# 2) 앱 기동 (Flyway V1 마이그레이션 자동 적용)
./gradlew bootRun

# 3) 헬스체크
curl http://localhost:8080/health
#   → {"status":"UP","version":"0.1.0"}
```

접속 정보는 환경변수(`DB_URL`/`DB_USER`/`DB_PASSWORD`)가 우선하며, 미설정 시
`application-local.yml` 기본값(localhost:5432/fairvalue)을 사용합니다. `.env.example` 참고.

## 빌드·테스트

```bash
cd backend && ./gradlew build      # 컴파일 + InputHashTest (DB 불필요)
```

> `./gradlew test` 는 기존 `InputHashTest`(Backend↔Engine 해시 교차검증)를 그대로 실행합니다.
> DB 가 필요한 통합테스트는 이 단계에 포함하지 않습니다(스캐폴딩).
> `gradle-wrapper.jar` 부재 시 CI 가 자동으로 `gradle wrapper` 를 실행합니다.

## DB 스키마 (V1)

Flyway `V1__init.sql` 이 생성하는 것:

- **enum**: `user_role`(5), `instrument_type`(7), `job_status`(5), `instrument_status`(4)
- **테이블(7)**: `organizations`, `users`, `projects`, `instruments`,
  `instrument_terms`, `pricing_jobs`, `audit_logs`

curve/option/redemption/result/simulation 등 나머지 테이블은 후속 마이그레이션(V2~)에서 추가합니다.
JPA `ddl-auto: validate` — 스키마는 Flyway 가 단독 관리하며 JPA 는 검증만 합니다.
