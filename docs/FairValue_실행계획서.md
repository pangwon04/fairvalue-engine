# FairValue Engine — 개발 실행계획서 (Execution Plan)

> **문서 성격**: 기존 PRD(`FairValue_Engine_기획서.md`)를 **실제 착수 가능한 개발 실행계획**으로 전환한 문서
> **대상 독자**: 팀원 A(PM/금융도메인/검증), 팀원 B(Backend/DB/API), 팀원 C(Frontend/UX)
> **전제**: 7개 전 상품군(RCPS·CPS·CB·EB·BW·SO·조건부SO) **end-to-end 지원**, MRC 제외, 독립 B2B UX
> **E2E 정의**: 입력 폼 → 입력검증 → Pricing Job 생성 → 엔진 호출 → 결과 저장 → 결과 대시보드 → 결과파일/보고서
> **작성 원칙**: 일정/주차/Sprint 미작성. **우선순위·의존관계·담당·완료기준** 중심. 구현 난이도·리스크는 솔직하게 표기.

> ⚠️ **3명 팀 현실 인식 (문서 전체 관통하는 전제)**
> 7개 상품 × E2E × 상용 엔진 정합성은 객관적으로 **소규모 팀에 매우 도전적**이다. 이 문서의 핵심 전략은 *"상품을 빼지 않되, 공통 코어를 두껍게 만들고 상품별 Calculator를 얇게 확장한다"* 이다. 일부 Calculator는 **검증된 단순 구조(예: CB는 T-F 격자 풀 구현, EB·BW는 DCF+closed-form 우선)** 로 먼저 E2E를 닫고, 동일 인터페이스 위에서 정밀도를 올린다. 이것은 MVP 축소가 아니라 **정밀도 업그레이드 여지를 남긴 완전 구조**다.

---

## 1. 기획서 검수 결과

기존 PRD를 5개 관점(구체성/추가정의/범위리스크/금융공학/UX) + 팀 리스크로 검토했다.

### 1.1 이미 충분히 구체화된 부분

| 구분 | 내용 | 조치 필요 여부 | 우선순위 | 담당 권장자 | 비고 |
|---|---|---|---|---|---|
| 도메인 | 7개 상품 정의·권리조건·결과항목(§7) | 불필요 | — | A | 그대로 구현 기준으로 채택 |
| 엔진 | 상품별 모형 매트릭스(§8.2)·T-F 의사로직(§8.3) | 일부 보완 | 중 | A | 의사로직→테스트 골든값 연결 필요 |
| DB | 22개 테이블 스키마·관계(§21) | 타입/제약 구체화 | 상 | B | DDL 초안화는 §8에서 수행 |
| API | 엔드포인트 표(§22) | Req/Res 스키마 구체화 | 상 | B | §9에서 구체화 |
| 아키텍처 | 2-서비스 분리·Job 흐름(§9, §24) | 불필요 | — | B | 채택 확정 |
| 커브 | 우선순위 규칙·폴백(§10.4)·부트스트랩(§11) | 불필요 | — | A/B | round-trip 검증 기준 명확 |

### 1.2 개발 전 추가 정의가 필요한 부분

| 구분 | 내용 | 조치 필요 여부 | 우선순위 | 담당 권장자 | 비고 |
|---|---|---|---|---|---|
| Form Schema | "상품별 Dynamic Form"의 **스키마 포맷(JSON Schema 표준)** 미정의 | 필요 | 상 | A+C | 검증규칙·표시조건·필드의존을 데이터로 표현하는 포맷 합의가 모든 화면의 선행조건 |
| 입력 정규화 | Backend↔Engine의 **ValuationContext JSON 계약** 미확정 | 필요 | 상 | A+B | §10에서 정의. input_hash 대상 필드 범위도 함께 확정 |
| Golden Value | 상품별 **기대 결과값(검증 기준)** 부재 | 필요 | 상 | A | 엔진 정합성 검증의 절대 선행조건. Excel 수기 평가로 기준값 산출 |
| 변동성 합성 | 비상장 "유사기업 합성" 산식·peer 선정규칙 미정 | 필요 | 중 | A | v1은 단순 가중/중앙값 + 수기 허용으로 구조만 확정 |
| 부호 규약 | 매도청구권/발행자콜 음수 처리(§17.2)의 **엔진 구현 규칙** | 필요 | 중 | A+B | components JSON에 보유자관점 부호 명시 |
| 리픽싱 경로 | StepMonth·Floor·방향(상/하향) 조합의 **상태머신** 미정의 | 필요 | 상 | A | LSMC 패스별 K궤적 추적 규칙 |

### 1.3 범위가 넓어 구현 리스크가 큰 부분

| 구분 | 내용 | 조치 필요 여부 | 우선순위 | 담당 권장자 | 비고 |
|---|---|---|---|---|---|
| 조건부 SO | 시장/비시장 분리(1102)·MC·배리어·true-up 동시 | **리스크 높음** | 중 | A+B | 가장 늦게 구현. 구조는 두되 v1은 시장조건 MC + 비시장 확률가중 단순형으로 E2E 마감 |
| 민감도/시나리오 | 8변수×충격 + 8시나리오 전상품 (§18) | 리스크 중 | 중 | B+C | 엔진 재호출 N회 → Job 비용. v1은 핵심 변수 우선, 프레임워크는 전부 |
| Bootstrapping 보간 | monotone-convex 등 4종 보간 | 리스크 중 | 중 | A/B | v1은 zero 선형 + log-linear 2종 확정 구현, 나머지는 인터페이스만 |
| 보고서 13섹션 | PDF 미리보기+주석+섹션토글 (§20.1) | 리스크 중 | 하 | C | 미리보기/주석은 후순위, 섹션 자동삽입은 필수 |

### 1.4 금융공학적으로 추가 검토가 필요한 부분

| 구분 | 내용 | 조치 필요 여부 | 우선순위 | 담당 권장자 | 비고 |
|---|---|---|---|---|---|
| T-F 이중할인 | equity/debt 컴포넌트 분리 backward induction 정확도 | 필요 | 상 | A | 문헌 벤치마크값 대조 필수. **이 검증이 엔진 신뢰의 핵심** |
| 리픽싱+격자 한계 | 경로의존이라 순수 격자로 부정확 → LSMC 전환 임계 | 필요 | 상 | A | "언제 격자→LSMC 전환"의 규칙 명문화 |
| 상환 IRR 보장 | 보장수익률 누적 원리금을 풋 행사가로(§8.3-2) | 필요 | 중 | A | 누적/비누적 우선배당 가산 처리 검증 |
| 1102 회계정합 | 시장=FV내재 / 비시장=수량 true-up | 필요 | 중 | A | 회계 해석 오류 시 결과 신뢰성 붕괴 → 별도 검수 |
| MC 수렴 | 표준오차 기준·패스수·분산축소 미수치화 | 필요 | 중 | A | 통과기준(표준오차 한계)을 테스트에 못박기 |

### 1.5 UI/UX 관점에서 추가 설계가 필요한 부분

| 구분 | 내용 | 조치 필요 여부 | 우선순위 | 담당 권장자 | 비고 |
|---|---|---|---|---|---|
| Dynamic Form 렌더러 | 스키마→폼 자동생성 엔진(§14.2) | 필요 | 상 | C | 7상품 폼을 개별 하드코딩하면 3명으로 불가. **스키마 구동 렌더러가 생존 전략** |
| 인라인 검증 딥링크 | 항목 옆 경고 + "수정하러 가기"(§14.2) | 필요 | 중 | C | 검증 결과↔필드 매핑 구조 선설계 |
| 결과 대시보드 | 컴포넌트 부호색·토네이도·시나리오표 | 필요 | 중 | C | 차트 라이브러리(Recharts) 컴포넌트 규격화 |
| 좌측 Summary 패널 | 완성도%·검증상태 상시 표시 | 필요 | 중 | C | Wizard 상태와 동기화 구조 |

### 1.6 3명 팀으로 수행 시 특별 관리 리스크

| 구분 | 내용 | 조치 필요 여부 | 우선순위 | 담당 권장자 | 비고 |
|---|---|---|---|---|---|
| 엔진 단일 의존 | Pricing 정합성이 사실상 A 1인에 집중 | **리스크 높음** | 상 | 전원 | A 병목 시 전 상품 지연. B가 엔진 실행구조·테스트하니스 분담 필수 |
| 계약 표류 | Form Schema·ValuationContext·결과 Schema 3계약이 흔들리면 전원 재작업 | 리스크 높음 | 상 | A+B+C | **3대 계약을 Phase 0에서 먼저 동결**하고 변경은 PR 리뷰 |
| 풀스택 부재 | 한 사람이 상품 1개를 끝까지 못 닫음 | 리스크 중 | 중 | 전원 | 상품 E2E를 A(도메인/검증)-B(엔진/API)-C(폼/대시보드) **수직 슬라이스 협업**으로 |
| 테스트 부채 | 엔진 검증 없이 진도만 빼면 후반 붕괴 | 리스크 중 | 상 | A | Golden Value를 **상품 구현 착수 전에** 확보 |

> **충돌 지점 명시**: PRD §25는 P0를 "CB(T-F)"로 둔다. 본 실행계획도 **CB 우선에 동의**하나, PRD가 암묵적으로 가정한 "엔진을 먼저 다 만들고 상품을 붙인다"는 흐름은 3명 팀에 부적합하다. → **수정안: CB 1개를 수직 슬라이스로 끝까지(폼~보고서) 관통**시켜 전 계약·전 모듈을 한 번에 검증한 뒤, 동일 골격에 나머지 6개를 확장한다(§4).

---

## 2. 프로젝트 수행 전략

### 2.1 "모든 상품군 E2E 구현"의 조작적 정의

각 상품이 아래 7단계를 **실 데이터로** 통과하면 해당 상품 E2E "완료"로 본다.

| 단계 | 완료 판정 | Mock 허용? |
|---|---|---|
| 1. 입력 폼 | 상품별 권리조건이 스키마 기반으로 동적 렌더 | ❌ 실제 스키마 구동 |
| 2. 입력 검증 | 오류=차단/경고=주석 2단계, 딥링크 동작 | ❌ |
| 3. Pricing Job 생성 | input_hash·seed·model_version 포함 Job 영속 | ❌ |
| 4. 엔진 호출 | 해당 상품 Calculator가 실제 모형으로 계산 | ❌ (단순화는 OK, Mock은 ❌) |
| 5. 결과 저장 | components/parameters/중간산출 스냅샷 저장 | ❌ |
| 6. 결과 대시보드 | 구성요소·부호·핵심파라미터 렌더 | ❌ |
| 7. 결과파일/보고서 | Excel + PDF 1건 이상 생성·다운로드 | ⚠️ 미리보기/주석은 후순위, 생성은 필수 |

### 2.2 "1차 상용 수준"이라 말할 수 있는 최소 조건

