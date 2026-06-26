# FairValue Engine — 3대 계약 파일화 전 최종 검수 (Pre-Freeze Review)

> **목적**: 「3대 핵심 계약 명세」를 그대로 `.ts` / `.schema.json` / `.py` / `openapi.yaml`로 옮겨도 되는지 판정.
> **결론(요약)**: 설계 골격은 견고하나 **그대로 파일화 시 깨지는 블로킹 이슈 3건** + 다수의 보완점이 있음. 아래 3건은 freeze 전 **반드시 수정**해야 한다.

## ⛔ 즉시 수정 필요 (Blocking) — 이것부터 보고

| # | 위치 | 문제 | 영향 |
|---|---|---|---|
| **B-1** | §4.8 RCPS PricingResult 예시 | **components 합 ≠ total.** `20619.00 + 4120.30 + 1300.25 = 26039.55` 인데 `total_fair_value`는 `24180.55`. §4.10 불변식(Σcomponents = total) 위반 | 이 예시를 Golden/테스트 픽스처로 쓰면 합계 검증이 **무조건 실패**. 합계 불변식 신뢰 붕괴 |
| **B-2** | §2.4/§2.5 폼 `bind: curves.risk_free_ref` / `credit_ref` | 이 경로가 ValuationContext 스키마에 **존재하지 않음**(`curves`는 `additionalProperties:false`, `risk_free_curve`만 존재). §3.11에 "ref→포인트 치환"이 서술돼 있으나 **스키마·타입에 형식화 안 됨** | form bind를 context 스키마로 검증하면 **전 상품 실패**. §1 "모든 field는 1:1 매핑" 불변식과 충돌 |
| **B-3** | §5 커버리지표 vs §4 스키마 | component 명칭 불일치: §5는 `bond`·`dilution` 등, §4 스키마는 `bond_value`·`dilution_effect`. **같은 값에 두 이름** | §5를 보고 구현하면 §4 스키마와 키가 어긋나 직렬화·DB 저장 불일치 |

---

## 1. 3대 계약 간 정합성 검토

