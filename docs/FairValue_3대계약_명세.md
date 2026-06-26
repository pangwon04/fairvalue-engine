# FairValue Engine — 3대 핵심 계약 명세 (Contract Spec v0)

> **문서 성격**: 코드 착수 전 **동결(freeze)** 대상인 3대 개발 계약의 확정 기준 문서
> **대상**: Frontend(폼/검증) · Backend(정규화/저장/API) · Pricing Engine(계산)
> **계약 3종**: ① Dynamic Form Schema ② ValuationContext JSON Schema ③ PricingResult / Result Schema
> **전제**: 7개 상품(RCPS·CPS·CB·EB·BW·SO·CSO) **전부 동일 공통 계약** 위에서 동작. 상품 차이는 **Form Schema + Calculator 라우팅**으로만 표현(상품 제외·축소 없음).
> **산출 목표**: 본 문서의 타입/스키마/예시를 그대로 `.ts`, `.schema.json`, `.py`, `openapi.yaml`로 이식 가능.

> **버전·식별자 규약**: 본 문서의 모든 계약은 semver(`MAJOR.MINOR.PATCH`)를 가진다. `InstrumentType` 코드값은 전 계약 공통: `RCPS | CPS | CB | EB | BW | SO | CSO`.

---

## 1. 3대 계약의 역할 정의

### 1.1 책임 분리표

| 계약명 | 사용 주체 | 책임 범위 | 포함해야 할 데이터 | 포함하면 안 되는 데이터 | 후속 모듈과의 연결 |
|---|---|---|---|---|---|
| **Dynamic Form Schema** | Frontend(렌더·검증), Backend(검증 규칙 참조) | 상품별 입력 화면 구조·필드·표시조건·검증규칙 정의 | step/field 정의, 타입, 단위, showWhen, validation rule, `bind`(컨텍스트 경로) | 계산 로직, 모형 수식, 결과 구조, 커브 실제값 | 각 field의 `bind`가 **ValuationContext 경로**로 매핑됨 |
| **ValuationContext** | Backend(생성·정규화), Engine(소비·재검증) | Engine에 넘기는 **정규화된 평가 입력 + 커브 스냅샷** | instrument_type, terms, rights, market, curves, model, seed, input_hash, model_version, options, metadata | UI 상태, 라벨/문구, 검증 메시지, 결과값 | Engine **Calculator 라우팅**의 입력. `input_hash`가 재현성·캐시 키 |
| **PricingResult** | Engine(반환), Backend(저장), Frontend(표시), Report(생성) | 계산 결과 + 파라미터 + 감사데이터 + 재현성 메타 | total/per_unit, components, key_parameters, audit_data, reproducibility, warnings/errors | 원본 폼 스키마, UI 컴포넌트 정보 | **DB 3테이블 분리저장** + 대시보드 + 보고서 입력 |

### 1.2 데이터 흐름 (계약 간 경계)

```
[Form Schema] --구동--> 입력 폼 --검증(rule)--> rawForm(JSON)
      │ field.bind                                   │
      ▼                                              ▼
Backend: rawForm → 정규화 → [ValuationContext] --input_hash--> Pricing Job
                                   │
                                   ▼  HTTP POST /price
                            Engine: 라우팅(instrument_type→Calculator) → 계산
                                   │
                                   ▼
                            [PricingResult] --반환--> Backend
                                   │ 분리저장
                                   ▼
            pricing_results · pricing_parameters · simulation_results
                                   │
                                   ▼
                        Result Dashboard · Report/PDF · Audit Pack
```

**핵심 불변식 3가지**
1. Form Schema의 모든 입력 field는 `bind`로 ValuationContext의 **단일 경로**에 1:1 매핑된다(매핑 불가 field는 `computed`/`readonly`만 허용).
2. ValuationContext의 `input_hash`는 **계산에 영향을 주는 필드만** 정규화해 해시한다(§3.8). 동일 hash → 동일 PricingResult.
3. PricingResult의 `components` 합은 항상 `total_fair_value`와 일치하며, **부호는 보유자 관점**(§4.10)으로 통일한다.

---

## 2. Dynamic Form Schema 설계

### 2.1 전체 TypeScript 타입 정의 (`shared/schemas/form-schema.ts`)

```ts
// ===========================================================================
// FairValue — Dynamic Form Schema (계약 ①)
// 모든 상품 폼은 이 타입으로 표현된다. 상품별 차이는 schema 데이터로만 표현.
// ===========================================================================

export type InstrumentType = 'RCPS' | 'CPS' | 'CB' | 'EB' | 'BW' | 'SO' | 'CSO';

export type Primitive = string | number | boolean | null;

// --- 필드 타입 (요청 목록 + 확장) ---
export type FieldType =
  | 'text'
  | 'number'
  | 'date'
  | 'select'
  | 'toggle'        // boolean 스위치 (권리조건 on/off)
  | 'table'         // 행 추가형 그리드 (현금흐름/성과조건 등)
  | 'assetSearch'   // 기초자산 검색·매핑
  | 'curveSelector' // (기준일,등급) 커브 선택
  | 'percentage'    // number + '%' 단위·범위검증 프리셋
  | 'currency'      // number + 통화 포맷
  | 'readonly'      // 표시 전용
  | 'computed';     // 다른 필드에서 파생 (예: parity = spot/conv_price)

// --- 조건식 (showWhen / enableWhen 공용) ---
export type ConditionOp =
  | 'eq' | 'neq' | 'in' | 'nin' | 'gt' | 'gte' | 'lt' | 'lte' | 'truthy' | 'falsy';

export interface LeafCondition {
  field: string;                 // 같은 폼 내 다른 field.key (dot-path 허용)
  op: ConditionOp;
  value?: Primitive | Primitive[];
}
export type Condition =
  | LeafCondition
  | { all: Condition[] }         // AND
  | { any: Condition[] }         // OR
  | { not: Condition };

// --- 검증 규칙 ---
export type ValidationRuleType =
  | 'required' | 'min' | 'max' | 'positive'
  | 'dateOrder' | 'dateWithin'
  | 'enum' | 'percentageRange'
  | 'dependencyRequired' | 'mutuallyExclusive' | 'showWhenRequired'
  | 'curveRequired' | 'assetRequired' | 'modelCompatibility'
  | 'refixingFloorCheck' | 'maturityAfterIssueDate'
  | 'exercisePeriodWithinMaturity'
  | 'volatilityPositive' | 'pricePositive' | 'dilutionRange';

export interface ValidationRule {
  rule: ValidationRuleType;
  params?: Record<string, Primitive | Primitive[]>; // 예: { min: 0 }, { other: 'floor_price' }
  message: string;                                  // 사용자 노출 메시지
  severity: 'error' | 'warning';                    // error=제출 차단, warning=주석
}

// --- table 컬럼 ---
export interface ColumnSchema {
  key: string;
  label: string;
  type: Exclude<FieldType, 'table'>;
  unit?: string;
  validations?: ValidationRule[];
}

// --- 단일 필드 ---
export interface FieldSchema {
  key: string;                   // 폼 내 고유 키
  label: string;
  type: FieldType;
  unit?: '%' | '원' | '주' | 'bp' | string;
  required?: boolean;
  defaultValue?: Primitive;
  placeholder?: string;
  help?: string;
  options?: { label: string; value: string | number }[]; // select
  columns?: ColumnSchema[];                                // table
  showWhen?: Condition;          // 미충족 시 숨김 + 검증 제외
  enableWhen?: Condition;        // 미충족 시 비활성(표시는 유지)
  validations?: ValidationRule[];
  bind?: string;                 // ValuationContext 경로. 예: 'terms.issue_date'
  computeFrom?: string[];        // computed/readonly 파생 입력 key 목록
}

// --- 스텝(Wizard 단계) ---
export interface StepSchema {
  id: string;                    // 'terms' | 'rights.refixing' ...
  title: string;
  description?: string;
  showWhen?: Condition;          // 스텝 전체 토글 (예: 매도청구권 미설정 시 숨김)
  fields: FieldSchema[];
}

// --- 폼 전체 ---
export interface FormSchema {
  product: InstrumentType;
  version: string;               // schema semver, 예: '1.0.0'
  title: string;
  steps: StepSchema[];
}
```

### 2.2 상품별 공통 Step 구조

모든 상품이 공유하는 7개 베이스 스텝. 상품별 차이는 **스텝 내 field 유무 + showWhen**으로 표현한다.

| Step id | 제목 | 공통 여부 | 핵심 field(bind) | 비고 |
|---|---|---|---|---|
| `instrument_basics` | 상품 기본정보 | 전 상품 | 발행사·상품명·`valuation_date` | metadata/식별 |
| `terms` | 계약조건 | 전 상품(필드 가변) | `terms.*` | 채권형/주식보상형 필드 분기 |
| `underlying` | 기초자산 | 주식연계 상품 | `market.asset_id`(assetSearch) | SO/CSO/EB/CB/RCPS/CPS/BW |
| `parameters` | 평가 파라미터 | 전 상품 | `curves.*`(curveSelector)·`market.volatility`·`market.dividend_yield` | 커브·변동성·배당 |
| `rights` | 권리조건 | 전 상품(서브스텝 가변) | `rights.*` | 아래 특수 스텝으로 분기 |
| `model` | 모형·실행옵션 | 전 상품 | `model`·`seed`·`options.lattice_steps`·`options.simulation_paths` | 모형 select |
| `review` | 검토·계산 | 전 상품 | computed 요약 | 제출=Job 생성 |

### 2.3 상품별 특수 Step 구조 (rights 하위)

권리조건은 `rights` 스텝의 **서브스텝/토글**로 표현하며, `showWhen`으로 노출 제어한다.

| 특수 Step id | 제목 | 노출 상품 | 핵심 field(bind) |
|---|---|---|---|
| `rights.conversion` | 전환권 | RCPS·CPS·CB | `rights.conversion.{strike,ratio,start,end,style}` |
| `rights.refixing` | 리픽싱 | RCPS·CPS·CB | `rights.refixing.{start,step_month,floor,init_strike,direction}` |
| `rights.redemption` | 상환권(콜/풋) | RCPS·CB·EB·BW | `rights.redemption.{put,call}` |
| `rights.issuer_call` | 발행자 콜 | RCPS·CB | `rights.issuer_call.{strike,style,start}` |
| `rights.sale_claim` | 매도청구권 | RCPS·CB | `rights.sale_claim.{discount_type,strike_pct,style,standalone}` |
| `rights.exchange` | 교환권(타사주) | EB | `rights.exchange.{target_asset_id,ratio,strike}` |
| `rights.warrant` | 신주인수권 | BW | `rights.warrant.{strike,quantity,separable,start,end}` |
| `rights.dilution` | 희석효과 | BW·CB(전환) | `rights.dilution.{enabled,new_shares}` |
| `rights.vesting` | 가득조건 | SO·CSO | `rights.vesting.{schedule[],cliff_month,forfeiture_rate}` |
| `rights.performance_condition` | 비시장 성과조건 | CSO | `rights.performance_condition.{metric,target,probability}` |
| `rights.market_condition` | 시장조건 | CSO | `rights.market_condition.{type,target_price,barrier}` |