| 조건 | 기준 | 근거 |
|---|---|---|
| 재현성 | 동일 input_hash → 동일 결과(해시 일치) | 감사 대응의 핵심. 이게 없으면 상용 아님 |
| 추적성 | 입력·커브·중간산출·결과가 스냅샷으로 영속 | Audit Pack 내보내기 가능해야 함 |
| 엔진 정합 | 최소 CB·RCPS가 문헌/Excel 골든값과 허용오차 내 일치 | "계산이 맞다"의 증명 |
| 커브 거버넌스 | 기준일·등급별 버전·출처 기록, 자동 매핑 | 파라미터 신뢰 |
| 검증 강도 | null 체크가 아니라 **금융적 부정합** 차단(날짜·parity·권리충돌) | 단순 CRUD와의 차별점 |
| 격리 | 조직 단위 데이터 격리(RBAC) | B2B 전제 |

### 2.3 반드시 공통 모듈화해야 하는 기능 (중복 구현 금지)

| 공통 모듈 | 이유 | 재사용 상품 |
|---|---|---|
| **CurveService** (zero/df/forward·보간·신용가산) | 전 상품이 할인에 동일 인터페이스 사용 | 전 상품 |
| **Lattice Core** (CRR 이항/삼항, backward induction 프레임) | RCPS·CPS·CB·BW 워런트가 공유 | 4종+ |
| **RightsEngine** (전환/교환/콜·풋/매도청구권/리픽싱/희석) | 권리조건을 데이터로 처리 | 전 상품 |
| **MC/LSMC Core** (시드·분산축소·회귀행사경계) | 리픽싱·시장조건·배리어 공유 | RCPS·CB·조건부SO |
| **Dynamic Form Renderer** (스키마→폼) | 7폼 개별 구현 회피 | 전 화면 |
| **Validation Engine** (스키마 규칙 해석) | 인라인+일괄 검증 일원화 | 전 화면 |
| **Job Orchestrator** (enqueue·상태·스냅샷) | 전 상품 동일 실행경로 | 전 상품 |
| **ResultBuilder / AuditWriter** | 결과·중간산출 직렬화 표준 | 전 상품 |

### 2.4 Engine Core + 상품별 Calculator 확장 전략

```
[Engine Core]
  CurveService · ModelLibrary(Lattice/TF/MC/LSMC/BSM/Barrier/DCF)
  · RightsEngine · ResultBuilder · AuditWriter · Reproducer
        ▲ 공통 인터페이스(ValuationContext, PricingResult)
        │
[상품별 Calculator] ── 얇은 어댑터 ──
  RCPS / CPS / CB / EB / BW / StockOption / ConditionalSO
  → 각 Calculator는 "어떤 Core 모듈을 어떤 순서로 조합하는가"만 정의
```

핵심: Calculator는 **수식을 새로 짜지 않고 Core를 오케스트레이션**한다. CB Calculator가 TF-Lattice + RightsEngine(콜/풋/전환/매도청구권) + 리픽싱(LSMC 전환)을 조합하면, RCPS Calculator는 같은 Lattice Core에 상환 IRR 로직만 추가한다.

### 2.5 Mock/Stub로 처리하면 안 되는 부분

| 절대 실제 구현 | 이유 |
|---|---|
| Pricing Job·결과 스냅샷·input_hash 재현성 | 상용 정체성 자체 |
| CurveService와 커브 자동 매핑 | 파라미터 신뢰 |
| 최소 2개 상품(CB·RCPS)의 실제 모형 계산 | "엔진이 있다"의 증명 |
| Validation Engine(금융 부정합 차단) | 차별점 |
| 조직 격리 RBAC | B2B 전제 |
| Excel/PDF 1건 이상 실제 생성 | E2E 정의 충족 |

### 2.6 단순화는 가능하되 반드시 구조를 남겨야 하는 부분

| 항목 | v1 단순화 | 남겨야 할 구조 |
|---|---|---|
| LSMC | 패스 수 보수적, 기본 basis function | 패스수·basis·시드를 파라미터로 노출 |
| 비상장 변동성 | 가중평균/중앙값 + 수기 | peer 선정·비유동성 조정 인터페이스 |
| Bootstrapping 보간 | 선형 + log-linear 2종 | 보간법 enum·monotone-convex 슬롯 |
| 조건부 SO | 시장조건 MC + 비시장 확률가중 | 배리어·true-up·성과경로 시뮬레이터 슬롯 |
| 민감도/시나리오 | 핵심 변수·핵심 시나리오 우선 | 전 변수/전 시나리오 정의·재호출 프레임 |
| 보고서 | 섹션 자동삽입 우선 | 미리보기·주석·섹션토글 슬롯 |

### 2.7 발표/포트폴리오에서 "상용 수준"으로 보이는 핵심 포인트

1. **재현성 데모**: 같은 입력 두 번 계산 → input_hash·결과 동일 화면 시연.
2. **Audit Pack zip**: 입력+커브+중간산출+결과+PDF 한 묶음 내보내기.
3. **엔진 교차검증**: CB를 격자 vs MC로 계산해 허용오차 내 일치 보여주기.
4. **커브 거버넌스**: 기준일·등급 버전·출처·폴백 표기.
5. **금융적 검증**: 잘못된 입력(만기<발행일, parity 충돌)을 엔진이 막는 장면.
6. **7상품 동일 골격**: 한 Wizard로 7상품이 모두 도는 것 자체가 설계력 증명.

### 2.8 가장 큰 실패 리스크와 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| **엔진 정합성 미달**(결과가 틀림) | 프로젝트 신뢰 붕괴 | Golden Value를 **구현 전** 확보, CB·RCPS는 문헌/Excel 대조 통과를 게이트로 |
| **3대 계약 표류** | 전원 재작업 | Phase 0에서 Form Schema·ValuationContext·Result Schema 동결 |
| **A(엔진) 병목** | 전 상품 지연 | B가 엔진 실행구조·테스트하니스 분담, Calculator는 코어 조합이라 난이도 하향 |
| **상품 확장 폭발** | 막판 6개 미완 | CB 수직 슬라이스로 골격 완성 후 복붙 확장, 어려운 조건부SO는 마지막 |
| **데모 미완** | 포트폴리오 실패 | 상품별 E2E "완료기준"을 작게 못박고 1상품씩 닫기 |

---

## 3. 전체 개발 로드맵 (Phase 0–7)

> 일정 없이 **선후관계·완료기준**만. 각 Phase는 선행조건 충족 시 착수.

### Phase 0. 프로젝트 세팅 및 기술 검증

| 항목 | 내용 |
|---|---|
| 목표 | 3대 계약 동결 + 기술 스파이크로 핵심 불확실성 제거 |
| 핵심 기능 | 모노레포 구조, Form Schema/ValuationContext/Result Schema 초안 동결, QuantLib·LSMC 스파이크 |
| 백엔드 작업 | Spring 부트스트랩, Engine FastAPI 스켈레톤, DB 컨테이너, JWT 골격 |
| 프론트 작업 | Next.js 부트스트랩, 디자인토큰(무채색+강조1색), Form Renderer PoC(스키마 1개→폼) |
| Engine 작업 | QuantLib 설치 검증, CRR 격자 1샷 계산, MC 시드 고정 재현성 PoC |
| DB 작업 | 22테이블 ERD 확정, 마이그레이션 도구(Flyway) 셋업 |
| 테스트 항목 | "동일 시드 → 동일 MC 결과" 재현성 PoC 통과 |
| 산출물 | 3대 계약 JSON 스키마, 스파이크 리포트, 레포 구조 |
| 예상 난이도 | 중 |
| 선행 조건 | — |
| 완료 기준 | 3대 계약 v0 머지 + MC 재현성 PoC 통과 + 빈 Wizard가 스키마로 폼 1개 렌더 |

### Phase 1. 공통 플랫폼 기반 구축

| 항목 | 내용 |
|---|---|
| 목표 | 인증·조직·권한·상품 CRUD·Job 골격 = "껍데기 E2E" 동작 |
| 핵심 기능 | Auth/Org/RBAC, Instrument CRUD, Job enqueue→상태→결과 스냅샷(엔진은 더미 계산 OK) |
| 백엔드 작업 | `/auth/*`, `/instruments/*`, Job Orchestrator, input_hash(SHA-256), audit_logs |
| 프론트 작업 | 로그인/조직 온보딩, 공통 레이아웃, Wizard 셸, Summary 패널, 상품 선택 |
| Engine 작업 | Router(상품→핸들러) + Echo Calculator(입력 검증·컨텍스트 정규화만, 값은 placeholder) |
| DB 작업 | users·organizations·instruments·pricing_jobs·audit_logs 마이그레이션 |
| 테스트 항목 | 로그인→상품생성→Job생성→상태조회 API Contract Test |
| 산출물 | 작동하는 인증·Job 파이프라인 |
| 예상 난이도 | 중 |
| 선행 조건 | Phase 0 계약 동결 |
| 완료 기준 | 사용자가 로그인→상품 만들고→Job 돌려 placeholder 결과까지 화면에서 확인 |

### Phase 2. Parameter / Curve Management 구축

| 항목 | 내용 |
|---|---|
| 목표 | 커브 업로드·버전·자동매핑·수기·Bootstrapping = 엔진의 할인 소스 확보 |
| 핵심 기능 | 커브 업로드(원본 스키마 검증)·버전·조직공유, (기준일,등급) 자동매핑, 수기입력, CurveService(zero/df/forward·보간), Bootstrapping round-trip |
| 백엔드 작업 | `/curves/*`, `/curves/bootstrap`, 업로드 권한 승인 큐, 우선순위 규칙(수기>업로드>부트스트랩) |
| 프론트 작업 | 커브 업로드 화면(권한상태·이력·만기표), 수기입력 그리드, Bootstrapping 화면(par→zero/df 차트) |
| Engine 작업 | CurveService 정식 구현, Bootstrapping(선형+log-linear), 음의 forward 경고 |
| DB 작업 | yield_curve_uploads·yield_curve_points, 버전·출처 컬럼 |
| 테스트 항목 | Curve Round-trip(par 재평가→100 복원), 자동매핑 정확도 |
| 산출물 | 커브 거버넌스 전체 |
| 예상 난이도 | 중상 |
| 선행 조건 | Phase 1 |
| 완료 기준 | 업로드한 커브가 평가에 자동 매핑되고 round-trip 통과, 화면에서 버전·출처 표기 |

### Phase 3. Pricing Engine Core 구축

