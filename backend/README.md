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

## Auth / Org / RBAC (Phase 1-B-1)

JWT 기반 인증 + 조직 격리 + 5역할 RBAC. openapi.yaml 의 Auth/Org 계약을 따른다.

```bash
# 1) 가입 — org_code 가 신규면 조직 생성 + 첫 사용자 ORG_ADMIN
curl -X POST localhost:8080/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.com","pw":"pw12345","org_code":"ACME"}'
#   → 201 {"token":"...","user":{"id":1,"email":"admin@acme.com","role":"ORG_ADMIN","organization_id":1}}

# 2) 같은 org_code 로 후속 가입 → VALUATOR
curl -X POST localhost:8080/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"val@acme.com","pw":"pw12345","org_code":"ACME"}'   # role: VALUATOR

# 3) 로그인 → JWT
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.com","pw":"pw12345"}' | jq -r .token)

# 4) 관리자 — 조직 내 사용자 목록(본인 조직만)
curl localhost:8080/admin/users -H "Authorization: Bearer $TOKEN"

# 5) 역할 변경(같은 조직 + ORG_ADMIN)
curl -X PATCH localhost:8080/admin/users/2 -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"role":"AUDITOR"}'

# 6) 현재 사용자
curl localhost:8080/me -H "Authorization: Bearer $TOKEN"
```

**조직 격리 차단 확인**: 다른 조직(`org_code:"BETA"`)으로 가입한 토큰으로 `GET /admin/users`
를 호출하면 BETA 사용자만 반환되며 ACME 사용자는 보이지 않는다. ACME 사용자 id 로
`PATCH /admin/users/{id}` 를 호출하면 404(누출 차단)다. 미인증 401, VIEWER 의 `/admin/**` 403.

> 에러 본문은 openapi 공통 `Error` 스키마 `{code,message,fields[]}` 형식(401=E401, 403=E403,
> 404=E404, 409=E409, 422=E422).
> `JWT_SECRET` 는 운영에서 반드시 env 로 주입한다(기본값은 개발 전용).

## DB 스키마 (V1)

Flyway `V1__init.sql` 이 생성하는 것:

- **enum**: `user_role`(5), `instrument_type`(7), `job_status`(5), `instrument_status`(4)
- **테이블(7)**: `organizations`, `users`, `projects`, `instruments`,
  `instrument_terms`, `pricing_jobs`, `audit_logs`

curve/option/redemption/result/simulation 등 나머지 테이블은 후속 마이그레이션(V2~)에서 추가합니다.
JPA `ddl-auto: validate` — 스키마는 Flyway 가 단독 관리하며 JPA 는 검증만 합니다.