### 2.4 CB Dynamic Form Schema 예시 (`frontend/src/forms/productSchemas/cb.json`)

```json
{
  "product": "CB",
  "version": "1.0.0",
  "title": "전환사채(CB) 평가",
  "steps": [
    {
      "id": "instrument_basics",
      "title": "기본정보",
      "fields": [
        { "key": "issuer", "label": "발행사", "type": "text", "required": true, "bind": "metadata.issuer" },
        { "key": "name", "label": "상품명", "type": "text", "required": true, "bind": "metadata.instrument_name" },
        { "key": "valuation_date", "label": "평가기준일", "type": "date", "required": true, "bind": "valuation_date",
          "validations": [{ "rule": "required", "message": "평가기준일은 필수입니다.", "severity": "error" }] }
      ]
    },
    {
      "id": "terms",
      "title": "계약조건",
      "fields": [
        { "key": "issue_date", "label": "발행일", "type": "date", "required": true, "bind": "terms.issue_date" },
        { "key": "maturity_date", "label": "만기일", "type": "date", "required": true, "bind": "terms.maturity_date",
          "validations": [
            { "rule": "maturityAfterIssueDate", "params": { "issueField": "issue_date" },
              "message": "만기일은 발행일 이후여야 합니다.", "severity": "error" }
          ] },
        { "key": "issue_amount", "label": "발행금액", "type": "currency", "unit": "원", "required": true, "bind": "terms.issue_amount",
          "validations": [{ "rule": "positive", "message": "발행금액은 0보다 커야 합니다.", "severity": "error" }] },
        { "key": "face_value", "label": "액면금액", "type": "currency", "unit": "원", "required": true, "bind": "terms.face_value" },
        { "key": "coupon_rate", "label": "표면이자율", "type": "percentage", "unit": "%", "required": true, "bind": "terms.coupon_rate",
          "validations": [{ "rule": "percentageRange", "params": { "min": 0, "max": 100 },
            "message": "표면이자율은 0~100% 범위여야 합니다.", "severity": "error" }] },
        { "key": "coupon_freq_month", "label": "이자지급주기(개월)", "type": "select", "required": true, "bind": "terms.coupon_freq_month",
          "options": [{ "label": "1개월", "value": 1 }, { "label": "3개월", "value": 3 },
                      { "label": "6개월", "value": 6 }, { "label": "12개월", "value": 12 }] },
        { "key": "guaranteed_yield", "label": "보장수익률(YTP/YTM)", "type": "percentage", "unit": "%", "bind": "terms.guaranteed_yield" },
        { "key": "redemption_type", "label": "원금상환방식", "type": "select", "bind": "terms.redemption_type",
          "options": [{ "label": "만기일시", "value": "bullet" }, { "label": "분할", "value": "amortizing" }] }
      ]
    },
    {
      "id": "underlying",
      "title": "기초자산",
      "fields": [
        { "key": "asset", "label": "기초자산(발행사 보통주)", "type": "assetSearch", "required": true, "bind": "market.asset_id",
          "validations": [{ "rule": "assetRequired", "message": "기초자산을 선택하세요.", "severity": "error" }] }
      ]
    },
    {
      "id": "parameters",
      "title": "평가 파라미터",
      "fields": [
        { "key": "risk_free_curve", "label": "무위험수익률 커브", "type": "curveSelector", "required": true, "bind": "curves.risk_free_ref",
          "validations": [{ "rule": "curveRequired", "params": { "kind": "RISK_FREE" },
            "message": "무위험 커브를 선택하세요.", "severity": "error" }] },
        { "key": "credit_curve", "label": "신용등급 커브", "type": "curveSelector", "required": true, "bind": "curves.credit_ref",
          "validations": [{ "rule": "curveRequired", "params": { "kind": "CREDIT" },
            "message": "신용 커브를 선택하세요.", "severity": "error" }] },
        { "key": "volatility", "label": "주가 변동성", "type": "percentage", "unit": "%", "required": true, "bind": "market.volatility",
          "validations": [{ "rule": "volatilityPositive", "message": "변동성은 0보다 커야 합니다.", "severity": "error" }] },
        { "key": "dividend_yield", "label": "배당수익률", "type": "percentage", "unit": "%", "defaultValue": 0, "bind": "market.dividend_yield" },
        { "key": "spot", "label": "기준일 주가", "type": "currency", "unit": "원", "required": true, "bind": "market.spot",
          "validations": [{ "rule": "pricePositive", "message": "주가는 0보다 커야 합니다.", "severity": "error" }] }
      ]
    },
    {
      "id": "rights.conversion",
      "title": "전환권",
      "fields": [
        { "key": "conv_price", "label": "전환가액", "type": "currency", "unit": "원", "required": true, "bind": "rights.conversion.strike" },
        { "key": "conv_ratio", "label": "전환비율", "type": "number", "bind": "rights.conversion.ratio" },
        { "key": "conv_start", "label": "전환청구 시작일", "type": "date", "required": true, "bind": "rights.conversion.start",
          "validations": [{ "rule": "exercisePeriodWithinMaturity",
            "message": "전환기간은 만기 이내여야 합니다.", "severity": "error" }] },
        { "key": "conv_end", "label": "전환청구 종료일", "type": "date", "bind": "rights.conversion.end" },
        { "key": "parity", "label": "패리티(자동계산)", "type": "computed", "computeFrom": ["spot", "conv_price"],
          "help": "spot / conv_price × 액면", "bind": "rights.conversion.parity" }
      ]
    },
    {
      "id": "rights.refixing",
      "title": "리픽싱",
      "showWhen": { "field": "use_refixing", "op": "truthy" },
      "fields": [
        { "key": "use_refixing", "label": "리픽싱 적용", "type": "toggle", "defaultValue": false, "bind": "rights.refixing.enabled" },
        { "key": "refix_start", "label": "리픽싱 시작일", "type": "date", "showWhen": { "field": "use_refixing", "op": "truthy" },
          "bind": "rights.refixing.start" },
        { "key": "refix_step", "label": "리픽싱 주기(개월)", "type": "number", "showWhen": { "field": "use_refixing", "op": "truthy" },
          "bind": "rights.refixing.step_month" },
        { "key": "refix_floor", "label": "리픽싱 하한가", "type": "currency", "unit": "원",
          "showWhen": { "field": "use_refixing", "op": "truthy" }, "bind": "rights.refixing.floor",
          "validations": [{ "rule": "refixingFloorCheck", "params": { "initField": "conv_price" },
            "message": "하한가는 0 이상이며 최초 전환가 이하여야 합니다.", "severity": "error" }] },
        { "key": "refix_direction", "label": "리픽싱 방향", "type": "select",
          "showWhen": { "field": "use_refixing", "op": "truthy" }, "bind": "rights.refixing.direction",
          "options": [{ "label": "하향만", "value": "DOWN" }, { "label": "상·하향", "value": "BOTH" }] }
      ]
    },
    {
      "id": "rights.redemption",
      "title": "상환권(콜/풋)",
      "fields": [
        { "key": "use_put", "label": "조기상환청구권(풋)", "type": "toggle", "defaultValue": false, "bind": "rights.redemption.put.enabled" },
        { "key": "put_yield", "label": "풋 보장수익률(YTP)", "type": "percentage", "unit": "%",
          "showWhen": { "field": "use_put", "op": "truthy" }, "bind": "rights.redemption.put.yield" },
        { "key": "use_call", "label": "중도상환권(콜)", "type": "toggle", "defaultValue": false, "bind": "rights.redemption.call.enabled" },
        { "key": "call_yield", "label": "콜 보장수익률(YTC)", "type": "percentage", "unit": "%",
          "showWhen": { "field": "use_call", "op": "truthy" }, "bind": "rights.redemption.call.yield" }
      ]
    },
    {
      "id": "rights.sale_claim",
      "title": "매도청구권(콜옵션·발행자/제3자)",
      "showWhen": { "field": "use_sale_claim", "op": "truthy" },
      "fields": [
        { "key": "use_sale_claim", "label": "매도청구권 적용", "type": "toggle", "defaultValue": false, "bind": "rights.sale_claim.enabled" },
        { "key": "sc_discount_type", "label": "할인유형", "type": "select",
          "showWhen": { "field": "use_sale_claim", "op": "truthy" }, "bind": "rights.sale_claim.discount_type",
          "options": [{ "label": "독립·무위험할인", "value": "standalone_riskfree" },
                      { "label": "신용할인", "value": "credit" }] },
        { "key": "sc_strike_pct", "label": "매도청구 행사가(%)", "type": "percentage", "unit": "%",
          "showWhen": { "field": "use_sale_claim", "op": "truthy" }, "bind": "rights.sale_claim.strike_pct" },
        { "key": "sc_style", "label": "행사유형", "type": "select",
          "showWhen": { "field": "use_sale_claim", "op": "truthy" }, "bind": "rights.sale_claim.style",
          "options": [{ "label": "American", "value": "AMERICAN" }, { "label": "European", "value": "EUROPEAN" },
                      { "label": "Bermudan", "value": "BERMUDAN" }] }
      ]
    },
    {
      "id": "model",
      "title": "평가모형·실행옵션",
      "fields": [
        { "key": "model", "label": "평가모형", "type": "select", "required": true, "defaultValue": "TF_LATTICE", "bind": "model",
          "options": [{ "label": "Tsiveriotis-Fernandes 격자", "value": "TF_LATTICE" },
                      { "label": "Monte Carlo (교차검증)", "value": "MONTE_CARLO" },
                      { "label": "LSMC (리픽싱 경로의존)", "value": "LSMC" }],
          "validations": [{ "rule": "modelCompatibility",
            "message": "선택 모형이 입력된 권리조건과 호환되지 않습니다.", "severity": "error" }] },
        { "key": "lattice_steps", "label": "격자 스텝수", "type": "number", "defaultValue": 1000,
          "showWhen": { "field": "model", "op": "in", "value": ["TF_LATTICE"] }, "bind": "options.lattice_steps" },
        { "key": "simulation_paths", "label": "시뮬레이션 패스수", "type": "number", "defaultValue": 100000,
          "showWhen": { "field": "model", "op": "in", "value": ["MONTE_CARLO", "LSMC"] }, "bind": "options.simulation_paths" },
        { "key": "seed", "label": "난수 시드", "type": "number", "defaultValue": 20240101, "bind": "seed" }
      ]
    },
    {
      "id": "review",
      "title": "검토 및 계산",
      "fields": [
        { "key": "summary", "label": "입력 요약", "type": "readonly", "computeFrom": ["issuer", "valuation_date", "issue_amount", "conv_price"] }
      ]
    }
  ]
}
```

### 2.5 RCPS Dynamic Form Schema 예시 (요지 JSON)

> CB와 공통 스텝을 공유하되 `terms`가 우선주형으로 바뀌고, `rights.redemption`이 보장수익률 기반 **상환권(풋)** 으로 표현된다.