| 항목 | 내용 |
|---|---|
| 목표 | 상품 calculator가 올라탈 **공통 엔진 코어** 완성 |
| 핵심 기능 | ModelLibrary(Lattice/TF/MC/LSMC/BSM/Barrier/DCF), RightsEngine, ResultBuilder, AuditWriter, Reproducer |
| 백엔드 작업 | Engine 호출 계약 확정, 결과/중간산출 영속, 캐시(input_hash→결과) |
| 프론트 작업 | (병렬) 결과 대시보드 컴포넌트 골격 |
| Engine 작업 | TF-Lattice 정식 구현, CRR 격자, MC/LSMC, BSM, DCF, AuditWriter(현금흐름·DF·노드·패스·행사로그) |
| DB 작업 | pricing_results·pricing_parameters·simulation_results·model_versions |
| 테스트 항목 | Engine Regression(골든값), 격자 vs MC 교차검증, 재현성(해시) |
| 산출물 | 검증된 엔진 코어 |
| 예상 난이도 | **상 (최난이도)** |
| 선행 조건 | Phase 2 (커브 필요) |
| 완료 기준 | CB가 TF로 문헌/Excel 골든값과 허용오차 내 일치 + 격자 vs MC 교차검증 통과 |

### Phase 4. 모든 상품군별 Pricing 기능 구현

| 항목 | 내용 |
|---|---|
| 목표 | 7개 상품 Calculator E2E 완성 |
| 핵심 기능 | CB→RCPS→CPS→BW→EB→SO→조건부SO 순(§4) Calculator + 상품별 폼 스키마 + 검증규칙 |
| 백엔드 작업 | 상품별 terms CRUD(option/redemption/call/refixing), 라우팅 |
| 프론트 작업 | 상품별 Dynamic Form 스키마, 인라인 검증 딥링크, 기초자산 검색·매핑 |
| Engine 작업 | 상품별 Calculator(코어 조합), 상환 IRR, 희석, 교환(타사주), 1102 |
| DB 작업 | option_terms·redemption_terms·call_terms·refixing_terms·underlying_assets·volatility_data·dividend_data |
| 테스트 항목 | 상품별 Golden Value, 권리조건 부호 점검, 모형-조건 정합성 |
| 산출물 | 7상품 E2E |
| 예상 난이도 | 상 |
| 선행 조건 | Phase 3 |
| 완료 기준 | 7상품 각각 입력→계산→결과 대시보드까지 실 데이터로 통과 |

### Phase 5. 결과 대시보드 / 보고서 / Audit Pack

| 항목 | 내용 |
|---|---|
| 목표 | 산출물 자동화 완성 |
| 핵심 기능 | 결과 대시보드(부호색·파라미터), 민감도(토네이도)·시나리오(비교표), Excel·PDF·Audit Pack(zip) |
| 백엔드 작업 | `/export.xlsx`·`/report.pdf`·`/audit-pack`, `/sensitivity`·`/scenarios` |
| 프론트 작업 | 대시보드 완성, 토네이도/스파이더, 보고서 미리보기·섹션토글 |
| Engine 작업 | 민감도(변수충격 재계산), 시나리오(동일시드 재계산), 보고서 데이터 빌더 |
| DB 작업 | valuation_reports·file_downloads |
| 테스트 항목 | Report Output Test(섹션 누락/값 일치), Audit Pack 무결성 |
| 산출물 | Excel·PDF·zip |
| 예상 난이도 | 중상 |
| 선행 조건 | Phase 4(상품별 결과 존재) |
| 완료 기준 | 한 평가에서 Excel+PDF+Audit Pack 생성·다운로드, 민감도 토네이도 표시 |

### Phase 6. 검증 / 테스트 / 성능 최적화

| 항목 | 내용 |
|---|---|
| 목표 | 상용 신뢰성 게이트 통과 |
| 핵심 기능 | 골든값 회귀 전상품, MC 표준오차 기준, 재현성 E2E, 멀티프로세스 MC |
| 백엔드 작업 | 캐시 적중률, Job 우선순위 큐, 동시성 |
| 프론트 작업 | 검증 UX 마감, 로딩/에러 상태 |
| Engine 작업 | LSMC 패스수 튜닝, 분산축소(안티테틱/소볼), 멀티프로세스 |
| DB 작업 | 인덱스 점검, 쿼리 최적화 |
| 테스트 항목 | 전체 회귀·성능·재현성·보안 |
| 산출물 | 테스트 리포트 |
| 예상 난이도 | 중 |
| 선행 조건 | Phase 5 |
| 완료 기준 | 7상품 골든값 회귀 통과 + MC 표준오차 기준 충족 + 동일입력 재현 일치 |

### Phase 7. 배포 / 포트폴리오 문서화

| 항목 | 내용 |
|---|---|
| 목표 | 배포 + 발표/포트폴리오 산출물 |
| 핵심 기능 | Docker Compose 배포, README·아키텍처 다이어그램, 7상품 데모 시나리오, 시연영상 |
| 백엔드 작업 | 배포 구성, 환경변수·시크릿, 헬스체크 |
| 프론트 작업 | 데모 데이터 시드, 시연 동선 정리 |
| Engine 작업 | 모델 버전 changelog, 성능 수치 정리 |
| DB 작업 | 시드 데이터(데모용 커브·상품) |
| 테스트 항목 | 배포 환경 E2E smoke |
| 산출물 | 배포본·README·발표자료·영상 |
| 예상 난이도 | 중 |
| 선행 조건 | Phase 6 |
| 완료 기준 | 클린 환경에서 7상품 데모가 처음부터 끝까지 재현 |

> **로드맵 핵심**: Phase 3(엔진 코어)이 최난이도이자 병목. **단, Phase 4의 CB 1상품은 Phase 3와 동시에 수직 슬라이스로 관통**시켜 코어를 즉시 실전 검증한다(완전 순차 진행 금지).

---

## 4. 상품군별 구현 우선순위

> 모든 상품은 E2E 대상. 아래는 **개발 순서**만 정한 것이며 제외 상품은 없다.

| 상품 | 구현 순서 | 먼저 구현해야 하는 이유 | 재사용 가능한 모듈 | 난이도 | 핵심 리스크 | E2E 완료 기준 | 담당 권장자 |
|---|---|---|---|---|---|---|---|
| **CB** | **1** | 모든 권리조건(전환·콜·풋·매도청구권·리픽싱) 포함 → 코어 전체를 한 번에 검증. TF격자=엔진 정점 | TF-Lattice, RightsEngine 전부, 신용할인, LSMC | 상 | T-F 이중할인 정확도, 매도청구권 부호 | 입력~Excel/PDF, 격자 vs MC 교차검증 통과 | A(엔진)+B(API)+C(폼) |
| **RCPS** | **2** | CB 격자 재사용 + 상환 IRR만 추가. 데모 임팩트 큼(스타트업 메자닌) | CB Lattice/Rights 대부분 | 중상 | 상환 IRR·우선배당 누적 | 우선주/전환/상환/매도청구권 결과 + 보고서 | A+B+C |
| **CPS** | **3** | RCPS에서 상환 제거 → 거의 공짜. 검증 쉬움 | RCPS 격자(상환 비활성) | 중 | host(영구/만기) 가치 | 우선주/전환/Parity 결과 | A+C |
| **BW** | **4** | 사채+워런트 분리 = "분리평가" 패턴 첫 도입, 희석계수 데모 | DCF, BSM 워런트, 희석 | 중 | 희석계수 범위·분리형 분해 | 채권/신주인수권/희석효과 결과 | A+B+C |
| **EB** | **5** | BW의 분리평가 재사용 + 기초=타사주(희석 0). 신용·기초 분리 | DCF, 교환옵션(BSM/격자), 기초 별도매핑 | 중 | 대상사≠발행사 매핑 혼동 방지 | 채권/교환권 결과 | A+C |
| **Stock Option** | **6** | 채권 로직 무관, BSM/격자 신규축. 1102 비용스케줄 도입 | BSM, 이항(조기행사), MC | 중 | 기대만기·퇴사율·가득 | 단가/총가치/보상원가/비용스케줄 | A+C |
| **조건부 SO** | **7** | 가장 복잡(시장/비시장 분리·MC·배리어·true-up). 마지막에 시간 집중 | MC/LSMC, Barrier, 확률조정 | **상** | 1102 회계정합·경로의존 | 조건부가치/충족확률/조건별스케줄/시나리오 | A+B+C |

> **순서 논리**: CB가 코어를 최대로 행사 → RCPS/CPS는 격자 재사용으로 빠르게 → BW/EB는 분리평가 패턴 공유 → SO/조건부SO는 별도 축(주식보상)이라 분리. **앞 4개를 격자 계열로 묶어 재사용을 극대화**한다.


---

## 5. 팀원별 역할분담 및 어싸인

> 원칙: 상품 E2E는 한 사람이 독점하지 않고 **A(도메인/검증)-B(엔진실행/API/DB)-C(폼/대시보드)** 수직 슬라이스로 병렬 진행.

### 5.1 팀원 A (PM / 금융도메인 / Pricing Logic 검증 / 문서화)

| 팀원 | 주요 역할 | 담당 모듈 | 구체 태스크 | 산출물 | 의존관계 | 우선순위 | 완료 기준 |
|---|---|---|---|---|---|---|---|
| A | 도메인 정의 | 상품 계약조건 | 7상품 계약/권리조건 필드·검증규칙 확정 | 상품 도메인 명세 | — | 상 | 7상품 필드·검증규칙 표 동결 |
| A | Form Schema | Dynamic Form | 상품별 폼 스키마(JSON) 작성·C와 합의 | 7개 form schema | 계약 동결 | 상 | C 렌더러가 7폼 구동 |
| A | 엔진 정합 | Engine Core/Calculator | 각 모형 요구사항·의사로직→Python 보조구현 | 모형 명세·일부 코드 | Phase3 | 상 | CB·RCPS 골든값 통과 |
| A | Golden Value | 테스트 | Excel 수기 평가로 상품별 기대값 산출 | Golden Value set | 도메인 명세 | **상** | 상품 구현 착수 전 확보 |
| A | 검증 정의 | Validation Engine | 금융 부정합 규칙(날짜·parity·권리충돌) 명세 | 검증규칙 카탈로그 | 계약 | 중 | 규칙→B/C 구현 매핑 |
| A | 회계 정합 | 보고서/1102 | 보고서 13섹션·1102 시장/비시장 검수 | 보고서 템플릿 | Phase5 | 중 | 보고서 섹션 자동삽입 검수 |
| A | 문서화 | 포트폴리오 | README·발표·데모 시나리오 | 발표자료·영상 | Phase7 | 중 | 7상품 데모 대본 |

### 5.2 팀원 B (Backend / Database / API / Auth / Job)