| 검토 영역 | 확인 결과 | 문제 여부 | 수정 필요 여부 | 권장 수정안 |
|---|---|---|---|---|
| bind 경로 ↔ ValuationContext 경로 (일반) | terms/rights/market/model/seed/options 경로는 정확히 일치(예: `terms.issue_date`, `rights.refixing.floor`, `rights.redemption.put.enabled` 모두 스키마에 존재) | 정상 | 불필요 | — |
| **커브 selector bind** | 폼은 `curves.risk_free_ref`·`credit_ref`로 bind하나 최종 context엔 `risk_free_curve`(포인트 배열)만 존재. 치환은 §3.11에 서술되나 미형식화 | **문제(B-2)** | **필요** | ① `FieldSchema`에 `resolve?: { to: string; kind: 'curve'\|'asset' }` 마커 추가 ② **rawForm 경로 ↔ 최종 context 경로 매핑표** 명문화 ③ §1 불변식 문구를 "rawForm 경로에 1:1 bind, 일부는 Normalizer가 최종 경로로 resolve"로 수정 |
| **assetSearch bind** | `market.asset_id`는 최종 경로에 존재(정상). 단 §3.11이 asset_id로 spot/vol/dividend를 채운다고 했는데 폼에 spot/vol/dividend **수동 필드도 존재** → 자동채움 vs 수동입력 **우선순위 미정** | 문제 | 필요 | 우선순위 규칙 명문화: "수동 입력값이 있으면 override, 없으면 asset 마스터 자동채움". `W204`(수기 변동성)와 연계 |
| **parity bind** | 폼이 `rights.conversion.parity`(computed)로 bind. parity는 **결과 key_parameter(출력)** 이며 conversion $def엔 parity 없음(TS `Conversion`에도 없음). 입력에 박으면 input_hash에 파생값 혼입 | 문제 | 필요 | parity는 **표시 전용**(computed, `bind` 제거)으로. 엔진이 재계산해 결과에만 노출 |
| CB·RCPS 폼 key/bind ↔ context 예시 일관성 | 커브 ref 제외하면 일관(예: CB `conv_price→rights.conversion.strike`, RCPS `dividend_rate→terms.coupon_rate`) | 부분 문제(커브 ref만) | 부분 필요 | B-2 해결 시 동시 해소 |
| terms 커버리지(전 상품) | 채권형(issue_amount/face/coupon)·우선주형(issue_price/dividend_cumulative/host_type)·SO형(grant_date/quantity/exercise_price/expected_term)·tranche 모두 포함 | 정상 | 불필요(미세) | `tranche` item 형상이 `{}`로 느슨 → v0.1 허용, 형상 TBD 주석 |
| rights 커버리지(전 상품) | conversion/exchange/warrant/redemption(put·call)/issuer_call/sale_claim/refixing/dilution/vesting/perf·market_condition 전부 정의 | 정상 | 불필요 | — |
| market 커버리지 | asset_id/spot/vol/dividend/shares_outstanding/peer/data_source/as_of 포함. **EB 의미 모호**(option 기초=교환대상인데 market.* 가 발행사인지 대상사인지 불명) | 부분 문제 | 필요 | EB는 `market.{spot,volatility}`=교환대상사 값으로 사용함을 명시. `rights.exchange.target_asset_id`와 일치 규칙 |
| curves 커버리지 | risk_free/credit/source/version/interpolation/fallback 포함. credit nullable이라 SO/CSO도 커버 | 정상 | 불필요 | (선택) instrument_type별 credit 필수여부는 엔진 validate에서 강제 |
| model/options 커버리지 | enum에 TF_LATTICE/LATTICE/TRINOMIAL/MC/LSMC/BSM/DCF. **TRINOMIAL은 라우팅(§7)·폼 어디에도 없음** | 경미 | 선택 | TRINOMIAL 제거 또는 "예약(미사용)" 주석 |
| **components 전 상품 표현력** | 7상품 결과를 모두 표현 가능(host/option/redemption/issuer_call/sale_claim/warrant/exchange/SO/CSO/dilution). 단 §5와 **이름 불일치(B-3)** | 문제(B-3) | 필요 | §5를 §4 정식명(`*_value`, `dilution_effect`)으로 통일 |
| components.total_fair_value 중복 | components 내부 total + 최상위 total 둘 다 존재 → "이게 합인가 항목인가" 모호 | 경미 | 선택 | components.total은 **합의 echo**임을 주석화하거나 제거. 검증은 "Σ(나머지)=최상위 total" |
| **components 합 = total 불변식** | CB 예시는 합 일치(167634.23 ✓). **RCPS 예시는 불일치(B-1)** | 문제(B-1) | 필요 | RCPS 예시 수치 보정(예: preferred 18760.00 또는 total 26039.55로 일치) + CI에 합계 검증 추가 |
| audit_data 충분성(보고서·Audit Pack) | cashflow/df/curve_snapshot/node·path_summary/exercise_log/calculation_trace/input_snapshot_hash 포함 → 보고서 13섹션·Audit Pack 커버 | 정상 | 불필요(미세) | 각 jsonb 항목 **요약 스키마**(컬럼명) v0.2에서 구체화 |
| input_hash 포함/제외 규칙 | seed·model_version 포함(동일 hash⇒동일 결과 성립), instrument_id 제외(캐시 공유), metadata/source/org 제외 → 재현성 관점 적절 | 정상 | 불필요 | **양 언어 동일 구현** 필요(아래 Freeze C-7) |
| input_hash 생성 규칙 | 키정렬·공백제거·10자리 반올림·ISO 날짜·배열순서 보존·SHA-256 → 합리적 | 정상 | 보완 | `round_floats` 미정의 함수 → 구현 명세 필요. NaN/None/빈 권리조건 정규화 규칙 명문화 |

---

## 2. 파일화 전 보완사항