```json
{
  "product": "RCPS",
  "version": "1.0.0",
  "title": "상환전환우선주(RCPS) 평가",
  "steps": [
    { "id": "instrument_basics", "title": "기본정보",
      "fields": [
        { "key": "issuer", "label": "발행사", "type": "text", "required": true, "bind": "metadata.issuer" },
        { "key": "valuation_date", "label": "평가기준일", "type": "date", "required": true, "bind": "valuation_date" }
      ] },
    { "id": "terms", "title": "발행조건",
      "fields": [
        { "key": "issue_date", "label": "발행일", "type": "date", "required": true, "bind": "terms.issue_date" },
        { "key": "maturity_date", "label": "만기일(상환청구 기준)", "type": "date", "required": true, "bind": "terms.maturity_date",
          "validations": [{ "rule": "maturityAfterIssueDate", "params": { "issueField": "issue_date" },
            "message": "만기일은 발행일 이후여야 합니다.", "severity": "error" }] },
        { "key": "issue_price", "label": "주당 발행가", "type": "currency", "unit": "원", "required": true, "bind": "terms.issue_price" },
        { "key": "issue_amount", "label": "발행금액", "type": "currency", "unit": "원", "required": true, "bind": "terms.issue_amount" },
        { "key": "dividend_rate", "label": "우선배당률", "type": "percentage", "unit": "%", "bind": "terms.coupon_rate" },
        { "key": "dividend_cumulative", "label": "누적적 여부", "type": "toggle", "defaultValue": true, "bind": "terms.dividend_cumulative" },
        { "key": "guaranteed_yield", "label": "보장수익률(상환 IRR)", "type": "percentage", "unit": "%", "required": true, "bind": "terms.guaranteed_yield" }
      ] },
    { "id": "underlying", "title": "기초자산",
      "fields": [{ "key": "asset", "label": "발행사 보통주", "type": "assetSearch", "required": true, "bind": "market.asset_id" }] },
    { "id": "parameters", "title": "평가 파라미터",
      "fields": [
        { "key": "risk_free_curve", "label": "무위험 커브", "type": "curveSelector", "required": true, "bind": "curves.risk_free_ref" },
        { "key": "credit_curve", "label": "신용 커브", "type": "curveSelector", "required": true, "bind": "curves.credit_ref" },
        { "key": "volatility", "label": "변동성", "type": "percentage", "unit": "%", "required": true, "bind": "market.volatility" },
        { "key": "spot", "label": "기준일 주가/공정가치", "type": "currency", "unit": "원", "required": true, "bind": "market.spot" }
      ] },
    { "id": "rights.conversion", "title": "전환권",
      "fields": [
        { "key": "conv_price", "label": "전환가액", "type": "currency", "unit": "원", "required": true, "bind": "rights.conversion.strike" },
        { "key": "conv_start", "label": "전환 시작일", "type": "date", "required": true, "bind": "rights.conversion.start" }
      ] },
    { "id": "rights.refixing", "title": "리픽싱", "showWhen": { "field": "use_refixing", "op": "truthy" },
      "fields": [
        { "key": "use_refixing", "label": "리픽싱 적용", "type": "toggle", "defaultValue": true, "bind": "rights.refixing.enabled" },
        { "key": "refix_floor", "label": "하한가", "type": "currency", "unit": "원", "showWhen": { "field": "use_refixing", "op": "truthy" },
          "bind": "rights.refixing.floor",
          "validations": [{ "rule": "refixingFloorCheck", "params": { "initField": "conv_price" },
            "message": "하한가는 최초 전환가 이하여야 합니다.", "severity": "error" }] }
      ] },
    { "id": "rights.redemption", "title": "상환권",
      "fields": [
        { "key": "redeemable", "label": "상환청구권 보유", "type": "toggle", "defaultValue": true, "bind": "rights.redemption.put.enabled" },
        { "key": "redemption_start", "label": "상환청구 가능일", "type": "date", "bind": "rights.redemption.put.start" }
      ] },
    { "id": "rights.issuer_call", "title": "발행자 콜",
      "fields": [
        { "key": "use_issuer_call", "label": "발행자 콜 보유", "type": "toggle", "defaultValue": false, "bind": "rights.issuer_call.enabled" }
      ] },
    { "id": "model", "title": "평가모형",
      "fields": [
        { "key": "model", "label": "평가모형", "type": "select", "required": true, "defaultValue": "LATTICE", "bind": "model",
          "options": [{ "label": "이항격자", "value": "LATTICE" }, { "label": "LSMC", "value": "LSMC" }] },
        { "key": "seed", "label": "시드", "type": "number", "defaultValue": 20240101, "bind": "seed" }
      ] },
    { "id": "review", "title": "검토 및 계산", "fields": [{ "key": "summary", "label": "요약", "type": "readonly" }] }
  ]
}
```

### 2.6 나머지 상품군 필드 구조 요약표

> 공통 스텝(`instrument_basics·terms·parameters·model·review`)은 전 상품 공유. 아래는 **상품별로 추가/변형되는 핵심 스텝·필드**만 정리.

| 상품 | terms 핵심 필드(bind) | underlying | 권리 스텝(showWhen) | 특수 필드 |
|---|---|---|---|---|
| **CPS** | issue_price, dividend_rate, dividend_cumulative, **만기형/영구형** `terms.host_type` | 필요 | `rights.conversion`, `rights.refixing` | 상환 스텝 **없음**(상환권 미보유), `terms.host_type=perpetual\|dated` |
| **EB** | issue_amount, face_value, coupon_rate, coupon_freq_month, redemption_type | **교환대상 타사주** `rights.exchange.target_asset_id` | `rights.exchange`, `rights.redemption` | `rights.exchange.{ratio,strike}`, 희석 **미적용** |
| **BW** | issue_amount, face_value, coupon_rate, coupon_freq_month | 필요(신주 기초) | `rights.warrant`, `rights.dilution`, `rights.redemption` | `rights.warrant.{strike,quantity,separable,start,end}`, `rights.dilution.new_shares` |
| **Stock Option (SO)** | `terms.grant_date`, `terms.grant_quantity`, `terms.exercise_price`, `terms.expected_term` | 필요(부여 기초) | `rights.vesting` | `rights.vesting.{schedule[],cliff_month,forfeiture_rate}`, `market.shares_outstanding` |
| **Conditional SO (CSO)** | SO 동일 + `terms.tranche[]`(table) | 필요 | `rights.vesting`, `rights.market_condition`, `rights.performance_condition` | `rights.market_condition.{type,target_price,barrier}`, `rights.performance_condition.{metric,target,probability}` |


---

## 3. ValuationContext JSON Schema 설계

### 3.1 전체 JSON Schema (`shared/schemas/valuation-context.schema.json`, draft 2020-12)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://fairvalue.dev/schemas/valuation-context.schema.json",
  "title": "ValuationContext",
  "type": "object",
  "required": ["instrument_type", "valuation_date", "instrument_id", "terms",
               "rights", "market", "curves", "model", "seed", "input_hash", "model_version"],
  "additionalProperties": false,
  "properties": {
    "instrument_type": { "enum": ["RCPS", "CPS", "CB", "EB", "BW", "SO", "CSO"] },
    "valuation_date": { "type": "string", "format": "date" },
    "organization_id": { "type": "integer" },
    "instrument_id": { "type": "integer" },
    "terms": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "issue_date": { "type": "string", "format": "date" },
        "maturity_date": { "type": ["string", "null"], "format": "date" },
        "issue_amount": { "type": ["number", "null"] },
        "face_value": { "type": ["number", "null"] },
        "issue_price": { "type": ["number", "null"] },
        "coupon_rate": { "type": ["number", "null"], "description": "연 %" },
        "coupon_freq_month": { "type": ["integer", "null"], "enum": [1, 3, 6, 12, null] },
        "guaranteed_yield": { "type": ["number", "null"], "description": "연 %" },
        "redemption_type": { "type": ["string", "null"], "enum": ["bullet", "amortizing", null] },
        "interest_payment_type": { "type": ["string", "null"], "enum": ["cash", "accrued", "compound", null] },
        "dividend_cumulative": { "type": ["boolean", "null"] },
        "host_type": { "type": ["string", "null"], "enum": ["dated", "perpetual", null] },
        "grant_date": { "type": ["string", "null"], "format": "date" },
        "grant_quantity": { "type": ["number", "null"] },
        "exercise_price": { "type": ["number", "null"] },
        "expected_term": { "type": ["number", "null"], "description": "연" },
        "tranche": { "type": "array", "items": { "type": "object" } }
      }
    },
    "rights": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "conversion": { "$ref": "#/$defs/conversion" },
        "exchange": { "$ref": "#/$defs/exchange" },
        "warrant": { "$ref": "#/$defs/warrant" },
        "redemption": { "$ref": "#/$defs/redemption" },
        "issuer_call": { "$ref": "#/$defs/issuerCall" },
        "sale_claim": { "$ref": "#/$defs/saleClaim" },
        "refixing": { "$ref": "#/$defs/refixing" },
        "dilution": { "$ref": "#/$defs/dilution" },
        "vesting": { "$ref": "#/$defs/vesting" },
        "performance_condition": { "$ref": "#/$defs/perfCondition" },
        "market_condition": { "$ref": "#/$defs/marketCondition" }
      }
    },
    "market": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "asset_id": { "type": ["integer", "null"] },
        "spot": { "type": ["number", "null"] },
        "volatility": { "type": ["number", "null"], "description": "연 %" },
        "dividend_yield": { "type": ["number", "null"], "description": "연 %" },
        "shares_outstanding": { "type": ["number", "null"] },
        "peer_companies": { "type": "array", "items": { "type": "object" } },
        "data_source": { "type": ["string", "null"] },
        "as_of": { "type": ["string", "null"], "format": "date" }
      }
    },
    "curves": {
      "type": "object",
      "additionalProperties": false,
      "required": ["risk_free_curve"],
      "properties": {
        "risk_free_curve": { "$ref": "#/$defs/curve" },
        "credit_curve": { "anyOf": [{ "$ref": "#/$defs/curve" }, { "type": "null" }] },
        "curve_source": { "type": "string" },
        "curve_version": { "type": "string" },
        "interpolation_method": { "enum": ["linear", "log_linear", "monotone_convex"] },
        "fallback_policy": { "enum": ["nearest_grade", "manual_only", "error"] }
      }
    },
    "model": { "enum": ["TF_LATTICE", "LATTICE", "TRINOMIAL", "MONTE_CARLO", "LSMC", "BSM", "DCF"] },
    "seed": { "type": "integer" },
    "input_hash": { "type": "string", "pattern": "^[a-f0-9]{64}$" },
    "model_version": { "type": "string", "description": "예: cb-1.0.0" },
    "options": {
      "type": "object",
      "properties": {
        "lattice_steps": { "type": "integer" },
        "simulation_paths": { "type": "integer" },
        "variance_reduction": { "enum": ["none", "antithetic", "sobol"] }
      }
    },
    "metadata": {
      "type": "object",
      "description": "input_hash 비대상. 추적용.",
      "properties": {
        "issuer": { "type": "string" },
        "instrument_name": { "type": "string" },
        "requested_by": { "type": "integer" },
        "created_at": { "type": "string", "format": "date-time" }
      }
    }
  },
  "$defs": {
    "curve": {
      "type": "array",
      "items": { "type": "array", "prefixItems": [{ "type": "number" }, { "type": "number" }],
                 "minItems": 2, "maxItems": 2 },
      "description": "[[tenor_year, rate_pct], ...]"
    },
    "conversion": { "type": "object", "properties": {
      "strike": { "type": "number" }, "ratio": { "type": ["number", "null"] },
      "start": { "type": ["string", "null"], "format": "date" },
      "end": { "type": ["string", "null"], "format": "date" },
      "style": { "enum": ["AMERICAN", "EUROPEAN", "BERMUDAN", null] } } },
    "exchange": { "type": "object", "properties": {
      "target_asset_id": { "type": "integer" }, "ratio": { "type": "number" },
      "strike": { "type": ["number", "null"] } } },
    "warrant": { "type": "object", "properties": {
      "strike": { "type": "number" }, "quantity": { "type": "number" },
      "separable": { "type": "boolean" },
      "start": { "type": ["string", "null"], "format": "date" },
      "end": { "type": ["string", "null"], "format": "date" } } },
    "redemption": { "type": "object", "properties": {
      "put": { "type": "object", "properties": {
        "enabled": { "type": "boolean" }, "yield": { "type": ["number", "null"] },
        "start": { "type": ["string", "null"], "format": "date" },
        "style": { "enum": ["AMERICAN", "EUROPEAN", "BERMUDAN", null] } } },
      "call": { "type": "object", "properties": {
        "enabled": { "type": "boolean" }, "yield": { "type": ["number", "null"] },
        "start": { "type": ["string", "null"], "format": "date" } } } } },
    "issuerCall": { "type": "object", "properties": {
      "enabled": { "type": "boolean" }, "strike": { "type": ["number", "null"] },
      "style": { "enum": ["AMERICAN", "EUROPEAN", "BERMUDAN", null] },
      "start": { "type": ["string", "null"], "format": "date" } } },
    "saleClaim": { "type": "object", "properties": {
      "enabled": { "type": "boolean" },
      "discount_type": { "enum": ["standalone_riskfree", "credit", null] },
      "strike_pct": { "type": ["number", "null"] },
      "style": { "enum": ["AMERICAN", "EUROPEAN", "BERMUDAN", null] },
      "beneficiary": { "enum": ["issuer", "third_party", null] } } },
    "refixing": { "type": "object", "properties": {
      "enabled": { "type": "boolean" },
      "start": { "type": ["string", "null"], "format": "date" },
      "step_month": { "type": ["integer", "null"] },
      "floor": { "type": ["number", "null"] },
      "init_strike": { "type": ["number", "null"] },
      "direction": { "enum": ["DOWN", "BOTH", null] } } },
    "dilution": { "type": "object", "properties": {
      "enabled": { "type": "boolean" }, "new_shares": { "type": ["number", "null"] } } },
    "vesting": { "type": "object", "properties": {
      "schedule": { "type": "array", "items": { "type": "object", "properties": {
        "date": { "type": "string", "format": "date" }, "ratio": { "type": "number" } } } },
      "cliff_month": { "type": ["integer", "null"] },
      "forfeiture_rate": { "type": ["number", "null"] } } },
    "perfCondition": { "type": "object", "properties": {
      "metric": { "enum": ["revenue", "ebitda", "ni", "custom", null] },
      "target": { "type": ["number", "null"] },
      "probability": { "type": ["number", "null"], "description": "비시장 충족확률(0~1)" } } },
    "marketCondition": { "type": "object", "properties": {
      "type": { "enum": ["target_price", "tsr", "barrier", null] },
      "target_price": { "type": ["number", "null"] },
      "barrier": { "type": ["number", "null"] },
      "knock": { "enum": ["in", "out", null] } } }
  }
}
```

### 3.2 TypeScript interface (`shared/schemas/valuation-context.ts`)

```ts
export interface ValuationContext {
  instrument_type: InstrumentType;
  valuation_date: string;            // ISO date
  organization_id?: number;
  instrument_id: number;
  terms: Terms;
  rights: Rights;
  market: Market;
  curves: Curves;
  model: ModelName;
  seed: number;
  input_hash: string;                // 64-hex SHA-256
  model_version: string;
  options?: EngineOptions;
  metadata?: ContextMetadata;        // input_hash 비대상
}