| 팀원 | 주요 역할 | 담당 모듈 | 구체 태스크 | 산출물 | 의존관계 | 우선순위 | 완료 기준 |
|---|---|---|---|---|---|---|---|
| B | 인증·격리 | Auth/Org/RBAC | JWT·조직격리·5역할 RBAC | 인증 API | — | 상 | 조직간 데이터 격리 테스트 통과 |
| B | DB | 전체 스키마 | 22테이블 DDL·마이그레이션·인덱스 | 마이그레이션 | ERD 동결 | 상 | 전 테이블 생성·FK 무결성 |
| B | API | 전체 REST | 13개 API 그룹 구현 | API + 계약테스트 | 스키마 | 상 | Contract Test 통과 |
| B | Job | Job Orchestrator | enqueue·상태·결과 스냅샷·input_hash·캐시 | Job 파이프라인 | Auth | 상 | 재현성 해시 일치 |
| B | 엔진 실행 | Engine 호출 | Backend↔Engine 계약·Worker·결과 영속 | 실행 구조 | Phase3 계약 | 상 | 상품 Calculator 호출 성공 |
| B | 커브 | Curve Upload/Service 일부 | 업로드·검증·버전·자동매핑 API | 커브 API | DB | 중상 | 자동매핑·버전 동작 |
| B | 산출물 | Export | Excel/PDF/zip 생성 파이프라인·S3 | 파일 API | Phase5 | 중 | 3종 산출물 다운로드 |
| B | 엔진 보조 | Calculator(난이도 분담) | CB/조건부SO 실행구조·테스트하니스 | 엔진 분담 | A와 협업 | 중 | A 병목 완화 |

### 5.3 팀원 C (Frontend / UX / Dashboard / Form)

| 팀원 | 주요 역할 | 담당 모듈 | 구체 태스크 | 산출물 | 의존관계 | 우선순위 | 완료 기준 |
|---|---|---|---|---|---|---|---|
| C | 디자인 시스템 | 공통 레이아웃 | 디자인토큰·데이터그리드·숫자포맷(우측정렬·음수색괄호) | UI 키트 | — | 상 | 공통 컴포넌트 셋 |
| C | Form Renderer | Dynamic Form | 스키마→폼 자동생성 엔진(필드의존·표시조건) | 렌더러 | A 스키마 | **상** | 7폼 스키마 구동 |
| C | Wizard | Stepper/Summary | 7단계 Wizard·좌측 Summary(완성도%·검증) | Wizard | 렌더러 | 상 | 단계 자유이동·상태동기 |
| C | 검증 UX | 인라인 검증 | 항목 옆 경고+"수정하러 가기" 딥링크 | 검증 UI | 검증 API | 중 | 오류=차단/경고=주석 |
| C | 기초자산 | Asset 검색 | 상장/비상장 검색·매핑 미리보기 모달 | Asset UI | Asset API | 중 | 상장1/비상장다수 매핑 |
| C | 대시보드 | Result Dashboard | 구성요소 부호색·파라미터·토네이도·시나리오표 | 대시보드 | 결과 API | 중 | 7상품 결과 렌더 |
| C | 커브 화면 | Curve/Bootstrap | 업로드·수기그리드·부트스트랩 차트 | 커브 UI | 커브 API | 중 | 업로드~매핑 표시 |
| C | 보고서 | Report Preview | 미리보기·섹션토글·다운로드 | 보고서 UI | Report API | 하 | 미리보기→PDF |

### 5.4 Pricing Engine 협업 구조 (3자 분담)

| 역할 | 담당 | 내용 |
|---|---|---|
| 요구사항·검증 | **A** | 모형 선택·의사로직·Golden Value·결과 정합 검수 |
| 실행구조·API·테스트하니스 | **B** | FastAPI·Worker·계약·회귀테스트 골격 |
| Python 계산 보조 | **A**(주), B(보조) | Calculator 수식 구현, 난이도 높은 부분 B와 페어 |

---

## 6. 역할별 책임 매트릭스 (RACI)

> R=실행, A=최종책임, C=자문, I=공유. (역할 A=PM/도메인, B=Backend, C=Frontend)

| 업무 | A | B | C |
|---|---|---|---|
| 상품별 계약조건 정의 | **R/A** | C | C |
| Dynamic Form Schema 설계 | **A**/R | I | **R** |
| DB 스키마 설계 | C | **R/A** | I |
| API 명세 작성 | C | **R/A** | C |
| Auth / Organization / RBAC | I | **R/A** | I |
| Yield Curve Upload | C | **R/A** | R |
| Bootstrapping | **R**(검증) | **A**/R | R(화면) |
| Curve Service | **A**(요구) | **R** | I |
| Pricing Job Orchestration | I | **R/A** | I |
| Pricing Engine Core | **A**/R | R(실행구조) | I |
| RCPS Calculator | **R/A** | C | I |
| CPS Calculator | **R/A** | C | I |
| CB Calculator | **R/A** | R(실행) | I |
| EB Calculator | **R/A** | C | I |
| BW Calculator | **R/A** | C | I |
| Stock Option Calculator | **R/A** | C | I |
| Conditional SO Calculator | **R/A** | **R**(MC실행) | I |
| Validation Engine | **A**(규칙) | R(서버) | R(인라인) |
| Result Dashboard | C | C | **R/A** |
| Report Generator | **A**(섹션검수) | R(생성) | R(미리보기) |
| Audit Pack Generator | C | **R/A** | I |
| 테스트 케이스 작성 | **R/A** | R | R |
| 배포 | I | **R/A** | C |
| README / 발표자료 | **R/A** | C | C |

> 주의: **Pricing Engine Core의 A(최종책임)는 팀원 A**지만, 실행구조 R은 B가 가져가 병목을 분산한다. Conditional SO는 MC 실행을 B가 R로 받아 A의 부하를 낮춘다.

---

## 7. 모듈 분해

| 모듈 | 역할 | 주요 기능 | 입력 데이터 | 출력 데이터 | 주요 API | 관련 DB 테이블 | 선행 모듈 | 테스트 방법 | 난이도 | 담당 |
|---|---|---|---|---|---|---|---|---|---|---|
| Auth/Org/RBAC | 인증·격리 | 로그인·조직·5역할 | 자격증명 | JWT·권한 | `/auth/*` `/admin/*` | users·organizations | — | 격리·권한 테스트 | 중 | B |
| Project/Instrument | 평가대상 관리 | 상품 CRUD·상태 | 상품 메타 | instrument | `/instruments/*` | projects·instruments·*_terms | Auth | CRUD 테스트 | 중 | B |
| Dynamic Form Schema | 폼 정의 | 스키마→폼·검증·표시조건 | 스키마 JSON | 렌더 폼 | (FE 내부) | — | 계약 | 스키마 구동 | 상 | A+C |
| Underlying Asset | 기초자산 | 상장/비상장 검색·매핑·변동성 | ticker/peer | asset·vol | `/assets/*` | underlying_assets·volatility_data·dividend_data | Auth | 매핑 테스트 | 중 | B+C |
| Yield Curve Upload | 커브 적재 | 원본검증·버전·승인큐 | Excel/CSV | 커브점 | `/curves` | yield_curve_uploads/points | Auth | 스키마검증 | 중 | B+C |
| Bootstrapping | 커브 생성 | par→zero/df/forward | par_rates | zero/df | `/curves/bootstrap` | yield_curve_* | Curve Upload | round-trip | 중상 | A+B |
| Curve Service | 할인 소스 | 보간·신용가산·우선순위 | 커브점 | DF/z(t) | (Engine 내부) | yield_curve_* | Upload/Bootstrap | 보간 검증 | 중상 | B(A검증) |
| Job Orchestration | 계산 실행 | enqueue·상태·스냅샷·캐시 | 정규화입력 | job·result | `/instruments/{id}/price` `/jobs/*` | pricing_jobs·results | Auth | 재현성 해시 | 상 | B |
| Pricing Engine Core | 공통 엔진 | ModelLibrary·정규화·재현 | ValuationContext | PricingResult | (Engine API) | pricing_results·simulation_results | Curve | 골든값·교차 | **상** | A+B |
| Rights Engine | 권리 처리 | 전환/교환/콜·풋/매도청구권/리픽싱/희석 | 권리조건 | 행사판단 | (Engine 내부) | *_terms | Engine Core | 부호·행사 로그 | 상 | A |
| Model Library | 모형 집합 | Lattice/TF/MC/LSMC/BSM/Barrier/DCF | context | 가치 | (Engine 내부) | — | Curve | 모형별 골든값 | 상 | A |
| Validation Engine | 입력 검증 | 금융 부정합 차단·인라인 | 입력 | 오류/경고 | `/terms`(검증) | *_terms | 계약 | 부정합 케이스 | 중 | A+B+C |
| Result Dashboard | 결과 표시 | 구성요소·부호·파라미터 | result | UI | `/jobs/{id}/result` | pricing_results | Engine | 렌더 테스트 | 중 | C |
| Sensitivity/Scenario | 분석 | 변수충격·시나리오 재계산 | result+변수 | grid/비교 | `/sensitivity` `/scenarios` | pricing_jobs | Engine | 방향성 검증 | 중상 | B+C |
| Report Generator | 보고서 | 13섹션 PDF·미리보기 | result | PDF | `/report.pdf` | valuation_reports | Result | 섹션 일치 | 중상 | B+C |
| Audit Pack | 감사 패키지 | 입력+커브+산출+PDF zip | snapshot | zip | `/audit-pack` | simulation_results·reports | Report | 무결성 | 중 | B |
| Admin Console | 관리 | 사용자·권한승인·모델버전 | 관리입력 | 변경 | `/admin/*` | users·model_versions | Auth | 권한 테스트 | 중 | B+C |
| Logging/Monitoring | 운영 | 접근로그·지연·실패율 | 이벤트 | 로그/지표 | — | audit_logs·file_downloads | — | 로그 적재 | 하 | B |


---

## 8. DB 설계 구체화 (PostgreSQL)

> 원칙: 입력·커브·결과를 **스냅샷으로 영속(재현성)**, 조직 격리, 감사 로그 분리, 가변 구조는 `jsonb`. 모든 테이블 `created_at timestamptz default now()`.

### 8.1 테이블 명세 요약