| 파일 | 현재 명세로 충분한 부분 | 보완 필요한 부분 | 보완 이유 | 우선순위 |
|---|---|---|---|---|
| `shared/schemas/form-schema.ts` | FieldType·Condition·ValidationRule·Step·Form 타입 완비 | `resolve` 마커(커브/자산 ref) 추가, `InstrumentType`를 여기서 선언하지 말고 `instrument-types.ts`에서 import | B-2 해소, 타입 중복 선언 방지 | 상 |
| `shared/schemas/valuation-context.schema.json` | draft 2020-12 구조·$defs 권리조건 완비 | (1) **draft 스키마 분리**(input_hash·model_version·resolved curve 미요구) (2) curve ref 미허용 명시 또는 별도 rawForm 스키마 (3) instrument_type별 conditional required(if/then) 선택 | 폼 단계엔 hash·resolved curve가 아직 없음 → 동일 스키마로 검증 불가 | 상 |
| `shared/schemas/valuation-context.ts` | interface 전부 정의, 타입 일관 | parity를 Conversion에서 제외(표시전용 결정 반영), `InstrumentType` import 일원화 | parity 입력 혼입 방지(D) | 중 |
| `shared/schemas/pricing-result.schema.json` | components·key_parameters·audit·repro·issue 정의 | key_parameters에 `model_name`·`model_version` required 추가, `warnings`/`errors` required+default[] 정합 | TS는 required인데 schema는 아님(불일치 H,G) | 중 |
| `shared/schemas/pricing-result.ts` | interface 완비, Issue 정의 | schema와 required 정합(위와 동일), components.total 주석 | 양측 동기화 | 중 |
| `shared/schemas/instrument-types.ts` | (명세에 별도 파일 없음 — 산재) | `InstrumentType`·`ModelName`·`ExerciseStyle`·enum 상수를 **단일 소스**로 신설, 나머지 파일이 import | 동일 enum 3중 선언 방지, drift 차단 | 상 |
| `shared/schemas/validation-rules.ts` | §6 rule 카탈로그(20종)·severity 정의 | 각 rule의 **params 형상**(예: `refixingFloorCheck.initField`, `dateOrder.before/after`, `dependencyRequired.requires`) 인터페이스화 + Zod 변환 매핑 | 파일 생성 시 params 키가 없으면 Zod 변환 불가 | 상 |
| `pricing-engine/app/context.py` | Pydantic 모델 골격 존재 | `model_config = ConfigDict(extra="forbid")` 명시, `round_floats`·`input_hash` 재검증 함수 포함, curve는 resolved 형태만 수용 | §3.12 재검증·B-2와 일치 | 상 |
| `pricing-engine/app/result.py` | Pydantic 모델 골격 존재 | components 합=total 검증 `@model_validator`, 부호 규칙(§4.10) 검증, Issue 코드 enum화 | 불변식 런타임 강제 | 중 |
| `backend/src/main/resources/openapi.yaml` | (명세에 본문 없음) | **요청/응답 본문 정의 부재**: `POST /instruments/{id}/price` 요청=rawForm/trigger인지, 응답=PricingResult인지, `$ref`로 공유 스키마 연결 | API 계약이 비어 있으면 BE/FE 연동 불가 | 상 |

---

## 3. 7개 상품군 커버리지 점검

| 상품 | Form Schema 커버리지 | ValuationContext 커버리지 | PricingResult 커버리지 | 누락 가능 필드 | 보완 필요 여부 |
|---|---|---|---|---|---|
| **RCPS** | 전체(transition·refixing·redemption.put·issuer_call) — 예시 완비 | terms(issue_price·dividend_cumulative·guaranteed_yield)·rights 완비 | preferred/conversion/redemption/issuer_call | sale_claim 스텝이 예시엔 없음(선택권리라 OK) | **예시 합계 버그(B-1)만 수정** |
| **CPS** | 요약표만(host_type·conversion·refixing) | host_type(dated/perpetual) 존재 | preferred/conversion | 영구형 시 maturity null 처리 규칙 명시 | 폼 풀버전 생성 필요(다음 단계) |
| **CB** | 전체 예시 완비(전권리) | terms·rights·curves(credit) 완비 | bond/conversion/redemption/issuer_call/sale_claim/dilution | — | 커브 ref(B-2)만 |
| **EB** | 요약표(exchange.target_asset_id·redemption) | exchange $def 존재 | bond/exchange/redemption | **market.* 가 교환대상사 값임이 불명** | market 의미 명시 필요 |
| **BW** | 요약표(warrant·dilution·redemption) | warrant·dilution $def 존재 | bond/warrant/dilution/redemption | 분리형(separable) 시 채권·워런트 분해 표현 규칙 | 폼 풀버전 + 분해규칙 |
| **Stock Option** | 요약표(vesting) + 예시 결과 | terms(grant_date/quantity/exercise_price/expected_term)·vesting | stock_option | 비용 인식 스케줄을 audit_data로만 표현 → 결과 표준 위치 합의 | 비용스케줄 위치 명시 |
| **Conditional SO** | 요약표(vesting·market·performance·tranche) | perf·market_condition·tranche 존재 | conditional_option | tranche item 형상 미정, true-up·배리어 수치 위치 | tranche 형상·CSO 결과 세분화(다음 단계) |