export type ModelName =
  'TF_LATTICE' | 'LATTICE' | 'TRINOMIAL' | 'MONTE_CARLO' | 'LSMC' | 'BSM' | 'DCF';

export interface Terms {
  issue_date?: string; maturity_date?: string | null;
  issue_amount?: number | null; face_value?: number | null; issue_price?: number | null;
  coupon_rate?: number | null; coupon_freq_month?: 1|3|6|12 | null;
  guaranteed_yield?: number | null;
  redemption_type?: 'bullet'|'amortizing' | null;
  interest_payment_type?: 'cash'|'accrued'|'compound' | null;
  dividend_cumulative?: boolean | null;
  host_type?: 'dated'|'perpetual' | null;
  grant_date?: string | null; grant_quantity?: number | null;
  exercise_price?: number | null; expected_term?: number | null;
  tranche?: Record<string, unknown>[];
}

export interface Rights {
  conversion?: Conversion; exchange?: Exchange; warrant?: Warrant;
  redemption?: Redemption; issuer_call?: IssuerCall; sale_claim?: SaleClaim;
  refixing?: Refixing; dilution?: Dilution; vesting?: Vesting;
  performance_condition?: PerfCondition; market_condition?: MarketCondition;
}
export interface Conversion { strike: number; ratio?: number | null; start?: string | null; end?: string | null; style?: ExerciseStyle | null; }
export interface Exchange { target_asset_id: number; ratio: number; strike?: number | null; }
export interface Warrant { strike: number; quantity: number; separable: boolean; start?: string | null; end?: string | null; }
export interface Redemption { put?: { enabled: boolean; yield?: number | null; start?: string | null; style?: ExerciseStyle | null };
                              call?: { enabled: boolean; yield?: number | null; start?: string | null }; }
export interface IssuerCall { enabled: boolean; strike?: number | null; style?: ExerciseStyle | null; start?: string | null; }
export interface SaleClaim { enabled: boolean; discount_type?: 'standalone_riskfree'|'credit' | null;
                             strike_pct?: number | null; style?: ExerciseStyle | null; beneficiary?: 'issuer'|'third_party' | null; }
export interface Refixing { enabled: boolean; start?: string | null; step_month?: number | null;
                            floor?: number | null; init_strike?: number | null; direction?: 'DOWN'|'BOTH' | null; }
export interface Dilution { enabled: boolean; new_shares?: number | null; }
export interface Vesting { schedule: { date: string; ratio: number }[]; cliff_month?: number | null; forfeiture_rate?: number | null; }
export interface PerfCondition { metric?: 'revenue'|'ebitda'|'ni'|'custom' | null; target?: number | null; probability?: number | null; }
export interface MarketCondition { type?: 'target_price'|'tsr'|'barrier' | null; target_price?: number | null; barrier?: number | null; knock?: 'in'|'out' | null; }
export type ExerciseStyle = 'AMERICAN'|'EUROPEAN'|'BERMUDAN';

export interface Market {
  asset_id?: number | null; spot?: number | null; volatility?: number | null;
  dividend_yield?: number | null; shares_outstanding?: number | null;
  peer_companies?: Record<string, unknown>[]; data_source?: string | null; as_of?: string | null;
}
export type CurvePoints = [number, number][]; // [tenor_year, rate_pct]
export interface Curves {
  risk_free_curve: CurvePoints; credit_curve?: CurvePoints | null;
  curve_source: string; curve_version: string;
  interpolation_method: 'linear'|'log_linear'|'monotone_convex';
  fallback_policy: 'nearest_grade'|'manual_only'|'error';
}
export interface EngineOptions { lattice_steps?: number; simulation_paths?: number; variance_reduction?: 'none'|'antithetic'|'sobol'; }
export interface ContextMetadata { issuer?: string; instrument_name?: string; requested_by?: number; created_at?: string; }
```

### 3.3 Python Pydantic model (`pricing-engine/app/context.py`)

```python
from __future__ import annotations
from datetime import date, datetime
from typing import Literal, Optional
from pydantic import BaseModel, Field, ConfigDict

InstrumentType = Literal["RCPS", "CPS", "CB", "EB", "BW", "SO", "CSO"]
ModelName = Literal["TF_LATTICE", "LATTICE", "TRINOMIAL", "MONTE_CARLO", "LSMC", "BSM", "DCF"]
ExerciseStyle = Literal["AMERICAN", "EUROPEAN", "BERMUDAN"]
CurvePoints = list[tuple[float, float]]   # [(tenor_year, rate_pct), ...]


class Terms(BaseModel):
    model_config = ConfigDict(extra="forbid")
    issue_date: Optional[date] = None
    maturity_date: Optional[date] = None
    issue_amount: Optional[float] = None
    face_value: Optional[float] = None
    issue_price: Optional[float] = None
    coupon_rate: Optional[float] = None
    coupon_freq_month: Optional[Literal[1, 3, 6, 12]] = None
    guaranteed_yield: Optional[float] = None
    redemption_type: Optional[Literal["bullet", "amortizing"]] = None
    interest_payment_type: Optional[Literal["cash", "accrued", "compound"]] = None
    dividend_cumulative: Optional[bool] = None
    host_type: Optional[Literal["dated", "perpetual"]] = None
    grant_date: Optional[date] = None
    grant_quantity: Optional[float] = None
    exercise_price: Optional[float] = None
    expected_term: Optional[float] = None
    tranche: list[dict] = Field(default_factory=list)


class Conversion(BaseModel):
    strike: float
    ratio: Optional[float] = None
    start: Optional[date] = None
    end: Optional[date] = None
    style: Optional[ExerciseStyle] = None

# (Exchange/Warrant/Redemption/IssuerCall/SaleClaim/Refixing/Dilution/
#  Vesting/PerfCondition/MarketCondition 도 동일 패턴으로 정의)

class Rights(BaseModel):
    model_config = ConfigDict(extra="forbid")
    conversion: Optional[Conversion] = None
    exchange: Optional[dict] = None
    warrant: Optional[dict] = None
    redemption: Optional[dict] = None
    issuer_call: Optional[dict] = None
    sale_claim: Optional[dict] = None
    refixing: Optional[dict] = None
    dilution: Optional[dict] = None
    vesting: Optional[dict] = None
    performance_condition: Optional[dict] = None
    market_condition: Optional[dict] = None


class Market(BaseModel):
    model_config = ConfigDict(extra="forbid")
    asset_id: Optional[int] = None
    spot: Optional[float] = None
    volatility: Optional[float] = None
    dividend_yield: Optional[float] = None
    shares_outstanding: Optional[float] = None
    peer_companies: list[dict] = Field(default_factory=list)
    data_source: Optional[str] = None
    as_of: Optional[date] = None


class Curves(BaseModel):
    model_config = ConfigDict(extra="forbid")
    risk_free_curve: CurvePoints
    credit_curve: Optional[CurvePoints] = None
    curve_source: str
    curve_version: str
    interpolation_method: Literal["linear", "log_linear", "monotone_convex"] = "linear"
    fallback_policy: Literal["nearest_grade", "manual_only", "error"] = "error"