| 테이블 | 설명 | 핵심 컬럼 | PK | FK | Index | Unique | 주의점 |
|---|---|---|---|---|---|---|---|
| organizations | 조직(테넌트) | name, plan | id | — | — | name | 격리 루트 |
| users | 사용자 | email, role, org_id | id | org_id | (org_id) | email | role enum 5종 |
| projects | 평가 묶음 | name, owner_id | id | org_id, owner_id | (org_id) | — | 선택적 그룹핑 |
| instruments | 평가 대상 | type, issuer, status | id | org_id, project_id | (org_id,type) | — | type enum 7종 |
| instrument_terms | 기본 계약조건 | issue_date, maturity_date, coupon_rate | id | instrument_id | — | instrument_id | 1:1 |
| option_terms | 전환/교환/신주인수 | kind, conv_price, ratio, dilution_flag | id | instrument_id, underlying_id | (instrument_id) | — | kind enum |
| redemption_terms | 상환(콜/풋) | side, strike, style, yield | id | instrument_id | (instrument_id) | — | side enum |
| call_terms | 매도청구권 | discount_type, strike, standalone_flag | id | instrument_id | (instrument_id) | — | 부호 주의 |
| refixing_terms | 리픽싱 | start, step_month, floor, init_strike, direction | id | instrument_id | (instrument_id) | — | direction enum |
| underlying_assets | 기초자산 | listed_flag, ticker, shares_outstanding | id | org_id | (ticker) | (org_id,ticker) | 상장/비상장 |
| volatility_data | 변동성 | as_of, vol, source, method | id | asset_id | (asset_id,as_of) | — | 수기 플래그 |
| dividend_data | 배당 | as_of, div_yield, source | id | asset_id | (asset_id,as_of) | — | — |
| yield_curve_uploads | 커브 헤더 | as_of, kind, grade, version, status | id | org_id, uploader_id | (org_id,as_of,grade) | (org_id,as_of,kind,grade,version) | 버전 증가 |
| yield_curve_points | 커브 만기점 | tenor, rate | id | upload_id | (upload_id) | (upload_id,tenor) | 만기 그리드 고정 |
| pricing_jobs | 계산 작업 | model, seed, input_hash, status | id | instrument_id, requested_by | (instrument_id), (input_hash) | — | 캐시키=input_hash |
| pricing_parameters | 적용 파라미터 | rfr, ytm, credit_spread, parity, curve_source | id | job_id | — | job_id | 1:1 |
| pricing_results | 결과 | total_fv, per_unit, components, pnl | id | job_id | — | job_id | components jsonb |
| simulation_results | 중간산출 | cashflows, df_table, node_summary, path_summary, exercise_log | id | job_id | — | job_id | 대용량 jsonb |
| valuation_reports | 보고서 | type, storage_key, generated_at | id | job_id | (job_id) | — | pdf/xlsx/zip |
| model_versions | 모델 버전 | model, version, changelog | id | — | (model) | (model,version) | 재현성 참조 |
| file_downloads | 다운로드 이력 | downloaded_at, ip | id | user_id, report_id | (report_id) | — | 감사 |
| audit_logs | 접근·행위 로그 | action, target, payload | id | org_id, user_id | (org_id,created_at) | — | append-only |

### 8.2 PostgreSQL DDL 초안 (핵심 테이블)

```sql
-- enum
CREATE TYPE user_role AS ENUM ('ORG_ADMIN','CURVE_MANAGER','VALUATOR','AUDITOR','VIEWER');
CREATE TYPE instrument_type AS ENUM ('RCPS','CPS','CB','EB','BW','SO','CSO');
CREATE TYPE job_status AS ENUM ('QUEUED','RUNNING','DONE','FAILED');
CREATE TYPE option_kind AS ENUM ('CONVERSION','EXCHANGE','WARRANT');
CREATE TYPE redemption_side AS ENUM ('CALL','PUT');
CREATE TYPE exercise_style AS ENUM ('AMERICAN','EUROPEAN','BERMUDAN');
CREATE TYPE refix_direction AS ENUM ('DOWN','BOTH');
CREATE TYPE curve_kind AS ENUM ('RISK_FREE','CREDIT');

CREATE TABLE organizations (
  id           BIGSERIAL PRIMARY KEY,
  name         TEXT NOT NULL UNIQUE,
  plan         TEXT NOT NULL DEFAULT 'standard',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
  id           BIGSERIAL PRIMARY KEY,
  org_id       BIGINT NOT NULL REFERENCES organizations(id),
  email        TEXT NOT NULL UNIQUE,
  name         TEXT NOT NULL,
  role         user_role NOT NULL DEFAULT 'VALUATOR',
  status       TEXT NOT NULL DEFAULT 'active',
  password_hash TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_org ON users(org_id);

CREATE TABLE instruments (
  id           BIGSERIAL PRIMARY KEY,
  org_id       BIGINT NOT NULL REFERENCES organizations(id),
  project_id   BIGINT REFERENCES projects(id),
  type         instrument_type NOT NULL,
  issuer       TEXT,
  name         TEXT NOT NULL,
  status       TEXT NOT NULL DEFAULT 'draft',
  created_by   BIGINT REFERENCES users(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_instruments_org_type ON instruments(org_id, type);

CREATE TABLE instrument_terms (
  id            BIGSERIAL PRIMARY KEY,
  instrument_id BIGINT NOT NULL UNIQUE REFERENCES instruments(id) ON DELETE CASCADE,
  issue_date     DATE NOT NULL,
  maturity_date  DATE NOT NULL,
  face_value     NUMERIC(20,4),
  issue_amount   NUMERIC(20,4),
  issue_price    NUMERIC(20,4),
  coupon_rate    NUMERIC(9,6),       -- %
  freq_month     INT,
  guaranteed_yield NUMERIC(9,6),     -- %
  redemption_type TEXT DEFAULT 'bullet',
  CHECK (maturity_date > issue_date)
);

CREATE TABLE refixing_terms (
  id            BIGSERIAL PRIMARY KEY,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
  start_date    DATE,
  step_month    INT,
  floor_price   NUMERIC(20,4),
  init_strike   NUMERIC(20,4),
  direction     refix_direction NOT NULL DEFAULT 'DOWN',
  CHECK (floor_price >= 0 AND floor_price <= init_strike)
);

CREATE TABLE yield_curve_uploads (
  id           BIGSERIAL PRIMARY KEY,
  org_id       BIGINT NOT NULL REFERENCES organizations(id),
  as_of        DATE NOT NULL,
  kind         curve_kind NOT NULL,
  grade        TEXT,                 -- 신용등급(무위험은 NULL)
  uploader_id  BIGINT REFERENCES users(id),
  version      INT NOT NULL DEFAULT 1,
  status       TEXT NOT NULL DEFAULT 'active',
  source_flag  TEXT NOT NULL DEFAULT 'uploaded', -- uploaded/manual/bootstrap
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (org_id, as_of, kind, grade, version)
);
CREATE INDEX idx_curve_lookup ON yield_curve_uploads(org_id, as_of, grade);

CREATE TABLE yield_curve_points (
  id         BIGSERIAL PRIMARY KEY,
  upload_id  BIGINT NOT NULL REFERENCES yield_curve_uploads(id) ON DELETE CASCADE,
  tenor      NUMERIC(6,3) NOT NULL,  -- 0.25=3M ... 50
  rate       NUMERIC(12,8) NOT NULL, -- %
  UNIQUE (upload_id, tenor)
);

CREATE TABLE pricing_jobs (
  id           BIGSERIAL PRIMARY KEY,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  model        TEXT NOT NULL,
  seed         BIGINT NOT NULL DEFAULT 20240101,
  input_hash   CHAR(64) NOT NULL,    -- SHA-256
  model_version_id BIGINT REFERENCES model_versions(id),
  status       job_status NOT NULL DEFAULT 'QUEUED',
  requested_by BIGINT REFERENCES users(id),
  started_at   TIMESTAMPTZ,
  finished_at  TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_jobs_instrument ON pricing_jobs(instrument_id);
CREATE INDEX idx_jobs_hash ON pricing_jobs(input_hash);

CREATE TABLE pricing_results (
  id         BIGSERIAL PRIMARY KEY,
  job_id     BIGINT NOT NULL UNIQUE REFERENCES pricing_jobs(id) ON DELETE CASCADE,
  total_fv   NUMERIC(24,6),
  per_unit   NUMERIC(24,6),
  pnl        NUMERIC(24,6),
  components JSONB NOT NULL          -- {"bond":..,"conversion":..,"issuer_call":-..}
);

CREATE TABLE simulation_results (
  id           BIGSERIAL PRIMARY KEY,
  job_id       BIGINT NOT NULL UNIQUE REFERENCES pricing_jobs(id) ON DELETE CASCADE,
  cashflows    JSONB,
  df_table     JSONB,
  node_summary JSONB,
  path_summary JSONB,
  exercise_log JSONB
);

CREATE TABLE audit_logs (
  id         BIGSERIAL PRIMARY KEY,
  org_id     BIGINT REFERENCES organizations(id),
  user_id    BIGINT REFERENCES users(id),
  action     TEXT NOT NULL,
  target     TEXT,
  payload    JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_org_time ON audit_logs(org_id, created_at);
```

> `option_terms`·`redemption_terms`·`call_terms`·`projects`·`underlying_assets`·`volatility_data`·`dividend_data`·`pricing_parameters`·`valuation_reports`·`model_versions`·`file_downloads`는 위 패턴(FK·CHECK·index)을 따라 동일 양식으로 추가. (지면상 핵심 테이블 DDL만 제시 — 나머지는 §8.1 명세표 기반으로 B가 동일 규칙 적용)

### 8.3 저장 예시

```jsonc
// pricing_results.components  (CB, 보유자 관점 부호)
{ "bond": 8326.94, "conversion": 314301.52, "redemption": 2047.61,
  "issuer_call": -157041.84, "total": 167634.23 }

// pricing_parameters
{ "rfr": 3.23, "ytm": 19.02, "parity": 3260.00,
  "curve_source": "uploaded:2025-01-02/B-/v1" }

// pricing_jobs.input_hash = sha256(정규화된 ValuationContext JSON)
```

### 8.4 설계상 주의점

| 항목 | 주의 |
|---|---|
| input_hash 범위 | 시각·UI상태 제외, **계산에 영향 주는 입력+커브 스냅샷만** 정규화 후 해시 (재현성·캐시 정확도) |
| 커브 버전 | 동일 (org,as_of,kind,grade) 재업로드는 version 증가, status로 active 관리 |
| jsonb 대용량 | simulation_results는 노드/패스 **요약**만 저장(전량 저장 금지), 원본은 파일로 |
| 부호 규약 | components는 항상 보유자 관점, 발행자/제3자 콜은 음수 (§17.2) |
| 조직 격리 | 모든 조회 쿼리에 org_id 필터 강제(애플리케이션 레벨 + RLS 검토) |
| terms 분기 | RightsEngine가 type별 필요한 terms만 로드, 불필요 terms NULL 허용 |


---

## 9. API 설계 구체화 (REST)

> 공통: `Authorization: Bearer <JWT>`, JSON, 오류 `{code, message, fields[]}`. 모든 응답은 조직 격리 적용.