> 전 상품 공통계약 골격은 충분. **CPS·EB·BW·SO·CSO는 폼 풀버전이 아직 요약표 수준** → freeze 후 첫 작업(다음 단계 프롬프트 ①)으로 채운다. 계약 구조 자체는 이들을 모두 수용 가능.

---

## 4. 구현 전 Freeze Checklist (v0.1)

| 체크 항목 | 설명 | 통과 기준 | 실패 시 영향 | 우선순위 |
|---|---|---|---|---|
| C-1 RCPS 예시 합계 정합 | §4.8 components 합=total 보정 | Σ(components) == total_fair_value (B-1) | 픽스처·합계검증 전부 실패 | 상 |
| C-2 커브/자산 ref 형식화 | rawForm 경로 ↔ 최종 context 경로 매핑 + `resolve` 마커 | 폼 bind 전수가 매핑표에 존재(미해소 0건, B-2) | form↔context 검증 전 상품 실패 | 상 |
| C-3 component 명칭 통일 | §5 ↔ §4 정식명 일치(B-3) | 단일 명칭 사전 1벌 | DB/직렬화 키 불일치 | 상 |
| C-4 draft vs final 스키마 분리 | 폼단계(hash·resolved 없음) 검증용 draft 스키마 | rawForm이 draft 스키마 통과, 최종이 full 스키마 통과 | 폼 데이터가 full 스키마에서 항상 실패 | 상 |
| C-5 enum 단일 소스화 | InstrumentType·ModelName·ExerciseStyle을 instrument-types.ts로 | 타 파일은 import만(중복 선언 0) | enum drift, 7상품 분기 오류 | 상 |
| C-6 validation params 형상 확정 | 20종 rule의 params 키 인터페이스화 | 각 rule param 타입 정의 + Zod 매핑 존재 | Zod 변환기 구현 불가 | 상 |
| C-7 input_hash 양측 동일 | Kotlin/Python 동일 정규화·직렬화 | 공유 테스트벡터 5케이스 양측 해시 동일 | 캐시 오적중·재현성 붕괴 | 상 |
| C-8 합계·부호 런타임 검증 | result.py에 Σ=total·부호규칙 validator | 위반 시 E-코드 발생 | 잘못된 결과 무검출 통과 | 중 |
| C-9 openapi I/O 정의 | price·result·curve API 본문을 $ref로 연결 | 요청/응답 스키마 명시 | BE↔FE 연동 불가 | 상 |
| C-10 asset 자동채움 우선순위 | 수동 vs asset 마스터 override 규칙 | 규칙 문서화 + 테스트 | spot/vol 출처 모호 → 결과 비결정 | 중 |
| C-11 parity 입력 제거 | parity는 결과 전용(입력 bind 제거) | input_hash에 parity 미포함 | 파생값이 해시 오염 | 중 |
| C-12 EB market 의미 명시 | market.* = 교환대상사 규칙 | 문서 + EB 예시 1건 | EB 계산 기초 오류 | 중 |
| C-13 errors/warnings 정합 | schema·TS·Pydantic required·default[] 일치 | 3측 동일 | null/누락 처리 분기 | 하 |
| C-14 코드체계 동결 | E/W 코드표를 append-only enum으로 | result.py Issue code enum 일치 | 코드 난립 | 하 |

---

## 5. Cowork 작업 지시 전 정리

> freeze 수정(§4 체크리스트) 반영 후, Cowork가 파일을 생성하도록 **의존관계 순**으로 작업 단위를 쪼갠다.