class EngineOptions(BaseModel):
    lattice_steps: Optional[int] = None
    simulation_paths: Optional[int] = None
    variance_reduction: Literal["none", "antithetic", "sobol"] = "none"


class ContextMetadata(BaseModel):
    issuer: Optional[str] = None
    instrument_name: Optional[str] = None
    requested_by: Optional[int] = None
    created_at: Optional[datetime] = None


class ValuationContext(BaseModel):
    model_config = ConfigDict(extra="forbid")
    instrument_type: InstrumentType
    valuation_date: date
    organization_id: Optional[int] = None
    instrument_id: int
    terms: Terms
    rights: Rights
    market: Market
    curves: Curves
    model: ModelName
    seed: int
    input_hash: str
    model_version: str
    options: EngineOptions = Field(default_factory=EngineOptions)
    metadata: ContextMetadata = Field(default_factory=ContextMetadata)
```

### 3.4 CB ValuationContext 예시

```json
{
  "instrument_type": "CB", "valuation_date": "2024-06-26",
  "organization_id": 12, "instrument_id": 3001,
  "terms": { "issue_date": "2023-09-13", "maturity_date": "2026-09-13",
    "issue_amount": 3000000000, "face_value": 10000, "coupon_rate": 2.0,
    "coupon_freq_month": 3, "guaranteed_yield": 8.0, "redemption_type": "bullet",
    "interest_payment_type": "cash" },
  "rights": {
    "conversion": { "strike": 3260, "ratio": 1, "start": "2024-09-13", "style": "AMERICAN" },
    "refixing": { "enabled": true, "start": "2024-09-13", "step_month": 3, "floor": 2282, "init_strike": 3260, "direction": "DOWN" },
    "redemption": { "put": { "enabled": true, "yield": 8.0, "start": "2025-09-13", "style": "BERMUDAN" },
                    "call": { "enabled": false } },
    "sale_claim": { "enabled": true, "discount_type": "standalone_riskfree", "strike_pct": 100, "style": "AMERICAN", "beneficiary": "issuer" },
    "dilution": { "enabled": false }
  },
  "market": { "asset_id": 880, "spot": 3260, "volatility": 45.0, "dividend_yield": 0.0,
              "shares_outstanding": 12000000, "data_source": "manual", "as_of": "2024-06-26" },
  "curves": { "risk_free_curve": [[0.25, 3.41], [1, 3.35], [3, 3.30]],
              "credit_curve": [[0.25, 13.45], [1, 14.10], [3, 15.02]],
              "curve_source": "uploaded:2024-06-26/B-/v1", "curve_version": "v1",
              "interpolation_method": "log_linear", "fallback_policy": "nearest_grade" },
  "model": "TF_LATTICE", "seed": 20240101, "model_version": "cb-1.0.0",
  "options": { "lattice_steps": 1000 },
  "input_hash": "a1b2c3d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccddeeff00",
  "metadata": { "issuer": "예시바이오", "instrument_name": "예시바이오 3CB", "requested_by": 7 }
}
```

### 3.5 RCPS ValuationContext 예시 (요지)

```json
{
  "instrument_type": "RCPS", "valuation_date": "2024-06-26", "instrument_id": 3002,
  "terms": { "issue_date": "2021-06-30", "maturity_date": "2031-06-30",
    "issue_price": 20619, "issue_amount": 5000000000, "coupon_rate": 0.024,
    "dividend_cumulative": true, "guaranteed_yield": 5.0 },
  "rights": {
    "conversion": { "strike": 20619, "ratio": 1, "start": "2022-06-30" },
    "refixing": { "enabled": true, "floor": 14433, "init_strike": 20619, "direction": "DOWN" },
    "redemption": { "put": { "enabled": true, "start": "2024-06-30", "style": "AMERICAN" } },
    "issuer_call": { "enabled": false }
  },
  "market": { "asset_id": 881, "spot": 22000, "volatility": 50.0, "dividend_yield": 0.0 },
  "curves": { "risk_free_curve": [[1, 3.35], [5, 3.40], [10, 3.55]],
              "credit_curve": [[1, 9.10], [5, 10.20], [10, 11.05]],
              "curve_source": "uploaded:2024-06-26/BB/v2", "curve_version": "v2",
              "interpolation_method": "linear", "fallback_policy": "nearest_grade" },
  "model": "LATTICE", "seed": 20240101, "model_version": "rcps-1.0.0",
  "input_hash": "....", "metadata": { "issuer": "예시테크" }
}
```

### 3.6 Stock Option ValuationContext 예시 (요지)

```json
{
  "instrument_type": "SO", "valuation_date": "2024-06-26", "instrument_id": 3003,
  "terms": { "grant_date": "2024-01-02", "grant_quantity": 100000,
    "exercise_price": 15000, "expected_term": 4.5 },
  "rights": {
    "vesting": { "schedule": [{ "date": "2026-01-02", "ratio": 0.5 }, { "date": "2027-01-02", "ratio": 0.5 }],
                 "cliff_month": 12, "forfeiture_rate": 5.0 }
  },
  "market": { "asset_id": 882, "spot": 15000, "volatility": 40.0, "dividend_yield": 1.0,
              "shares_outstanding": 8000000 },
  "curves": { "risk_free_curve": [[1, 3.35], [5, 3.40]], "credit_curve": null,
              "curve_source": "uploaded:2024-06-26/RF/v1", "curve_version": "v1",
              "interpolation_method": "linear", "fallback_policy": "error" },
  "model": "BSM", "seed": 20240101, "model_version": "so-1.0.0",
  "input_hash": "....", "metadata": { "issuer": "예시소프트" }
}
```

### 3.7 Conditional Stock Option ValuationContext 예시 (요지)

```json
{
  "instrument_type": "CSO", "valuation_date": "2024-06-26", "instrument_id": 3004,
  "terms": { "grant_date": "2024-01-02", "grant_quantity": 200000, "exercise_price": 15000, "expected_term": 5.0,
    "tranche": [{ "id": "T1", "quantity": 100000 }, { "id": "T2", "quantity": 100000 }] },
  "rights": {
    "vesting": { "schedule": [{ "date": "2027-01-02", "ratio": 1.0 }], "cliff_month": 24, "forfeiture_rate": 7.0 },
    "market_condition": { "type": "target_price", "target_price": 30000, "barrier": 30000, "knock": "in" },
    "performance_condition": { "metric": "revenue", "target": 100000000000, "probability": 0.6 }
  },
  "market": { "asset_id": 883, "spot": 15000, "volatility": 55.0, "dividend_yield": 0.0, "shares_outstanding": 10000000 },
  "curves": { "risk_free_curve": [[1, 3.35], [5, 3.40]], "credit_curve": null,
              "curve_source": "uploaded:2024-06-26/RF/v1", "curve_version": "v1",
              "interpolation_method": "linear", "fallback_policy": "error" },
  "model": "MONTE_CARLO", "seed": 20240101, "model_version": "cso-1.0.0",
  "options": { "simulation_paths": 200000, "variance_reduction": "antithetic" },
  "input_hash": "...."
}
```

### 3.8 input_hash 산정 대상/제외 필드

| 포함(해시 대상) | 제외(비대상) |
|---|---|
| instrument_type, valuation_date | input_hash 자신 |
| terms 전체 | metadata 전체(issuer·이름·요청자·시각) |
| rights 전체 | organization_id (접근제어용, 계산 무관) |
| market의 **수치값**(spot, volatility, dividend_yield, shares_outstanding) + as_of | market.data_source(자유문구), peer_companies 자유메모 |
| curves의 risk_free_curve·credit_curve **포인트**, interpolation_method, curve_version | curves.curve_source 표시문구(단, curve_version은 포함) |
| model, model_version, seed, options(lattice_steps·paths·variance_reduction) | instrument_id (동일조건 캐시 재사용 위해 제외; 식별은 job에서) |

> **설계 결정**: `seed`와 `model_version`은 결과를 바꾸므로 **해시에 포함**한다. 이로써 "동일 input_hash ⇒ 동일 PricingResult"가 성립하고, 캐시 키로 직접 사용 가능하다. `instrument_id`는 제외해 **동일 입력의 서로 다른 상품 인스턴스가 캐시를 공유**하도록 한다.

### 3.9 input_hash 생성 규칙

```python
import hashlib, json

def canonicalize(ctx: dict) -> dict:
    """해시 대상만 추출 + 정규화."""
    pick = {
        "instrument_type": ctx["instrument_type"],
        "valuation_date": ctx["valuation_date"],          # ISO 'YYYY-MM-DD'
        "terms": ctx["terms"],
        "rights": ctx["rights"],
        "market": {k: ctx["market"].get(k) for k in
                   ["asset_id","spot","volatility","dividend_yield","shares_outstanding","as_of"]},
        "curves": {
            "risk_free_curve": ctx["curves"]["risk_free_curve"],
            "credit_curve": ctx["curves"].get("credit_curve"),
            "interpolation_method": ctx["curves"]["interpolation_method"],
            "curve_version": ctx["curves"]["curve_version"],
        },
        "model": ctx["model"], "model_version": ctx["model_version"],
        "seed": ctx["seed"], "options": ctx.get("options", {}),
    }
    return round_floats(pick, ndigits=10)   # 부동소수 노이즈 제거(1e-10)

def input_hash(ctx: dict) -> str:
    canonical = canonicalize(ctx)
    blob = json.dumps(canonical, sort_keys=True, separators=(",", ":"),
                      ensure_ascii=False, default=str)  # 키정렬·공백제거·UTF-8
    return hashlib.sha256(blob.encode("utf-8")).hexdigest()