| 그룹 | Method | Endpoint | 설명 | Request 예시 | Response 예시 | Error | 권한 | DB |
|---|---|---|---|---|---|---|---|---|
| Auth | POST | `/auth/signup` | 가입 | `{email,pw,org_code}` | `{user,token}` | 409,422 | public | users |
| Auth | POST | `/auth/login` | 로그인 | `{email,pw}` | `{token,user}` | 401 | public | users |
| Org/User | GET | `/admin/users` | 사용자 목록 | — | `{items:[...]}` | 403 | ORG_ADMIN | users |
| Org/User | PATCH | `/admin/users/{id}` | 역할변경 | `{role}` | `{user}` | 403 | ORG_ADMIN | users |
| Project | POST | `/projects` | 평가묶음 생성 | `{name}` | `{id,name}` | 422 | VALUATOR+ | projects |
| Instrument | POST | `/instruments` | 상품 생성 | `{type:"CB",name,issuer}` | `{id,type,status}` | 422 | VALUATOR+ | instruments |
| Instrument | GET | `/instruments?type&status` | 목록 | — | `{items:[...]}` | 401 | all | instruments |
| Pricing Input | PUT | `/instruments/{id}/terms` | 계약·권리조건 저장 | `{terms,rights[]}` | `{saved, validation}` | 422 | VALUATOR+ | *_terms |
| Yield Curve | POST | `/curves` (multipart) | 커브 업로드 | `as_of,kind,grade,file` | `{upload_id,validation}` | 403,422 | CURVE_MANAGER | curve_* |
| Yield Curve | GET | `/curves?as_of&grade&kind` | 조회 | — | `{points:[...],source}` | 404 | all | curve_* |
| Yield Curve | POST | `/curves/permission-request` | 업로드 권한 요청 | — | `{request_id}` | — | VALUATOR+ | audit_logs |
| Bootstrapping | POST | `/curves/bootstrap` | zero/df/forward 생성 | `{par_rates[],interp}` | `{zero[],df[],forward[]}` | 422,409 | CURVE_MANAGER | curve_* |
| Asset | GET | `/assets?listed&q` | 기초자산 검색 | — | `{assets:[...]}` | 401 | all | underlying_assets |
| Asset | POST | `/assets/{id}/volatility` | 변동성 산출 | `{as_of,method,peers[]}` | `{vol,source}` | 422 | VALUATOR+ | volatility_data |
| Pricing Job | POST | `/instruments/{id}/price` | 평가 실행 | `{model,params_override}` | `{job_id,status,cached}` | 422,409 | VALUATOR+ | pricing_jobs |
| Pricing Job | GET | `/jobs/{job_id}` | 상태 | — | `{status,progress}` | 404 | all | pricing_jobs |
| Pricing Result | GET | `/jobs/{job_id}/result` | 결과 | — | `{components,parameters,repro}` | 404,409 | all | pricing_results |
| Pricing Result | GET | `/jobs/{job_id}/audit-data` | 계산검증 데이터 | — | `{cashflows,df,nodes,paths,exercise_log}` | 404,403 | AUDITOR+ | simulation_results |
| Pricing Result | POST | `/jobs/{job_id}/snapshot` | 결과 저장 | `{label}` | `{snapshot_id,link}` | 404 | VALUATOR+ | pricing_jobs |
| Sensitivity | POST | `/jobs/{job_id}/sensitivity` | 민감도 | `{variables[],shocks[]}` | `{grid,tornado}` | 404 | VALUATOR+ | pricing_jobs |
| Scenario | POST | `/jobs/{job_id}/scenarios` | 시나리오 | `{scenarios[]}` | `{comparison}` | 404 | VALUATOR+ | pricing_jobs |
| Report/File | POST | `/jobs/{job_id}/export.xlsx` | Excel | `{sheets[]}` | `{url}` | 404 | VALUATOR+ | valuation_reports |
| Report/File | POST | `/jobs/{job_id}/report.pdf` | PDF | `{sections[],notes}` | `{url}` | 404 | VALUATOR+ | valuation_reports |
| Report/File | POST | `/jobs/{job_id}/audit-pack` | zip | — | `{url}` | 404 | AUDITOR+ | valuation_reports |
| Admin | GET | `/admin/curve-permissions` | 권한 승인 큐 | — | `{requests:[...]}` | 403 | ORG_ADMIN | audit_logs |
| Admin | GET | `/admin/model-versions` | 모델 버전 | — | `{versions:[...]}` | 403 | ORG_ADMIN | model_versions |

> **상세 Request/Response 스키마**(전 필드)는 OpenAPI(`openapi.yaml`)로 별도 관리하고 Contract Test의 단일 소스로 삼는다(B 담당). 위 표는 인터페이스 합의용 요지.

---

## 10. Pricing Engine 개발명세 (Python)

### 10.1 권장 폴더 구조

```
pricing_engine/
├─ app/
│  ├─ main.py                  # FastAPI 진입, /price /health
│  ├─ router.py                # instrument_type → Calculator 매핑
│  ├─ context.py               # ValuationContext (정규화 입력 + 커브 스냅샷)
│  ├─ result.py                # PricingResult, ComponentBreakdown
│  ├─ reproducer.py            # input_hash, seed, model_version
│  ├─ core/
│  │  ├─ curve_service.py      # zero/df/forward, 보간, 신용가산
│  │  ├─ model_library/
│  │  │  ├─ lattice.py         # CRR 이항/삼항 backward induction
│  │  │  ├─ tf_lattice.py      # Tsiveriotis-Fernandes 이중할인
│  │  │  ├─ monte_carlo.py     # MC + 분산축소
│  │  │  ├─ lsmc.py            # Longstaff-Schwartz 행사경계
│  │  │  ├─ bsm.py             # Black-Scholes-Merton
│  │  │  ├─ barrier.py         # KI/KO
│  │  │  └─ dcf.py             # bond floor / host PV
│  │  ├─ rights_engine.py      # 전환/교환/콜·풋/매도청구권/리픽싱/희석
│  │  ├─ result_builder.py     # 구성요소 분해, RFR/YTM/Parity, 부호
│  │  └─ audit_writer.py       # 현금흐름·DF·노드·패스·행사로그 직렬화
│  ├─ calculators/
│  │  ├─ base.py               # BaseCalculator(ABC)
│  │  ├─ rcps.py  cps.py  cb.py  eb.py  bw.py
│  │  ├─ stock_option.py
│  │  └─ conditional_so.py
│  └─ validation/engine_rules.py   # 모형-조건 정합성(서버 검증과 분담)
└─ tests/
   ├─ golden/                  # 상품별 기대값 fixture
   └─ test_reproducibility.py
```

### 10.2 핵심 인터페이스

```python
# context.py — Backend가 보내는 정규화 입력 + 커브 스냅샷
@dataclass(frozen=True)
class ValuationContext:
    instrument_type: str          # "CB" ...
    as_of: date
    terms: dict                   # 계약조건(정규화)
    rights: dict                  # 권리조건(전환/상환/매도청구권/리픽싱/희석)
    market: dict                  # 주가·변동성·배당·기발행수
    curves: dict                  # {"risk_free": [...], "credit": [...]} 스냅샷
    model: str                    # 선택 모형
    seed: int
    input_hash: str
    model_version: str

# result.py
@dataclass
class PricingResult:
    total_fv: float
    per_unit: float | None
    components: dict[str, float]   # 보유자 관점 부호
    parameters: dict[str, float]   # rfr, ytm, credit_spread, parity ...
    audit: dict                    # cashflows/df/nodes/paths/exercise_log
    repro: dict                    # seed, input_hash, model_version

# calculators/base.py
class BaseCalculator(ABC):
    def __init__(self, ctx: ValuationContext, core: EngineCore): ...
    @abstractmethod
    def validate(self) -> list[Issue]: ...      # 엔진측 정합성
    @abstractmethod
    def price(self) -> PricingResult: ...        # core 모듈 조합
```

### 10.3 모듈별 구조 요지

| 모듈 | 핵심 구조 |
|---|---|
| **CurveService** | `df(t)`, `zero(t)`, `forward(t1,t2)`, `with_credit(spread)`; 보간 enum(linear/log_linear/monotone_convex 슬롯); 우선순위(수기>업로드>부트스트랩) |
| **ModelLibrary** | 각 모형은 `(context, curve)→value` 순수함수 형태. TF-Lattice는 `(B_E,B_D)` 이중상태 backward induction |
| **RightsEngine** | 노드/패스에서 `max(hold, convert, redeem, ...)` 행사판단 + 리픽싱 K궤적 추적 + 희석계수 `N/(N+M)` |
| **ResultBuilder** | 총가치→구성요소 분해, 부호 규약 적용, parity/ytm 산출 |
| **AuditWriter** | backward induction/패스에서 중간산출 수집→요약 직렬화 |
| **Reproducer** | seed 고정·소볼/안티테틱, input_hash 검증, model_version 태깅 |

### 10.4 Backend ↔ Engine JSON 스키마 (계약)

```jsonc
// 요청
{ "instrument_type":"CB", "as_of":"2024-06-26",
  "terms":{"face":10000,"coupon_rate":2.0,"freq_month":3,"maturity":"2026-09-13","guaranteed_yield":8.0},
  "rights":{"conversion":{"strike":..., "ratio":..., "refixing":{"start":...,"step_month":...,"floor":...,"direction":"DOWN"}},
            "redemption":{"call":{...},"put":{...}},
            "sale_claim":{"discount_type":"standalone_riskfree","strike_pct":...,"style":"AMERICAN"},
            "dilution":false},
  "market":{"spot":..., "vol":..., "dividend":..., "shares_outstanding":...},
  "curves":{"risk_free":[[0.25,3.41],...], "credit":[[0.25,13.45],...]},
  "model":"TF_LATTICE", "seed":20240101, "input_hash":"<sha256>", "model_version":"cb-1.0.0" }
// 응답 = PricingResult 직렬화
```

### 10.5 재현성·에러 처리

| 항목 | 방식 |
|---|---|
| input_hash | Backend가 정규화 JSON SHA-256 생성, Engine이 재검증·캐시키 |
| seed | 요청에 포함, MC/LSMC 전 경로 고정. 동일 seed→동일 결과 계약 |
| model_version | Calculator별 semver, 결과에 태깅, 변경 시 changelog |
| 에러 | `EngineError{code, stage, message, context_ref}` 표준. 검증오류(422)·계산실패(500)·수렴실패(MC 표준오차 초과→경고+결과) 구분 |

### 10.6 상품별 Calculator 명세