| 작업 단위 | 생성/수정할 파일 | 선행 조건 | 완료 기준 | 주의사항 |
|---|---|---|---|---|
| W1. 공통 enum 단일소스 | `shared/schemas/instrument-types.ts` | C-5 | InstrumentType·ModelName·ExerciseStyle·코드enum export | 타 파일은 반드시 import(재선언 금지) |
| W2. Form Schema 타입 | `shared/schemas/form-schema.ts` | W1, C-2 | `resolve` 마커 포함 타입 컴파일 통과 | InstrumentType은 W1에서 import |
| W3. Validation 카탈로그 | `shared/schemas/validation-rules.ts` | W1, C-6 | 20종 rule + params 타입 + severity | 폼/서버 공용. Zod 매핑은 별도 작업 |
| W4. ValuationContext 스키마/타입 | `valuation-context.schema.json`(full), `valuation-context.draft.schema.json`, `valuation-context.ts` | W1, C-4, C-11 | full/draft 양 스키마 + TS 동기화. parity 입력 제거 | curve는 resolved만 수용. draft는 hash 미요구 |
| W5. PricingResult 스키마/타입 | `pricing-result.schema.json`, `pricing-result.ts` | W1, C-3, C-13 | components 명칭 통일, required 3측 일치 | components.total은 echo 주석 |
| W6. Engine Pydantic | `pricing-engine/app/context.py`, `result.py` | W4, W5, C-8 | extra="forbid", Σ=total·부호 validator, hash 재검증 | round_floats·input_hash를 backend와 동일 구현 |
| W7. input_hash 공용 구현 | `pricing-engine/app/reproducer.py`, `backend/.../InputHash.kt`, `shared/schemas/hash-test-vectors.json` | W4, C-7 | 5케이스 양측 동일 해시(CI 교차검증) | 부동소수·날짜·빈 권리조건 엣지케이스 포함 |
| W8. 상품 폼 스키마 | `frontend/src/forms/productSchemas/{cb,rcps,cps,eb,bw,so,cso}.json` | W2, W3, C-2 | 7개 모두 bind가 매핑표와 100% 일치 | CB/RCPS는 예시 이식, 나머지 5종 신규 |
| W9. OpenAPI 계약 | `backend/src/main/resources/openapi.yaml` | W4, W5, C-9 | price/result/curve I/O가 공유 스키마 $ref | 요청=trigger params(서버가 context 조립), 응답=PricingResult |
| W10. bind 일치 검증기 | `shared/scripts/verify-bind.ts` | W2, W4, W8 | 전 폼 bind ↔ context 경로 자동대조 통과 | CI 게이트로 등록(미해소 0건) |

### Cowork에 바로 보낼 다음 프롬프트

> **"첨부한 「3대 계약 최종 검수」 문서의 §4 Freeze Checklist와 §5 작업 단위(W1~W10)를 그대로 실행해, 실제 파일을 생성·수정해줘. 먼저 W1~W7(공통 enum·Form Schema 타입·Validation 카탈로그·ValuationContext full+draft 스키마·PricingResult 스키마·Engine Pydantic·input_hash 공용 구현+테스트벡터)을 만들고, 다음 제약을 반드시 반영해줘: (1) InstrumentType·ModelName·ExerciseStyle은 `instrument-types.ts` 단일 소스에서만 선언하고 나머지는 import한다, (2) 폼의 커브/자산 selector는 `resolve` 마커를 갖고 rawForm 경로(`curves.*_ref`)는 draft 스키마에서만 허용하며 최종 context엔 resolved 포인트만 둔다, (3) parity는 결과 전용으로 ValuationContext 입력에서 제거한다, (4) PricingResult는 components 합=total과 §4.10 부호규칙을 `@model_validator`로 런타임 강제하고 §4.8 RCPS 예시 합계를 정합되게 보정한다, (5) input_hash는 Kotlin/Python 양측이 동일 정규화·직렬화를 쓰고 `hash-test-vectors.json` 5케이스로 CI 교차검증한다. 각 파일 생성 후 §5 완료 기준을 만족하는지 체크하고, 통과 못 한 항목은 표로 보고해줘. 이어서 W8(7개 상품 폼 스키마)·W9(openapi.yaml)·W10(bind 일치 검증기)을 만들어줘."**

---

*검수 종료. 블로킹 3건(B-1 RCPS 합계, B-2 커브 ref 형식화, B-3 component 명칭)을 먼저 닫고 §4 체크리스트를 통과시키면, 본 3대 계약은 v0.1로 freeze 가능하다.*