```

규칙 요약: (1) 해시 대상만 추출, (2) `sort_keys=True`·공백 제거, (3) 부동소수 10자리 반올림, (4) 날짜는 ISO 문자열, (5) 배열 순서 보존(커브 포인트), (6) SHA-256 hex 64자.

### 3.10 seed / model_version 관리 방식

| 항목 | 규칙 |
|---|---|
| seed | 기본값 `20240101`. 요청에 명시, MC/LSMC 전 경로 고정. 변경 시 결과·해시 모두 변경(의도된 재계산) |
| model_version | Calculator별 semver(`cb-1.0.0`). `model_versions` 테이블에서 관리, 결과에 태깅. 계산 로직 변경 시 MINOR/MAJOR 증가 + changelog 기록 |
| 호환성 | 동일 input_hash라도 model_version이 다르면 다른 결과로 간주(해시에 model_version 포함되므로 자동 분리) |

### 3.11 Backend → Engine 전달 전 정규화 항목

| 정규화 항목 | 처리 |
|---|---|
| 단위 | %는 숫자(8.0=8%)로 통일, 통화는 원 단위 정수/실수 통일 |
| 날짜 | 전부 ISO `YYYY-MM-DD` |
| 커브 참조 → 스냅샷 | `curves.*_ref`(폼의 curveSelector 선택)를 실제 포인트 배열로 치환(스냅샷 동결) |
| 기초자산 참조 → 값 | `market.asset_id`로 spot/vol/dividend 조회해 컨텍스트에 박제 |
| 빈 권리조건 제거 | `enabled:false`인 권리는 빈 객체로 정규화(해시 안정화) |
| 파생값 | parity 등 computed는 재계산해 일관화 |
| input_hash 생성 | 정규화 완료 후 마지막에 §3.9로 생성 |

### 3.12 Engine 재검증 항목

| 재검증 | 이유 |
|---|---|
| input_hash 재계산 일치 | 전송 중 변조·정규화 누락 탐지 |
| Pydantic `extra="forbid"` 스키마 통과 | 미정의 필드 차단 |
| 날짜 정합(만기>발행, 행사기간⊆만기) | 계산 전 부정합 차단 |
| model ↔ rights 호환(modelCompatibility) | 예: 리픽싱 有 + 순수격자 → LSMC 권고/거부 |
| 커브 단조성·음의 forward | 경고 발생(warnings에 적재) |
| 필수 market(spot/vol) 존재 | 주식연계 상품 필수 |


---

## 4. PricingResult / Result Schema 설계

### 4.1 전체 JSON Schema (`shared/schemas/pricing-result.schema.json`, draft 2020-12)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://fairvalue.dev/schemas/pricing-result.schema.json",
  "title": "PricingResult",
  "type": "object",
  "required": ["job_id", "instrument_id", "instrument_type", "valuation_date",
               "status", "total_fair_value", "components", "key_parameters",
               "reproducibility"],
  "additionalProperties": false,
  "properties": {
    "job_id": { "type": "integer" },
    "instrument_id": { "type": "integer" },
    "instrument_type": { "enum": ["RCPS", "CPS", "CB", "EB", "BW", "SO", "CSO"] },
    "valuation_date": { "type": "string", "format": "date" },
    "status": { "enum": ["DONE", "FAILED", "PARTIAL"] },
    "total_fair_value": { "type": ["number", "null"] },
    "per_unit_value": { "type": ["number", "null"] },
    "components": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "bond_value": { "type": ["number", "null"] },
        "preferred_share_value": { "type": ["number", "null"] },
        "conversion_option_value": { "type": ["number", "null"] },
        "exchange_option_value": { "type": ["number", "null"] },
        "warrant_value": { "type": ["number", "null"] },
        "redemption_option_value": { "type": ["number", "null"] },
        "issuer_call_value": { "type": ["number", "null"], "description": "음수(보유자 불리)" },
        "sale_claim_value": { "type": ["number", "null"], "description": "음수(보유자 불리)" },
        "stock_option_value": { "type": ["number", "null"] },
        "conditional_option_value": { "type": ["number", "null"] },
        "dilution_effect": { "type": ["number", "null"], "description": "음수(희석)" },
        "total_fair_value": { "type": ["number", "null"] }
      }
    },
    "key_parameters": {
      "type": "object",
      "properties": {
        "risk_free_rate": { "type": ["number", "null"] },
        "ytm": { "type": ["number", "null"] },
        "credit_spread": { "type": ["number", "null"] },
        "volatility": { "type": ["number", "null"] },
        "dividend_yield": { "type": ["number", "null"] },
        "parity": { "type": ["number", "null"] },
        "discount_rate": { "type": ["number", "null"] },
        "model_name": { "type": "string" },
        "model_version": { "type": "string" },
        "simulation_paths": { "type": ["integer", "null"] },
        "lattice_steps": { "type": ["integer", "null"] }
      }
    },
    "sensitivity_summary": {
      "type": ["object", "null"],
      "properties": {
        "tornado": { "type": "array", "items": { "type": "object", "properties": {
          "variable": { "type": "string" }, "down": { "type": "number" }, "up": { "type": "number" } } } }
      }
    },
    "scenario_summary": {
      "type": ["object", "null"],
      "properties": {
        "scenarios": { "type": "array", "items": { "type": "object", "properties": {
          "name": { "type": "string" }, "total_fair_value": { "type": "number" } } } }
      }
    },
    "audit_data": {
      "type": ["object", "null"],
      "properties": {
        "cashflow_schedule": { "type": "array", "items": { "type": "object" } },
        "discount_factors": { "type": "array", "items": { "type": "object" } },
        "curve_snapshot": { "type": "object" },
        "node_summary": { "type": "object" },
        "path_summary": { "type": "object" },
        "exercise_log": { "type": "array", "items": { "type": "object" } },
        "calculation_trace": { "type": "array", "items": { "type": "object" } },
        "input_snapshot_hash": { "type": "string" }
      }
    },
    "reproducibility": {
      "type": "object",
      "required": ["input_hash", "seed", "model_version"],
      "properties": {
        "input_hash": { "type": "string", "pattern": "^[a-f0-9]{64}$" },
        "seed": { "type": "integer" },
        "model_version": { "type": "string" },
        "engine_commit": { "type": ["string", "null"] },
        "computed_at": { "type": "string", "format": "date-time" }
      }
    },
    "warnings": { "type": "array", "items": { "$ref": "#/$defs/issue" } },
    "errors": { "type": "array", "items": { "$ref": "#/$defs/issue" } }
  },
  "$defs": {
    "issue": { "type": "object", "required": ["code", "message"], "properties": {
      "code": { "type": "string" }, "message": { "type": "string" },
      "field": { "type": ["string", "null"] }, "stage": { "type": ["string", "null"] } } }
  }
}
```

### 4.2 TypeScript interface (`shared/schemas/pricing-result.ts`)

```ts
export interface PricingResult {
  job_id: number;
  instrument_id: number;
  instrument_type: InstrumentType;
  valuation_date: string;
  status: 'DONE' | 'FAILED' | 'PARTIAL';
  total_fair_value: number | null;
  per_unit_value: number | null;
  components: Components;
  key_parameters: KeyParameters;
  sensitivity_summary?: { tornado: { variable: string; down: number; up: number }[] } | null;
  scenario_summary?: { scenarios: { name: string; total_fair_value: number }[] } | null;
  audit_data?: AuditData | null;
  reproducibility: Reproducibility;
  warnings: Issue[];
  errors: Issue[];
}

export interface Components {
  bond_value?: number | null;
  preferred_share_value?: number | null;
  conversion_option_value?: number | null;
  exchange_option_value?: number | null;
  warrant_value?: number | null;
  redemption_option_value?: number | null;
  issuer_call_value?: number | null;   // 음수
  sale_claim_value?: number | null;    // 음수
  stock_option_value?: number | null;
  conditional_option_value?: number | null;
  dilution_effect?: number | null;     // 음수
  total_fair_value?: number | null;
}

export interface KeyParameters {
  risk_free_rate?: number | null; ytm?: number | null; credit_spread?: number | null;
  volatility?: number | null; dividend_yield?: number | null; parity?: number | null;
  discount_rate?: number | null; model_name: string; model_version: string;
  simulation_paths?: number | null; lattice_steps?: number | null;
}

export interface AuditData {
  cashflow_schedule?: Record<string, unknown>[];
  discount_factors?: Record<string, unknown>[];
  curve_snapshot?: Record<string, unknown>;
  node_summary?: Record<string, unknown>;
  path_summary?: Record<string, unknown>;
  exercise_log?: Record<string, unknown>[];
  calculation_trace?: Record<string, unknown>[];
  input_snapshot_hash?: string;
}

export interface Reproducibility {
  input_hash: string; seed: number; model_version: string;
  engine_commit?: string | null; computed_at?: string;
}
export interface Issue { code: string; message: string; field?: string | null; stage?: string | null; }
```

### 4.3 Python Pydantic model (`pricing-engine/app/result.py`)

```python
from __future__ import annotations
from datetime import date, datetime
from typing import Literal, Optional
from pydantic import BaseModel, Field

class Components(BaseModel):
    bond_value: Optional[float] = None
    preferred_share_value: Optional[float] = None
    conversion_option_value: Optional[float] = None
    exchange_option_value: Optional[float] = None
    warrant_value: Optional[float] = None
    redemption_option_value: Optional[float] = None
    issuer_call_value: Optional[float] = None     # 음수
    sale_claim_value: Optional[float] = None      # 음수
    stock_option_value: Optional[float] = None
    conditional_option_value: Optional[float] = None
    dilution_effect: Optional[float] = None       # 음수
    total_fair_value: Optional[float] = None

class KeyParameters(BaseModel):
    risk_free_rate: Optional[float] = None
    ytm: Optional[float] = None
    credit_spread: Optional[float] = None
    volatility: Optional[float] = None
    dividend_yield: Optional[float] = None
    parity: Optional[float] = None
    discount_rate: Optional[float] = None
    model_name: str
    model_version: str
    simulation_paths: Optional[int] = None
    lattice_steps: Optional[int] = None

class AuditData(BaseModel):
    cashflow_schedule: list[dict] = Field(default_factory=list)
    discount_factors: list[dict] = Field(default_factory=list)
    curve_snapshot: dict = Field(default_factory=dict)
    node_summary: dict = Field(default_factory=dict)
    path_summary: dict = Field(default_factory=dict)
    exercise_log: list[dict] = Field(default_factory=list)
    calculation_trace: list[dict] = Field(default_factory=list)
    input_snapshot_hash: Optional[str] = None

class Reproducibility(BaseModel):
    input_hash: str
    seed: int
    model_version: str
    engine_commit: Optional[str] = None
    computed_at: Optional[datetime] = None

class Issue(BaseModel):
    code: str
    message: str
    field: Optional[str] = None
    stage: Optional[str] = None

class PricingResult(BaseModel):
    job_id: int
    instrument_id: int
    instrument_type: Literal["RCPS","CPS","CB","EB","BW","SO","CSO"]
    valuation_date: date
    status: Literal["DONE","FAILED","PARTIAL"]
    total_fair_value: Optional[float] = None
    per_unit_value: Optional[float] = None
    components: Components
    key_parameters: KeyParameters
    sensitivity_summary: Optional[dict] = None
    scenario_summary: Optional[dict] = None
    audit_data: Optional[AuditData] = None
    reproducibility: Reproducibility
    warnings: list[Issue] = Field(default_factory=list)
    errors: list[Issue] = Field(default_factory=list)
```

### 4.4 DB 분리 저장 매핑

| PricingResult 필드 | 저장 테이블 | 컬럼 |
|---|---|---|
| total_fair_value | pricing_results | total_fv |
| per_unit_value | pricing_results | per_unit |
| components | pricing_results | components(jsonb) |
| (total - 발행가 등 손익) | pricing_results | pnl |
| key_parameters.* | pricing_parameters | rfr·ytm·credit_spread·parity·discount_rate + 나머지 jsonb |
| reproducibility.input_hash | pricing_jobs | input_hash |
| reproducibility.seed | pricing_jobs | seed |
| reproducibility.model_version | pricing_jobs | model_version_id(→model_versions) |
| audit_data.cashflow_schedule | simulation_results | cashflows(jsonb) |
| audit_data.discount_factors | simulation_results | df_table(jsonb) |
| audit_data.node_summary | simulation_results | node_summary(jsonb) |
| audit_data.path_summary | simulation_results | path_summary(jsonb) |
| audit_data.exercise_log | simulation_results | exercise_log(jsonb) |
| audit_data.curve_snapshot·calculation_trace | simulation_results | 각 jsonb 컬럼 |
| sensitivity_summary / scenario_summary | pricing_jobs(연관) 또는 별도 분석 레코드 | jsonb |
| warnings / errors | pricing_results | warnings·errors(jsonb) |