| Calculator | 대상 | 입력 Context | 사용 공통 모듈 | 핵심 계산 단계 | 결과 Schema | 검증 포인트 | 테스트 케이스 |
|---|---|---|---|---|---|---|---|
| **CBCalculator** | CB | terms+전환+콜/풋+매도청구권+리픽싱 | TF-Lattice·RightsEngine·LSMC·DCF | 트리구성→(B_E,B_D)이중할인 backward→콜/풋/전환 최적→리픽싱시 LSMC→매도청구권 독립평가 | bond/conversion/redemption/issuer_call/total + rfr/ytm/parity | bond floor 하한, 부호, 격자vsMC | 단순전환, 리픽싱, 콜+풋, 매도청구권 음수 |
| **RCPSCalculator** | RCPS | 우선주+전환+상환+(매도청구권)+리픽싱 | Lattice·RightsEngine·DCF | 상환가=보장수익률 누적원리금→노드 `max(전환,보유,상환)`→발행자콜 제약→우선배당 누적 | preferred/conversion/redemption/(call)/total/parity | 상환 floor, IRR 보장 | 누적배당, 콜 있음/없음 |
| **CPSCalculator** | CPS | 우선주+전환+리픽싱 | Lattice 또는 BSM+host PV | host PV(영구/만기)→전환옵션→리픽싱 | preferred/conversion/total/parity | BSvs격자 일치, host 독립검산 | 만기형/영구형 |
| **BWCalculator** | BW | 사채+신주인수권+희석+상환 | DCF·BSM워런트·희석 | 사채 DCF→워런트 BSM(희석 `N/(N+M)`)→분리형 분해 | bond/warrant/total/dilution | 워런트≥intrinsic, 희석 0~1 | 분리/비분리 |
| **EBCalculator** | EB | 사채+교환권(타사주)+상환 | DCF·교환옵션·기초 별도매핑 | 사채 DCF→교환옵션(대상주 주가·변동성)→희석 0 | bond/exchange/total | bond floor, 대상사≠발행사 | 교환옵션 intrinsic |
| **StockOptionCalculator** | SO | 부여·행사·변동성·배당·기대만기·퇴사율 | BSM·이항(조기행사)·MC | BSM(기대만기)→퇴사율 수량조정→비용 인식 스케줄 | unit/total/comp_cost/vesting_schedule | 단가≥intrinsic, 기대만기≤계약만기 | 정액/가속 배분 |
| **ConditionalSOCalculator** | 조건부SO | 시장/비시장·vesting·목표주가·성과지표 | MC·LSMC·Barrier·확률조정 | 시장조건 MC(FV 내재)→비시장 충족확률 수량 true-up→배리어 병행 | cond_value/prob/comp_cost/schedule/scenario | 시장=FV내재 vs 비시장=수량, MC 수렴 | 목표주가 KI/KO, 성과 미충족 |


---

## 11. 프론트엔드 화면 설계 구체화 (Next.js + TypeScript)

### 11.1 라우팅 구조 (App Router)

```
/(auth)/login  /(auth)/signup
/(app)
  /dashboard                        # 홈: 최근평가·진행Job·커브최신성
  /valuation/new                    # 상품 선택
  /valuation/[instrumentId]/wizard  # 7단계 Wizard
  /valuation/history                # 이력·비교·재계산
  /jobs/[jobId]/result              # 결과 대시보드
  /jobs/[jobId]/sensitivity
  /jobs/[jobId]/report              # 보고서 미리보기
  /parameters/curves                # 커브 업로드·이력·수기
  /parameters/bootstrap             # Bootstrapping
  /assets                           # 기초자산 마스터
  /admin                            # 조직·권한·모델버전
```

### 11.2 공통 레이아웃·상태관리

| 요소 | 구조 |
|---|---|
| 레이아웃 | 좌측 글로벌 내비 + 상단 조직/사용자 + 본문. Wizard에선 좌측이 **Summary/검증 패널**로 전환 |
| 상태관리 | 서버상태=TanStack Query(Job 폴링/캐시), 폼상태=React Hook Form + Zod, 전역=Zustand(현재 instrument·wizard step) |
| Wizard 상태 | step별 입력을 단일 `instrumentDraft`에 누적, 완성도%·검증상태 파생 |
| 디자인 | 무채색 베이스 + 강조 1색, 데이터그리드(TanStack Table), 숫자 우측정렬·천단위·음수 색+괄호 |

### 11.3 Dynamic Form 처리 방식

```ts
// 상품별 스키마(A 작성) → 렌더러가 폼 생성
type FieldSchema = {
  key: string; label: string; type: 'text'|'number'|'date'|'select'|'toggle';
  unit?: '%'|'원'|'주'; required?: boolean;
  showWhen?: { field: string; equals: any };     // 표시조건(권리조건 동적 노출)
  validate?: ValidationRule[];                    // 인라인 검증
};
type FormSchema = { product: InstrumentType; steps: { id:string; fields:FieldSchema[] }[] };
```
→ 7상품 폼을 개별 구현하지 않고 **스키마 데이터로 구동**(3명 생존 전략). 없는 권리조건은 `showWhen`으로 숨김.

### 11.4 화면별 명세

| 화면 | 목적 | 주요 컴포넌트 | 상태관리 | API | 유효성 | 사용자 액션 | 예외 처리 | 우선순위 | 담당 |
|---|---|---|---|---|---|---|---|---|---|
| 로그인/가입 | 인증·온보딩 | 폼·조직코드 | RHF+Zod | `/auth/*` | 이메일·조직코드 | 로그인/가입 | 401·중복 토스트 | 상 | C |
| 대시보드 | 진입점 | 최근평가·Job·커브알림 | Query | `/me`,`/jobs`,`/curves/freshness` | — | 새평가/이력 | 빈상태 안내 | 중 | C |
| 상품 선택 | 상품 진입 | 상품 카드 | Zustand | `/instruments` | — | 카드 선택→Wizard | — | 상 | C |
| **Wizard** | 입력 | Stepper+Summary+Dynamic Form | RHF+Zustand | `/instruments/{id}/terms` | 인라인+일괄 | 단계이동·저장·계산 | 검증오류 딥링크 | 상 | C(A스키마) |
| 기초자산 모달 | 매핑 | 검색·구분토글·미리보기 | Query | `/assets` | 상장1/비상장다수 | 검색·선택·적용 | 결과없음 | 중 | C |
| 커브 업로드 | 적재 | 권한상태·이력·만기표 | Query | `/curves` | 스키마검증 | 업로드·조회 | 권한없음·검증실패 | 중 | C |
| Bootstrapping | 커브생성 | par테이블·보간선택·차트 | Query | `/curves/bootstrap` | par 수치 | 생성·저장 | 음forward 경고 | 중 | C |
| **결과 대시보드** | 결과 | Summary·Component(부호색)·Parameters·로그 | Query | `/jobs/{id}/result` | — | 다운로드·스냅샷 | 미완료/실패 | 중 | C |
| 민감도 | 분석 | 토네이도·스파이더·변수토글 | Query | `/sensitivity` | — | 변수선택·실행 | 재계산 지연 | 중 | C |
| 시나리오 | 분석 | 비교테이블·차트 | Query | `/scenarios` | — | 프리셋·실행 | — | 중 | C |
| 이력 | 스냅샷 | 필터·diff·재계산 | Query | `/valuations` | — | 비교·재계산 | — | 중 | C |
| 보고서 미리보기 | PDF | 미리보기·섹션토글·주석 | Query | `/report.pdf` | — | 생성·다운로드 | 생성실패 | 하 | C |

### 11.5 Result Dashboard 컴포넌트 구조

```
<ResultDashboard>
 ├─ <ReproBadge/>          // 모델버전·시드·input_hash·커브출처
 ├─ <SummaryCard total perUnit pnl/>
 ├─ <ComponentBreakdown/>  // 항목·부호색(음수=적색괄호)·막대
 ├─ <KeyParameters/>       // RFR·YTM·CreditSpread·Vol·Div·Parity
 ├─ <TornadoChart/> <ScenarioTable/>
 └─ <DownloadBar/>         // Excel·PDF·Audit Pack
```

---

## 12. 테스트 전략

| 테스트 | 목적 | 대상 모듈 | 테스트 데이터 | 통과 기준 | 자동화 | 우선순위 | 담당 |
|---|---|---|---|---|---|---|---|
| Unit | 함수 정확성 | 전 모듈 | 합성 입력 | 분기 커버리지 목표치 | O | 상 | B/C |
| Integration | 모듈 결합 | API↔DB↔Engine | 테스트 DB | 시나리오 통과 | O | 상 | B |
| **Engine Regression** | 모형 정합 유지 | Calculator | golden fixture | 골든값과 오차 ≤ ε | O | **상** | A |
| **Golden Value** | 결과 정확성 | 상품 엔진 | Excel 수기 기준값 | CB·RCPS 우선 통과 | O | **상** | A |
| Curve Round-trip | 부트스트랩 정확 | CurveService | par 입력 | 재평가 par≈100 | O | 상 | A/B |
| Reproducibility | 재현성 | Job·Engine | 동일 입력 2회 | input_hash·결과 동일 | O | **상** | B |
| API Contract | 계약 준수 | REST | OpenAPI | 스키마 일치 | O | 상 | B |
| E2E | 사용자 흐름 | 전체 | 데모 데이터 | 7상품 입력~산출물 | △(Playwright) | 중 | C |
| Performance | 응답·처리 | Engine·Job | MC 대량 | 표준오차·지연 기준 | △ | 중 | B |
| Security | 격리·권한 | Auth·API | 타조직 토큰 | 격리 위반 0 | O | 중 | B |
| Report Output | 산출물 정확 | Report | 결과 fixture | 섹션·값 일치 | △ | 중 | A/B |

> **게이트 규칙**: 상품 Calculator는 **Golden Value 통과 전 "완료"로 표시 금지**. CB·RCPS는 문헌/Excel 대조를 머지 게이트로 둔다.

---

## 13. 모든 상품군 E2E 데모 시나리오

> 입력값은 데모용 예시(실제 시장값 아님). 각 상품 1케이스로 전 흐름 시연.

| 상품 | 데모 목적 | 입력값 예시 | 실행 단계 | 결과 핵심 값 | 산출물 | 검증 포인트 |
|---|---|---|---|---|---|---|
| **CB** | 전권리+TF격자 정점 | 발행 3,000,000,000 / 표면 2% / 지급 3M / 보장 8% / 만기 3Y / 전환가·리픽싱(상-하향)·매도청구권 | 기준일·커브→채권→전환(리픽싱)→상환→매도청구권→계산 | 채권/전환권/상환권/**매도청구권(음수)**/합계, RFR·YTM·Parity | Excel+PDF+zip | 매도청구권 음수, 격자vsMC 일치 |
| **RCPS** | 상환 IRR+메자닌 임팩트 | 발행가 20,619 / 우선배당 0.024% / 보장 5% / 만기 10Y / 전환·리픽싱 | 동일 골격(상환=보장 누적) | 우선주/전환권/상환권/합계/Parity | Excel+PDF | 상환 floor, 보장 IRR |
| **CPS** | 상환 제거 단순화 | RCPS에서 상환 비활성 | 전환만 | 우선주/전환권/Parity | Excel+PDF | 상환 입력 차단, host PV |
| **BW** | 사채+워런트 분리·희석 | 사채 + 워런트(행사가·수량) + 기발행수 | 사채→워런트(희석)→계산 | 채권/신주인수권/희석효과/합계 | Excel+PDF | 희석계수 0~1, 워런트≥intrinsic |
| **EB** | 타사주 교환·희석0 | 사채 + 교환대상사 종목·주가·변동성 | 사채→교환옵션(대상주)→계산 | 채권/교환권/합계 | Excel+PDF | 대상사≠발행사, 희석 미적용 |
| **Stock Option** | 1102 비용스케줄 | 부여수량·행사가·기대만기·퇴사율·가득 | 부여조건→BSM→비용배분 | 단가/총가치/보상원가/비용스케줄 | Excel+PDF | 기대만기≤계약, 단가≥intrinsic |
| **조건부 SO** | 시장/비시장 분리(정점) | 목표주가(시장)+매출/EBITDA(비시장)+vesting | 조건입력→MC(시장)→확률 true-up(비시장)→시나리오 | 조건부가치/충족확률/조건별 스케줄/시나리오별 | Excel+PDF | 시장=FV내재, 비시장=수량, MC 수렴 |

