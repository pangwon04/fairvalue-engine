# 백엔드 (backend)

Kotlin/Spring Boot 3.3.2 애플리케이션이다. 도메인 규칙이 전부 모이는 곳으로, 인증·권한·조직 격리·입력 정규화·엔진 호출·영속·보고서 생성을 맡는다. 요청은 대체로 **web → service → repository** 순으로 흐르고, 엔진과 DB는 인터페이스·JPA 뒤에 둔다.

```text
backend/src/main/kotlin/com/fairvalue/
├── web/           # REST 컨트롤러 (11개, ~36 엔드포인트)
├── service/       # 도메인 서비스 (17개)
├── pricing/       # 엔진·컨텍스트 경계 (8개)
├── domain/        # JPA 엔티티·enum (16개)
├── repository/    # Spring Data JPA (10개)
├── dto/           # 요청/응답 (10개)
├── validation/    # rawForm 검증 (4개)
├── security/      # JWT·RBAC (5개)
├── curve/         # 커브 보간·부트스트랩 (2개)
└── contracts/     # InputHash.kt
resources/
├── application.yml · application-local.yml · application-prod.yml
├── db/migration/  # Flyway V1~V8
├── openapi.yaml   # API 계약(OpenAPI 3.1)
└── fonts/         # 보고서 한글 폰트
```

## web/ — 컨트롤러

HTTP 경계다. 각 컨트롤러는 얇게 두고 실제 일은 서비스에 위임한다.

| 컨트롤러 | 담당 |
|---|---|
| `AuthController` · `MeController` | 가입·로그인, 내 정보 |
| `InstrumentController` · `TermsController` | 상품 CRUD, 계약조건 저장 |
| `PricingController` | 평가 실행, 평가 이력·결과, 배치 삭제 |
| `CurveController` · `VolatilityController` | 커브·변동성 등록/조회/삭제, KOFIA 파싱 |
| `ReportController` | 보고서 발급·목록·PDF/Excel 다운로드 |
| `DashboardController` | 대시보드 요약 |
| `AdminUserController` · `ProjectController` | 사용자 역할 관리, 프로젝트 |

## service/ — 도메인 서비스

핵심 로직이 여기 있다.

- **`JobService`** — 평가 파이프라인. 계약조건 로드 → `ContextResolver`로 정규화 → input_hash 캐시 조회 → 엔진 호출 → 결과·스냅샷 저장. 이력 조회·배치 삭제도 담당한다.
- **`ReportService` + `ReportPdfBuilder` + `ReportExcelBuilder`** — 보고서 발급. 저장된 결과로 평가보고서 PDF(OpenPDF, 한글 폰트 임베딩)와 계산근거 엑셀(Apache POI)을 만들고 blob으로 저장한다.
- **`CurveService` · `CurveCalcService` · `CurveMappingService` · `KofiaExcelParser`** — 커브 업로드·버전 이력, 부트스트랩 계산, 평가시점 자동 매핑, 평가사 엑셀 파싱.
- **`VolatilityService` · `VolatilityCalculator`** — 변동성 등록과 역사적 변동성 산출.
- **`InstrumentService` · `TermsService` · `DashboardService` · `AuthService` · `UserAdminService`** — 상품 생애주기, 계약조건, 현황 집계, 인증, 역할 관리.
- **`WriteAccess`** — 쓰기 권한(역할) 검사 헬퍼.

## pricing/ — 엔진·컨텍스트 경계

엔진과 붙는 지점을 인터페이스로 감싸 더미와 실엔진을 갈아끼운다.

- **`ContextResolver`** (인터페이스) → `RealContextResolver`(@Primary) / `DummyContextResolver`. rawForm을 받아 커브·변동성을 매핑하고 시장데이터를 채워 `ResolvedContext`(엔진에 넘길 ValuationContext + input_hash)를 만든다.
- **`PricingEngineClient`** (인터페이스) → `RealPricingEngineClient`(Python `POST /price` 호출) / `DummyPricingEngineClient`(오프라인·테스트용).
- **`PricingOutcome`** — 엔진 응답을 원문(raw JSON)과 타입 결과 두 벌로 들고 있는 상자. 원문을 저장해 트리·근거가 잘리지 않게 한다.

## domain/ — 엔티티·enum

JPA 엔티티와 enum이다. `Organization` · `UserEntity` · `InstrumentEntity` · `InstrumentTermsEntity` · `PricingJobEntity` · `YieldCurveUpload`/`YieldCurvePoint` · `VolatilityData` · `ValuationReport` · `Project`, 그리고 `InstrumentType`(상품 7종) · `UserRole`(RBAC 5역할) · `JobStatus` · `InstrumentStatus` · `CurveKind`/`CurveOrigin` 같은 enum이 여기 있다.

## repository/ — 영속

Spring Data JPA 리포지토리 10개. 조회는 거의 다 `findByIdAndOrgId` 같은 형태로 **조직 격리**를 강제한다. 대시보드·목록용 count/projection 쿼리도 여기 둔다.

## validation/ — 입력 검증

평가 실행 전에 rawForm을 두 단계로 검증한다. `DraftSchemaValidator`가 구조(shared의 draft 스키마)를, `FormRuleValidator`가 상품별 룰(폼 스키마)을 본다. `RawFormValidator`가 이 둘을 묶는 오케스트레이터다.

## security/ — 인증·권한

`SecurityConfig`가 stateless 체인과 CORS·공개 경로(`/health`, `/auth/**` 등)를 정의한다. `JwtService`가 토큰을 발급/검증하고 `JwtAuthFilter`가 요청마다 인증을 심으며, `AuthPrincipal`이 현재 사용자(userId·orgId·role)를 담는다. 역할별 쓰기 제한은 서비스단에서 강제한다.

## contracts/ — `InputHash.kt`

input_hash의 코틀린 구현. 엔진의 파이썬 정본(`app/reproducer.py`)과 동일한 정규화로 같은 해시를 내야 하며, `shared/schemas/hash-test-vectors.json`으로 교차검증한다.

## curve/ — 커브 수학

`InterpolatedCurve`(보간)와 `Bootstrapper`(부트스트랩)로, 커브 계산의 순수 수학 부분을 담는다.

## resources/

`application.yml`은 공통 설정, `-local`/`-prod`는 프로필별 차이(로컬 기본값 / 운영 CORS·문서 비공개)다. DB 스키마는 `db/migration`의 Flyway V1~V8이 관리하고, API 계약은 `openapi.yaml`(OpenAPI 3.1)에 명세한다.

## 테스트

`src/test`에 통합·단위 테스트 101개(@Test). Testcontainers로 실제 PostgreSQL을 띄워 평가·커브·변동성·보고서·삭제·대시보드 경로를 검증하고, `InputHashTest`로 해시 정합을 확인한다.