### 4.5 Result Dashboard 표시 매핑

| 대시보드 영역 | PricingResult 필드 |
|---|---|
| 재현성 배지 | reproducibility.{input_hash, seed, model_version}, key_parameters.model_name |
| 요약 카드 | total_fair_value, per_unit_value |
| 구성요소 분해(부호색) | components.*(음수=적색·괄호) |
| 핵심 파라미터 | key_parameters.{risk_free_rate, ytm, credit_spread, volatility, dividend_yield, parity} |
| 토네이도 | sensitivity_summary.tornado |
| 시나리오 표 | scenario_summary.scenarios |
| 경고 배너 | warnings[] |
| 계산검증 탭(권한) | audit_data.* |

### 4.6 보고서/PDF 생성 매핑

| 보고서 섹션 | 사용 필드 |
|---|---|
| 평가 개요 | instrument_type, valuation_date, reproducibility.model_version |
| 주요 계약조건 | (ValuationContext.terms·rights 스냅샷) |
| 적용 파라미터 | key_parameters.* |
| 평가모형 | key_parameters.{model_name, model_version, lattice_steps, simulation_paths} |
| 평가 결과 | total_fair_value, per_unit_value, components.* |
| 민감도 분석 | sensitivity_summary.tornado |
| 계산근거 요약 | audit_data.{cashflow_schedule, discount_factors, exercise_log}(요약) |
| 한계·유의사항 | warnings[] + 모형 가정 |

### 4.7 CB PricingResult 예시

```json
{
  "job_id": 90011, "instrument_id": 3001, "instrument_type": "CB",
  "valuation_date": "2024-06-26", "status": "DONE",
  "total_fair_value": 167634.23, "per_unit_value": 11175.62,
  "components": {
    "bond_value": 8326.94, "conversion_option_value": 314301.52,
    "redemption_option_value": 2047.61, "issuer_call_value": -157041.84,
    "sale_claim_value": 0, "dilution_effect": 0, "total_fair_value": 167634.23
  },
  "key_parameters": {
    "risk_free_rate": 3.30, "ytm": 19.02, "credit_spread": 11.72,
    "volatility": 45.0, "dividend_yield": 0.0, "parity": 3260.00,
    "model_name": "TF_LATTICE", "model_version": "cb-1.0.0", "lattice_steps": 1000
  },
  "sensitivity_summary": { "tornado": [
    { "variable": "volatility", "down": 152300.1, "up": 184220.7 },
    { "variable": "spot", "down": 120110.0, "up": 219980.4 } ] },
  "reproducibility": { "input_hash": "a1b2c3...ff00", "seed": 20240101,
    "model_version": "cb-1.0.0", "engine_commit": "9f3c1a2", "computed_at": "2024-06-26T04:12:00Z" },
  "warnings": [], "errors": []
}
```

### 4.8 RCPS PricingResult 예시 (요지)

```json
{
  "job_id": 90012, "instrument_id": 3002, "instrument_type": "RCPS",
  "valuation_date": "2024-06-26", "status": "DONE",
  "total_fair_value": 24180.55, "per_unit_value": 24180.55,
  "components": {
    "preferred_share_value": 20619.00, "conversion_option_value": 4120.30,
    "redemption_option_value": 1300.25, "issuer_call_value": 0,
    "dilution_effect": 0, "total_fair_value": 24180.55 },
  "key_parameters": { "risk_free_rate": 3.45, "ytm": 10.20, "credit_spread": 6.75,
    "volatility": 50.0, "parity": 22000.0, "model_name": "LATTICE", "model_version": "rcps-1.0.0", "lattice_steps": 1000 },
  "reproducibility": { "input_hash": "....", "seed": 20240101, "model_version": "rcps-1.0.0" },
  "warnings": [{ "code": "W201", "message": "리픽싱 경로의존으로 LSMC 교차검증 권장", "field": "rights.refixing", "stage": "validate" }],
  "errors": []
}
```

### 4.9 Stock Option PricingResult 예시 (요지)

```json
{
  "job_id": 90013, "instrument_id": 3003, "instrument_type": "SO",
  "valuation_date": "2024-06-26", "status": "DONE",
  "total_fair_value": 456000000, "per_unit_value": 4560.0,
  "components": { "stock_option_value": 456000000, "total_fair_value": 456000000 },
  "key_parameters": { "risk_free_rate": 3.38, "volatility": 40.0, "dividend_yield": 1.0,
    "model_name": "BSM", "model_version": "so-1.0.0" },
  "audit_data": { "calculation_trace": [
    { "step": "BSM", "d1": 0.512, "d2": 0.193, "call": 4800.0 },
    { "step": "forfeiture_adjust", "rate": 5.0, "adjusted_qty": 95000 },
    { "step": "expense_schedule", "y2026": 216600000, "y2027": 239400000 } ] },
  "reproducibility": { "input_hash": "....", "seed": 20240101, "model_version": "so-1.0.0" },
  "warnings": [], "errors": []
}
```

### 4.10 components 부호 규칙 (보유자 관점 통일)

| 컴포넌트 | 부호 | 이유 |
|---|---|---|
| bond_value, preferred_share_value | **+** | host 가치(보유자 자산) |
| conversion_option_value, exchange_option_value, warrant_value | **+** | 보유자 유리 옵션 |
| redemption_option_value(풋) | **+** | 보유자 행사권 |
| stock_option_value, conditional_option_value | **+** | 보유자(임직원) 자산 |
| issuer_call_value(발행자 콜) | **−** | 발행자 권리 → 보유자 가치 차감 |
| sale_claim_value(매도청구권, 발행자/제3자) | **−** | 보유자에게 불리 |
| dilution_effect | **−** | 희석은 가치 감소 |
| total_fair_value | = 모든 컴포넌트 **부호 포함 합** | 불변식: Σcomponents == total |

### 4.11 warnings / errors 표준 코드 체계

| 코드 | 분류 | 의미 | severity |
|---|---|---|---|
| E001 | 입력 | 필수 커브 누락 | error(차단) |
| E002 | 입력 | 만기일 ≤ 발행일 | error |
| E003 | 입력 | 변동성/주가 누락(주식연계) | error |
| E004 | 라우팅 | model ↔ instrument_type 비호환 | error |
| E101 | 계산 | 격자/시뮬 수치 발산 | error |
| E102 | 계산 | input_hash 재계산 불일치 | error |
| W201 | 모형 | 리픽싱 경로의존 → LSMC 교차검증 권장 | warning(주석) |
| W202 | 커브 | 음의 forward rate 발생 | warning |
| W203 | 수렴 | MC 표준오차 임계 초과 | warning |
| W204 | 입력 | 비상장 변동성 수기 입력 사용 | warning |
| W205 | 회계 | 1102 비시장 성과조건 충족확률 수기 가정 | warning |

> 코드 체계: `E`(차단)·`W`(경고) + 3자리(0xx 입력, 1xx 계산, 2xx 모형/커브/회계). 신규 코드는 본 표에 append-only로 등록.


---

## 5. 7개 상품군 공통 필드 / 특수 필드 정리

> 공통 필드(전 상품): `valuation_date`, `terms.issue_date`, `market.{spot, volatility, dividend_yield}`(주식연계), `curves.risk_free_curve`, `model`, `seed`, `model_version`.

| 상품 | 공통 필드 | 특수 필드 | 필수 권리조건 | 선택 권리조건 | 기본 평가모형 | 결과 components |
|---|---|---|---|---|---|---|
| **RCPS** | 발행일·기준일·주가·변동성·무위험커브·신용커브 | issue_price, dividend_cumulative, guaranteed_yield(상환 IRR) | conversion, redemption(put), refixing | issuer_call, sale_claim | LATTICE(이항) | preferred_share, conversion_option, redemption_option, (issuer_call−), (sale_claim−) |
| **CPS** | 동일 | issue_price, host_type(dated/perpetual) | conversion | refixing | LATTICE / BSM+host PV | preferred_share, conversion_option |
| **CB** | 동일 | face_value, coupon_rate, coupon_freq_month, guaranteed_yield | conversion, redemption(put) | refixing, issuer_call, sale_claim, dilution | TF_LATTICE | bond, conversion_option, redemption_option, (issuer_call−), (sale_claim−), (dilution−) |
| **EB** | 발행일·기준일·**교환대상주** 주가·변동성·무위험·신용커브 | face_value, coupon_rate, coupon_freq_month | exchange, redemption(put) | issuer_call | DCF + 교환옵션(BSM/격자) | bond, exchange_option, redemption_option |
| **BW** | 발행일·기준일·주가·변동성·무위험·신용커브 | face_value, coupon_rate, warrant 정보 | warrant, redemption(put) | dilution, issuer_call | DCF + BSM(워런트) | bond, warrant, (dilution−), redemption_option |
| **Stock Option (SO)** | 기준일·주가·변동성·배당·무위험커브 | grant_date, grant_quantity, exercise_price, expected_term, shares_outstanding | vesting | — | BSM / 이항(조기행사) | stock_option |
| **Conditional SO (CSO)** | 동일 + tranche[] | 위 SO + 성과/시장조건 | vesting, market_condition 또는 performance_condition | barrier, true-up | MONTE_CARLO / LSMC | conditional_option |

---

## 6. Validation Rule 표준화

> `severity`: error=제출/계산 차단, warning=주석(진행 가능). Frontend(Zod)와 Backend가 **동일 rule 카탈로그**를 공유한다.