> 데모 하이라이트: CB에서 **같은 입력 2회 계산→input_hash·결과 동일**(재현성), **Audit Pack zip** 내보내기, **잘못된 입력(만기<발행일) 차단**을 함께 시연.


---

## 14. GitHub / 협업 운영 방식

> 일정 없이 **규칙·원칙** 중심. 3명이라 무거운 프로세스는 독이므로 "가볍지만 계약은 엄격" 기조.

### 14.1 Repository 구조 (모노레포)

```
fairvalue-engine/
├─ backend/              # Kotlin/Spring (B)
├─ pricing-engine/       # Python/FastAPI (A+B)
├─ frontend/             # Next.js/TS (C)
├─ contracts/            # ★3대 계약 단일소스: openapi.yaml, valuation-context.schema.json, form-schema/*.json
├─ docs/                 # PRD, 본 실행계획서, ADR(의사결정기록), 회의록
├─ golden-values/        # 상품별 기대값 fixture (A)
├─ infra/                # docker-compose, env 템플릿
└─ .github/              # PR/Issue 템플릿, CI 워크플로
```

> 모노레포 채택 이유: 3명이 계약(contracts)을 공유·동기화해야 하므로 멀티레포의 버전 표류 비용이 더 크다.

### 14.2 Branch 전략

| 브랜치 | 용도 | 규칙 |
|---|---|---|
| `main` | 항상 배포 가능 | 직접 push 금지, PR만 |
| `develop` | 통합 | feature 머지 대상 |
| `feat/<scope>-<요약>` | 기능 | 예: `feat/engine-tf-lattice` |
| `fix/<요약>` | 버그 | — |
| `docs/<요약>` | 문서 | 리뷰 1인 |

> 소규모라 GitHub Flow + develop 정도면 충분. git-flow 풀셋은 과함.

### 14.3 Commit / PR / Review 규칙

| 항목 | 규칙 |
|---|---|
| Commit | Conventional Commits: `feat(engine): TF lattice backward induction` / `fix(api): 커브 매핑 폴백` / 타입 `feat·fix·docs·test·refactor·chore` |
| PR 크기 | 가능한 작게(리뷰 30분 내). 거대 PR 금지 |
| PR 템플릿 | 변경요약 / 관련이슈 / 테스트방법 / **계약변경 여부(contracts 영향)** / 스크린샷(FE) |
| Review | 최소 1인 승인. **contracts 변경 PR은 영향받는 전원 승인 필수** |
| Merge | Squash merge로 히스토리 단순화 |
| CI 게이트 | lint + unit + (해당 시) golden/contract test 통과해야 머지 |

### 14.4 Issue / Milestone / 문서

| 항목 | 규칙 |
|---|---|
| Issue 템플릿 | `[기능]`, `[버그]`, `[엔진검증]`(골든값 대조), `[계약변경]` 4종. 라벨: `area:backend/frontend/engine/contracts`, `prio:high/mid/low` |
| Milestone | Phase 0~7에 매핑(§3). 일정 아닌 **완료기준 묶음**으로 운용 |
| 문서 관리 | PRD·실행계획·ADR은 `docs/`에 PR로 갱신. 중요한 기술 결정은 ADR 1장씩 |
| 계약 변경 | `contracts/` 변경은 반드시 Issue→PR→전원리뷰. 구두 변경 금지 |

### 14.5 협업 리듬 (일정표 아님)

| 항목 | 방식 |
|---|---|
| Daily Check-in | 비동기 텍스트 3줄: 어제/오늘/블로커. 블로커만 짧게 동기 논의 |
| 주간 회고 | 완료기준 충족 항목 점검 + 계약 표류·엔진 검증 리스크 리뷰 + 다음 우선순위 재정렬 |
| 의사결정 | 기술 분기점은 ADR로 기록(대안·결정·이유) |
| 배포 브랜치 | `main`=배포. 데모 직전 `release/demo` 태그로 고정(시연 재현 보장) |

---

## 15. 포트폴리오 및 발표자료 구성

### 15.1 README 목차

```
1. 프로젝트 소개 (한 줄 + 핵심 가치)
2. 문제 정의 (엑셀 수기 평가의 재현성·감사 한계)
3. 핵심 기능 (7상품 E2E·재현성·Audit Pack·커브 거버넌스)
4. 시스템 아키텍처 (다이어그램)
5. Pricing Engine (모형 매트릭스·재현성)
6. 지원 상품군 (RCPS·CPS·CB·EB·BW·SO·조건부SO)
7. E2E 데모 흐름 (입력~보고서, GIF/영상)
8. DB 설계 요약
9. API 설계 요약
10. 기술 스택
11. 팀원 역할
12. 기술적 어려움과 해결
13. 한계 및 향후 개선
14. 실행 방법 (docker-compose up)
```

### 15.2 핵심 문구·서사

| 항목 | 내용 |
|---|---|
| 소개 문구 | "복합금융상품 공정가치를 **재현 가능하고 감사 추적 가능하게** 산출하는 B2B 평가 플랫폼" |
| 문제 정의 | 담당자별 엑셀 산재→재현 불가·검증 난이. 리픽싱·상환·매도청구권이 수식에 하드코딩 |
| 차별점 | 정확성을 *증명 가능하게* 만든 점(골든값·교차검증·재현성·Audit Pack) |
| 서사 한 줄 | "엑셀 평가의 재현성·감사 문제를 플랫폼으로 해결" |

### 15.3 아키텍처 다이어그램 설명 (요지)

`[Next.js] ⇄ [Spring API: 인증·상태·Job·진실의원천] ⇄ [Job Queue] ⇄ [Python Engine: stateless 계산] ⇄ [PostgreSQL/S3]`. **Backend=진실의 원천, Engine=순수 계산, 동일 input_hash는 캐시로 즉시 반환**임을 강조.

### 15.4 "기술적 어려움과 해결" 어필 포인트

| 어려움 | 해결 | 어필 |
|---|---|---|
| T-F 이중할인 정확도 | 문헌/Excel 골든값 대조 + 격자vsMC 교차검증 | 금융공학 엔지니어링 |
| 리픽싱 경로의존 | 격자→LSMC 전환 규칙 + 패스별 K궤적 추적 | 모형 선택 판단력 |
| MC 재현성 | 시드 고정·소볼·input_hash 캐시 | 상용 재현성 설계 |
| 3명 7상품 | Engine Core + 얇은 Calculator + 스키마 구동 폼 | 아키텍처 설계력 |
| 1102 시장/비시장 | FV내재 vs 수량 true-up 분리 | 회계 정합 인지 |

### 15.5 시연 영상·발표자료 구성

| 영상 구성 | 발표자료 목차 |
|---|---|
| ① 문제(엑셀 한계) 30초 | 1. 문제 정의 |
| ② CB 입력~계산~결과 대시보드 | 2. 솔루션 개요·지원 상품군 |
| ③ 재현성(2회 계산 동일) | 3. 시스템 아키텍처 |
| ④ 잘못된 입력 차단(검증) | 4. Pricing Engine·모형 매트릭스 |
| ⑤ Excel+PDF+Audit Pack | 5. 7상품 E2E 데모 |
| ⑥ 7상품 빠른 순회 | 6. 재현성·검증·Audit |
| ⑦ 커브 거버넌스 | 7. 기술적 도전과 해결 |
| (3~5분) | 8. 팀 역할·한계·향후 |

---

## 16. 팀원 3명이 실제로 바로 착수해야 할 첫 작업 10개

> 의존관계상 **계약 동결 → 골격 → 검증 기준**이 최우선. 아래 10개는 Phase 0~1 + 일부 Phase 2의 선행 핵심.

| # | 작업 | 담당 | 선행 | 완료 기준 |
|---|---|---|---|---|
| 1 | **3대 계약 v0 동결**: `valuation-context.schema.json`, `openapi.yaml` 골격, CB용 `form-schema/cb.json` | A+B+C | — | `contracts/`에 3파일 머지, 전원 리뷰 승인 |
| 2 | **모노레포 + CI 셋업**: backend/pricing-engine/frontend/contracts/docs 구조 + lint·test 워크플로 | B | — | PR 시 CI 자동 실행 |
| 3 | **CB Golden Value 산출**: Excel 수기 평가로 채권/전환/상환/매도청구권 기대값 fixture 작성 | A | 1 | `golden-values/cb_case1.json` 확정 |
| 4 | **Auth/Org/RBAC + 조직격리**: 로그인·5역할·조직 필터 | B | 2 | 타조직 토큰 접근 차단 테스트 통과 |
| 5 | **MC/LSMC 재현성 PoC**: 시드 고정→동일 결과 + QuantLib/CRR 격자 1샷 | A(주)+B | 2 | 동일 시드 2회 결과 일치 |
| 6 | **Form Renderer PoC**: `cb.json` 스키마→Wizard 폼 자동 렌더(표시조건·인라인검증) | C | 1 | CB 폼이 스키마로 렌더, 권리조건 토글 동작 |
| 7 | **Instrument CRUD + Job 골격**: 상품 생성→terms 저장→Job enqueue→상태조회(엔진 더미) | B | 4 | placeholder 결과까지 화면 확인 |
| 8 | **CurveService + 커브 업로드 골격**: 원본 스키마 검증·(기준일,등급) 자동매핑·df/zero 인터페이스 | B(A검증) | 4 | 업로드 커브가 평가 컨텍스트에 매핑 |
| 9 | **CB Calculator 1차 + TF-Lattice**: 코어 조합으로 CB 실제 계산, #3 골든값 대조 | A(주)+B | 5,8 | CB가 골든값과 허용오차 내 일치 |
| 10 | **CB 수직 슬라이스 관통**: #6 폼→#7 Job→#9 엔진→결과 대시보드→Excel 1건 | A+B+C | 6,7,9 | CB 입력~Excel 다운로드 E2E 1회 성공 |

> 이 10개를 마치면 **CB 1상품이 입력~산출물까지 실제로 돌고, 전 계약·전 코어·검증 기준이 검증**된다. 이후 RCPS→CPS→BW→EB→SO→조건부SO는 동일 골격에 Calculator·스키마만 확장하므로 속도가 붙는다(§4 순서).

---

*문서 끝. 본 실행계획은 첨부 PRD를 구현 단위로 전환한 것이며, 모든 상품군 end-to-end 지원을 최종 목표로 유지한다. 3명 팀 제약상 일부 Calculator는 검증된 단순 구조로 먼저 E2E를 닫되, 정밀도 업그레이드 슬롯을 남기는 것을 원칙으로 한다.*