| rule | 설명 | 적용 필드 예시 | 오류 메시지 예시 | 차단 여부 |
|---|---|---|---|---|
| required | 필수값 존재 | valuation_date, conv_price | "필수 입력 항목입니다." | error |
| min | 최솟값 이상 | lattice_steps(min 100) | "100 이상이어야 합니다." | error |
| max | 최댓값 이하 | coupon_rate(max 100) | "100 이하여야 합니다." | error |
| positive | 0 초과 | issue_amount, spot | "0보다 커야 합니다." | error |
| dateOrder | 날짜 선후관계 | conv_start ≤ conv_end | "시작일이 종료일보다 늦을 수 없습니다." | error |
| dateWithin | 기간 내 포함 | exercise_date ∈ [issue,maturity] | "유효 기간을 벗어났습니다." | error |
| enum | 허용값 집합 | model, redemption_type | "허용되지 않은 값입니다." | error |
| percentageRange | % 범위 | coupon_rate, volatility | "0~100% 범위여야 합니다." | error |
| dependencyRequired | A 입력 시 B 필수 | use_refixing→refix_floor | "리픽싱 적용 시 하한가는 필수입니다." | error |
| mutuallyExclusive | 동시 입력 불가 | put.enabled vs call 동일조건 | "두 옵션을 동시에 설정할 수 없습니다." | error |
| showWhenRequired | 노출 시에만 필수 | sc_strike_pct(매도청구권 노출 시) | "해당 권리조건의 필수값입니다." | error |
| curveRequired | 커브 선택 필수 | risk_free_curve, credit_curve | "커브를 선택하세요." | error |
| assetRequired | 기초자산 매핑 필수 | market.asset_id | "기초자산을 선택하세요." | error |
| modelCompatibility | 모형↔조건 호환 | model vs rights | "선택 모형이 입력 권리조건과 호환되지 않습니다." | error |
| refixingFloorCheck | 0 ≤ floor ≤ init_strike | refix_floor | "하한가는 0 이상, 최초 전환가 이하여야 합니다." | error |
| maturityAfterIssueDate | 만기 > 발행 | maturity_date | "만기일은 발행일 이후여야 합니다." | error |
| exercisePeriodWithinMaturity | 행사기간 ⊆ 만기 | conv_start, warrant.end | "행사기간은 만기 이내여야 합니다." | error |
| volatilityPositive | 변동성 > 0 | market.volatility | "변동성은 0보다 커야 합니다." | error |
| pricePositive | 가격 > 0 | spot, exercise_price | "가격은 0보다 커야 합니다." | error |
| dilutionRange | 0 ≤ 희석계수 ≤ 1 | dilution.new_shares 비율 | "희석 비율이 유효 범위를 벗어났습니다." | warning |

---

## 7. Engine Routing 규칙

> 라우팅 키: `(instrument_type, model)`. `model` 미지정 시 기본 Calculator의 기본 model 사용. 비호환 조합은 `modelCompatibility`(E004)로 차단.

| instrument_type | 기본 Calculator | 기본 model | 사용 가능한 model | 필요한 권리조건 | 필요한 market data | 필요한 curve data |
|---|---|---|---|---|---|---|
| RCPS | RCPSCalculator | LATTICE | LATTICE, LSMC(리픽싱시), MONTE_CARLO(교차검증) | conversion, redemption.put, refixing | spot, volatility | risk_free, credit |
| CPS | CPSCalculator | LATTICE | LATTICE, BSM, LSMC | conversion | spot, volatility, dividend_yield | risk_free, (credit 선택) |
| CB | CBCalculator | TF_LATTICE | TF_LATTICE, LSMC(리픽싱시), MONTE_CARLO | conversion, redemption.put | spot, volatility | risk_free, credit |
| EB | EBCalculator | DCF | DCF+BSM, DCF+LATTICE, MONTE_CARLO | exchange | **교환대상주** spot, volatility | risk_free, credit |
| BW | BWCalculator | DCF | DCF+BSM, DCF+LATTICE | warrant | spot, volatility, shares_outstanding | risk_free, credit |
| SO | StockOptionCalculator | BSM | BSM, LATTICE(조기행사), MONTE_CARLO | vesting | spot, volatility, dividend_yield | risk_free |
| CSO | ConditionalStockOptionCalculator | MONTE_CARLO | MONTE_CARLO, LSMC | vesting + (market_condition ∨ performance_condition) | spot, volatility, shares_outstanding | risk_free |

```python
# pricing-engine/app/router.py (요지)
ROUTING = {
    "RCPS": (RCPSCalculator, "LATTICE", {"LATTICE", "LSMC", "MONTE_CARLO"}),
    "CPS":  (CPSCalculator,  "LATTICE", {"LATTICE", "BSM", "LSMC"}),
    "CB":   (CBCalculator,   "TF_LATTICE", {"TF_LATTICE", "LSMC", "MONTE_CARLO"}),
    "EB":   (EBCalculator,   "DCF", {"DCF", "BSM", "LATTICE", "MONTE_CARLO"}),
    "BW":   (BWCalculator,   "DCF", {"DCF", "BSM", "LATTICE"}),
    "SO":   (StockOptionCalculator, "BSM", {"BSM", "LATTICE", "MONTE_CARLO"}),
    "CSO":  (ConditionalStockOptionCalculator, "MONTE_CARLO", {"MONTE_CARLO", "LSMC"}),
}

def route(ctx: ValuationContext):
    calc_cls, default_model, allowed = ROUTING[ctx.instrument_type]
    model = ctx.model or default_model
    if model not in allowed:
        raise EngineError(code="E004", stage="route",
                          message=f"{ctx.instrument_type} 미지원 모형: {model}")
    # 리픽싱 경로의존 강제 전환 예시
    if ctx.rights.refixing and ctx.rights.refixing.get("enabled") and model in {"TF_LATTICE", "LATTICE"}:
        warn("W201")  # LSMC 교차검증 권고
    return calc_cls, model
```

---

## 8. 실제 파일 산출물 기준

### 8.1 추천 파일 구조

```
fairvalue-engine/
├─ shared/
│  └─ schemas/
│     ├─ form-schema.ts                 # 계약① 타입 정의(SSOT)
│     ├─ valuation-context.schema.json  # 계약② JSON Schema(SSOT)
│     ├─ valuation-context.ts           # 계약② TS interface(생성/수기 동기화)
│     ├─ pricing-result.schema.json     # 계약③ JSON Schema(SSOT)
│     ├─ pricing-result.ts              # 계약③ TS interface
│     └─ validation-rules.ts            # rule 카탈로그(§6) 공유
├─ frontend/
│  └─ src/forms/
│     ├─ renderer/                      # 스키마→폼 렌더러
│     ├─ productSchemas/
│     │  ├─ cb.json  rcps.json  cps.json  eb.json  bw.json  so.json  cso.json
│     └─ zodFromSchema.ts               # ValidationRule → Zod 변환기
├─ backend/
│  └─ src/main/
│     ├─ resources/openapi.yaml         # API 계약(요청/응답=위 schema 참조)
│     ├─ kotlin/.../context/Normalizer.kt   # rawForm→ValuationContext 정규화
│     └─ kotlin/.../context/InputHash.kt    # §3.9 해시 생성
├─ pricing-engine/
│  └─ app/
│     ├─ context.py                     # 계약② Pydantic(§3.3)
│     ├─ result.py                      # 계약③ Pydantic(§4.3)
│     ├─ router.py                      # §7 라우팅
│     ├─ reproducer.py                  # input_hash 재검증·seed·model_version
│     └─ calculators/                   # 상품별 Calculator
└─ docs/
   └─ contract-spec.md                  # 본 문서(변경은 PR+영역 전원리뷰)
```

### 8.2 파일별 역할

| 파일 | 역할 |
|---|---|
| `shared/schemas/form-schema.ts` | 계약① 타입 단일 소스. FE 렌더러·BE 검증참조가 import |
| `shared/schemas/valuation-context.schema.json` | 계약② 정본. BE 생성·Engine 소비 양쪽 검증 기준 |
| `shared/schemas/valuation-context.ts` / `pricing-result.ts` | TS 측 소비 타입(JSON Schema에서 생성 권장) |
| `shared/schemas/pricing-result.schema.json` | 계약③ 정본. Engine 반환·BE 저장·FE 표시 검증 |
| `shared/schemas/validation-rules.ts` | §6 rule 카탈로그. Zod 변환·서버 검증 공용 |
| `frontend/src/forms/renderer/` | FormSchema→Wizard/Field 렌더 |
| `frontend/src/forms/productSchemas/*.json` | 상품별 폼 스키마(§2.4~2.6) |
| `frontend/src/forms/zodFromSchema.ts` | ValidationRule→Zod 스키마 변환 |
| `backend/.../openapi.yaml` | REST 계약. request/response가 위 schema를 `$ref` |
| `backend/.../Normalizer.kt` | rawForm→ValuationContext 정규화(§3.11) |
| `backend/.../InputHash.kt` | 정규화 후 input_hash 생성(§3.9) — Engine과 동일 알고리즘 |
| `pricing-engine/app/context.py` / `result.py` | 계약②③ Pydantic 모델(런타임 검증) |
| `pricing-engine/app/router.py` | (instrument_type, model)→Calculator |
| `pricing-engine/app/reproducer.py` | input_hash 재검증·재현성 메타 |
| `docs/contract-spec.md` | 본 계약 문서. 변경 시 3계약 영향 검토 필수 |

> **동기화 주의**: input_hash 알고리즘이 **Backend(Kotlin)와 Engine(Python) 두 곳**에 구현된다. 동일 정규화·동일 직렬화(키정렬·공백제거·10자리 반올림)를 보장하는 **공유 테스트 벡터**(`shared/schemas/hash-test-vectors.json`)를 두고 양측이 같은 해시를 내는지 CI에서 교차검증한다.

---

## 9. 이 산출물 완료 후 이어서 요청할 다음 Claude 프롬프트 3개

**프롬프트 ①: 상품별 Form Schema 7종 완성 + Zod 변환기**
> "첨부한 3대 계약 명세(특히 §2 Dynamic Form Schema)를 기준으로, CPS·EB·BW·SO·CSO 5개 상품의 `productSchemas/*.json`을 CB·RCPS 예시와 동일한 수준으로 완성해줘. 각 상품의 모든 스텝·필드·showWhen·validations를 채우고, §6 ValidationRule 카탈로그를 Zod 스키마로 변환하는 `zodFromSchema.ts` 구현(각 rule→z 변환 매핑 포함)도 작성해줘. 모든 field의 `bind`가 §3 ValuationContext 경로와 1:1로 맞는지 검증표도 함께 만들어줘."

**프롬프트 ②: input_hash 정규화 + 양측 동일 구현 + 테스트 벡터**
> "첨부 계약의 §3.9 input_hash 규칙을 기준으로, Backend(Kotlin)와 Engine(Python) 양쪽에서 동일한 해시를 산출하는 정규화·직렬화 구현을 작성해줘. canonicalize/round_floats/직렬화 규칙을 양 언어로 구현하고, 동일 결과를 보장하기 위한 `hash-test-vectors.json`(CB·RCPS·SO 입력→기대 해시) 5케이스와, CI에서 교차검증하는 테스트 코드도 함께 작성해줘. 부동소수·날짜·빈 권리조건 정규화 엣지케이스를 반드시 포함해줘."

**프롬프트 ③: CB Calculator + TF-Lattice 골든값 검증 하니스**
> "첨부 계약의 ValuationContext(§3.4 CB 예시)를 입력으로 받아 PricingResult(§4.7)를 반환하는 `CBCalculator`와 `tf_lattice.py`의 클래스/메서드 골격을 작성해줘. TF 이중할인 backward induction, 콜/풋/전환 최적행사, 리픽싱 시 LSMC 전환, 매도청구권 독립평가, components 부호 규칙(§4.10)을 반영한 인터페이스를 정의하고, 문헌/Excel 골든값과 대조하는 regression 테스트 하니스(`tests/golden/cb_case1.json` 포맷 포함)도 함께 설계해줘. 수식 전체 구현보다 검증 가능한 구조·인터페이스·테스트 골격을 우선해줘."

---

*문서 끝. 본 3대 계약은 코드 착수 전 동결 대상이며, 변경 시 세 계약의 상호 영향(특히 field.bind ↔ ValuationContext 경로, components ↔ DB 저장, input_hash 양측 구현)을 반드시 함께 검토한다.*
