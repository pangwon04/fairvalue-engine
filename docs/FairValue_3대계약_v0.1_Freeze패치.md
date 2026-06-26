# FairValue Engine — 3대 계약 v0.1 Freeze 패치 (Pre-Freeze Patch)

> **목적**: 「파일화 전 최종 검수」에서 식별된 블로킹 3건(B-1·B-2·B-3) + 보완 5건을 반영해 **실제 파일화 가능한 v0.1 계약**으로 확정.
> **기준 문서**: PRD · 실행계획서 · 3대 계약 명세 · 최종 검수 문서.
> **사용처**: Claude Cowork가 실제 파일(`.ts`/`.schema.json`/`.py`/`openapi.yaml`)을 생성할 때의 단일 기준.
> **전제 유지**: 7상품(RCPS·CPS·CB·EB·BW·SO·CSO) 전부 E2E, 상품 제외·축소 없음, 독립 B2B 구조.

---

# 1. 최우선 블로킹 이슈 패치

## B-1. RCPS PricingResult 예시 합계 보정

### 1.1 무엇을 바꿨나

| 항목 | 기존(오류) | 수정 후 | Δ | 근거 |
|---|---|---|---|---|
| `preferred_share_value` | 20,619.00 | **18,760.00** | **−1,859.00** | 기존 값이 주당 발행가(20,619)와 동일해 host 가치를 과대표기. 우선배당 PV + 상환 floor의 현재가치로 보정. Δ가 정확히 불일치액(1,859.00)과 일치 |
| `conversion_option_value` | 4,120.30 | 4,120.30 | 0 | 유지 |
| `redemption_option_value` | 1,300.25 | 1,300.25 | 0 | 유지 |
| `issuer_call_value` | 0 | 0 | 0 | 유지 |
| `dilution_effect` | 0 | 0 | 0 | 유지 |
| `total_fair_value` | 24,180.55 | 24,180.55 | 0 | **유지**(기존 total을 정본으로 삼고 components를 맞춤) |

> 수정 원칙: total을 흔들면 보고서·민감도 기준이 함께 움직이므로 **total을 고정**하고 `preferred_share_value`를 보정했다.

### 1.2 합계 검증식 (불변식 §4.10)

```
Σ(components 제외 total_fair_value) == total_fair_value
18,760.00 + 4,120.30 + 1,300.25 + 0 + 0 = 24,180.55 == total_fair_value(24,180.55) ✓
```

### 1.3 RCPS Golden Fixture (`golden-values/rcps_case1.json`)

```json
{
  "fixture_id": "rcps_case1",
  "instrument_type": "RCPS",
  "valuation_date": "2024-06-26",
  "expected": {
    "total_fair_value": 24180.55,
    "per_unit_value": 24180.55,
    "components": {
      "preferred_share_value": 18760.00,
      "conversion_option_value": 4120.30,
      "redemption_option_value": 1300.25,
      "issuer_call_value": 0,
      "sale_claim_value": 0,
      "dilution_effect": 0,
      "total_fair_value": 24180.55
    },
    "key_parameters": {
      "risk_free_rate": 3.45, "ytm": 10.20, "credit_spread": 6.75,
      "volatility": 50.0, "parity": 22000.0,
      "model_name": "LATTICE", "model_version": "rcps-1.0.0", "lattice_steps": 1000
    }
  },
  "tolerance": { "absolute": 0.01, "relative": 1e-6 },
  "invariants": ["sum_components_equals_total", "sign_rules_v1"]
}
```

> CI는 `sum_components_equals_total`을 `tolerance.absolute` 내에서 강제한다. CB 예시(167,634.23)는 이미 합계 정합이므로 동일 포맷으로 `cb_case1.json`도 동결한다.

---

## B-2. 커브 selector bind 경로 충돌 → rawForm / final 분리 + `resolve` 마커

### 2.1 핵심 결정: 2단계 네임스페이스

| 단계 | 명칭 | 생성자 | 커브 표현 | 검증 스키마 |
|---|---|---|---|---|
| 입력 단계 | **rawForm** | Frontend 폼 | `curves.risk_free_ref`, `curves.credit_ref` (= 커브 `upload_id` 참조) | `valuation-context.draft.schema.json` |
| 계산 단계 | **final ValuationContext** | Backend Normalizer | `curves.risk_free_curve`, `curves.credit_curve` (포인트 배열 스냅샷) | `valuation-context.schema.json` |

### 2.2 `bind` 불변식 (수정본)

> **(구)** "모든 입력 field는 ValuationContext의 단일 경로에 1:1 매핑된다."
> **(신, 확정)** "모든 입력 field는 **rawForm 경로**에 매핑된다. 일부 selector field(`curveSelector`·`assetSearch`)는 `resolve` 규칙에 따라 Backend Normalizer가 **final ValuationContext 경로**로 변환한다. `computed`/`readonly` field는 어떤 경로에도 bind하지 않는다."

### 2.3 FieldSchema에 `resolve` 마커 추가 (`shared/schemas/form-schema.ts`)

```ts
export interface ResolveSpec {
  to: string;                 // final ValuationContext 경로. 예: 'curves.risk_free_curve'
  kind: 'curve' | 'asset';    // Normalizer가 적용할 치환 규칙
  // curve: upload_id → 포인트 배열 스냅샷
  // asset: asset_id → market.{spot,volatility,dividend_yield} 보강(수동값 우선, §2.2)
}

export interface FieldSchema {
  key: string;
  label: string;
  type: FieldType;
  unit?: '%' | '원' | '주' | 'bp' | string;
  required?: boolean;
  defaultValue?: Primitive;
  placeholder?: string;
  help?: string;
  options?: { label: string; value: string | number }[];
  columns?: ColumnSchema[];
  showWhen?: Condition;
  enableWhen?: Condition;
  validations?: ValidationRule[];
  bind?: string;              // rawForm 경로 (예: 'curves.risk_free_ref')
  resolve?: ResolveSpec;      // ★신규: rawForm → final 변환 규칙
  computeFrom?: string[];     // computed/readonly 전용 (bind 금지)
}
```

### 2.4 패치된 커브 selector 필드 (CB·RCPS 공통)

```json
{ "key": "risk_free_curve", "label": "무위험수익률 커브", "type": "curveSelector",
  "required": true, "bind": "curves.risk_free_ref",
  "resolve": { "to": "curves.risk_free_curve", "kind": "curve" },
  "validations": [{ "rule": "curveRequired", "params": { "kind": "RISK_FREE" },
    "message": "무위험 커브를 선택하세요.", "severity": "error" }] },
{ "key": "credit_curve", "label": "신용등급 커브", "type": "curveSelector",
  "required": true, "bind": "curves.credit_ref",
  "resolve": { "to": "curves.credit_curve", "kind": "curve" },
  "validations": [{ "rule": "curveRequired", "params": { "kind": "CREDIT" },
    "message": "신용 커브를 선택하세요.", "severity": "error" }] }
```

### 2.5 rawForm ↔ final ValuationContext 매핑표

| rawForm 경로 | resolve.kind | final ValuationContext 경로 | 변환 규칙 | input_hash 대상 |
|---|---|---|---|---|
| `curves.risk_free_ref` | curve | `curves.risk_free_curve` (+ `curve_source`, `curve_version`) | upload_id → 포인트 배열 스냅샷 동결 | **final 포인트**가 대상(ref는 비대상) |
| `curves.credit_ref` | curve | `curves.credit_curve` (+ source/version) | 동일 | 동일 |
| `market.asset_id` | asset | `market.asset_id`(유지) + `market.{spot,volatility,dividend_yield}` 보강 | asset master 조회, **수동값 우선**(§2.2) | spot/vol/div 수치값이 대상 |
| (그 외 전 field) | — | `bind` 경로 그대로 | 변환 없음 | §2.5 명세 규칙 |

### 2.6 draft 스키마 vs final 스키마 차이

| 항목 | `valuation-context.draft.schema.json` (rawForm) | `valuation-context.schema.json` (final) |
|---|---|---|
| 커브 | `curves.risk_free_ref`(int/str), `credit_ref` 허용 | `risk_free_curve` 포인트 배열 필수, `*_ref` **불허** |
| `input_hash` | 미요구(아직 없음) | **required** |
| `model_version` | 미요구 | **required** |
| `additionalProperties` | curves에 한해 ref 허용 | `false`(엄격) |
| 용도 | 폼 제출 검증 | 엔진 전달 직전 검증 |

---

## B-3. components 명칭 표준 사전

### 3.1 표준 키 (정본) — 이 외 명칭 사용 금지

| 표준 key | 의미 | 부호 | 적용 상품 |
|---|---|---|---|
| `bond_value` | 채권(host) 가치 | + | CB·EB·BW |
| `preferred_share_value` | 우선주(host) 가치 | + | RCPS·CPS |
| `conversion_option_value` | 전환옵션 | + | RCPS·CPS·CB |
| `exchange_option_value` | 교환옵션(타사주) | + | EB |
| `warrant_value` | 신주인수권 | + | BW |
| `redemption_option_value` | 상환권(풋) | + | RCPS·CB·EB·BW |
| `issuer_call_value` | 발행자 콜 | **−** | RCPS·CB |
| `sale_claim_value` | 매도청구권(발행자/제3자) | **−** | RCPS·CB |
| `stock_option_value` | 주식매수선택권 | + | SO |
| `conditional_option_value` | 조건부 SO | + | CSO |
| `dilution_effect` | 희석효과 | **−** | BW·CB |
| `total_fair_value` | 총공정가치(= 나머지 부호포함 합의 echo) | = | 전 상품 |

### 3.2 약식 → 표준 매핑 사전 (`components-dictionary`)

| 기존 약식 명칭(검수 §5 등) | 표준 key |
|---|---|
| bond | `bond_value` |
| preferred / preferred_share | `preferred_share_value` |
| conversion / conversion_option | `conversion_option_value` |
| exchange / exchange_option | `exchange_option_value` |
| warrant | `warrant_value` |
| redemption / redemption_option | `redemption_option_value` |
| issuer_call | `issuer_call_value` |
| sale_claim | `sale_claim_value` |
| stock_option | `stock_option_value` |
| conditional / conditional_option | `conditional_option_value` |
| dilution | `dilution_effect` |
| total | `total_fair_value` |

> 적용: 검수 §5 커버리지표, 명세 §5, 모든 예시·DB jsonb·프론트 렌더가 위 표준 key만 사용한다.

---

# 2. 추가 보완사항 반영

## 2.1 parity 입력 제거

| 변경 | 내용 |
|---|---|
| Form Schema | `parity` field는 `type: "computed"`, **`bind` 제거**, `computeFrom: ["spot","conv_price"]`만 유지(화면 표시용) |
| final ValuationContext | `rights.conversion.parity` **삭제**(스키마·TS `Conversion`에서 제외) |
| input_hash | parity 미포함(파생값 오염 차단) |
| PricingResult | parity는 **`key_parameters.parity`에만** 존재(엔진 산출) |

패치된 parity 필드:
```json
{ "key": "parity", "label": "패리티(자동계산)", "type": "computed",
  "computeFrom": ["spot", "conv_price"], "help": "spot / conv_price × 액면 (표시 전용)" }
```

## 2.2 assetSearch 자동채움 vs 수동입력 우선순위

| 규칙 | 내용 |
|---|---|
| 우선순위 | **수동 입력값 우선(override)**, 비어 있으면 asset master 자동값 사용 |
| 적용 필드 | `market.spot`, `market.volatility`, `market.dividend_yield` |
| 처리 위치 | Backend Normalizer(`resolve.kind="asset"`) |
| 경고 | 수동값이 asset master 값과 유의하게 다르면 `W206` 발생(차단 아님) |

신규 경고 코드:
| 코드 | 분류 | 의미 | severity |
|---|---|---|---|
| `W206` | 입력 | 기초자산 자동값을 수기 입력이 override함(편차 기록) | warning |

## 2.3 enum 단일 소스화 (`shared/schemas/instrument-types.ts`)

```ts
// 단일 소스. form-schema.ts / valuation-context.ts / pricing-result.ts 는 import만.
export type InstrumentType = 'RCPS' | 'CPS' | 'CB' | 'EB' | 'BW' | 'SO' | 'CSO';
export type ModelName =
  'TF_LATTICE' | 'LATTICE' | 'MONTE_CARLO' | 'LSMC' | 'BSM' | 'DCF';  // TRINOMIAL 제거(미사용)
export type ExerciseStyle = 'AMERICAN' | 'EUROPEAN' | 'BERMUDAN';
export type CurveKind = 'RISK_FREE' | 'CREDIT';
export type RedemptionSide = 'PUT' | 'CALL';
export type JobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED' | 'PARTIAL';
export const INSTRUMENT_TYPES: InstrumentType[] =
  ['RCPS','CPS','CB','EB','BW','SO','CSO'];
```
> 규칙: 위 타입을 다른 파일에서 **재선언 금지, import만**. CI에 중복 선언 린트 추가.

## 2.4 warnings / errors 정합성 (3측 동일, 기본 `[]`)

| 위치 | 처리 |
|---|---|
| JSON Schema | `warnings`·`errors`를 `required`에 추가, 각 `"default": []` |
| TypeScript | `warnings: Issue[]; errors: Issue[];` (옵셔널 아님, 호출부에서 `[]` 기본) |
| Pydantic | `warnings: list[Issue] = Field(default_factory=list)` (errors 동일) |

JSON Schema 패치 조각:
```json
"required": ["job_id","instrument_id","instrument_type","valuation_date",
             "status","total_fair_value","components","key_parameters",
             "reproducibility","warnings","errors"],
"properties": {
  "warnings": { "type": "array", "items": { "$ref": "#/$defs/issue" }, "default": [] },
  "errors":   { "type": "array", "items": { "$ref": "#/$defs/issue" }, "default": [] }
}
```

## 2.5 input_hash 구현 정합성 (Python ↔ Backend 동일)

### 정규화·직렬화 규칙 (양 언어 공통 의사규약)

| 단계 | 규칙 |
|---|---|
| 1. 대상 추출 | §명세 3.8 포함 필드만 추출(rights/terms/market 수치/curves 포인트·interpolation·version/model/model_version/seed/options). ref·metadata·org_id·instrument_id·source 문구 제외 |
| 2. null 제거 | 값이 `null`/`None`인 키는 **삭제**(존재/부재 차이로 인한 해시 흔들림 방지) |
| 3. 빈 권리조건 | `enabled:false`인 권리 객체는 `{}`(빈 객체)로 정규화 후, 그 안의 잔여 키 삭제 |
| 4. float 반올림 | `round_floats(x, 10)`: 모든 number를 소수 10자리 반올림(예: `8.0000000001`→`8.0`). 정수는 그대로 |
| 5. 날짜 | 전부 ISO `YYYY-MM-DD` 문자열 |
| 6. 배열 | 순서 보존(커브 포인트 `[[tenor,rate],...]`는 정렬하지 않음) |
| 7. 직렬화 | `sort_keys=true`, 구분자 `(",",":")`(공백 없음), `ensure_ascii=false`(UTF-8) |
| 8. 해시 | `SHA-256` → 소문자 hex 64자 |

```python
# pricing-engine/app/reproducer.py (정본) — Backend Kotlin도 동일 로직 구현
import hashlib, json
from decimal import Decimal

def round_floats(o, nd=10):
    if isinstance(o, float):  return round(o, nd)
    if isinstance(o, dict):   return {k: round_floats(v, nd) for k, v in o.items() if v is not None}
    if isinstance(o, list):   return [round_floats(v, nd) for v in o]
    return o

def normalize_rights(rights: dict) -> dict:
    out = {}
    for k, v in (rights or {}).items():
        if isinstance(v, dict) and v.get("enabled") is False:
            out[k] = {}                      # 비활성 권리 → 빈 객체
        elif v is not None:
            out[k] = v
    return out

def canonicalize(ctx: dict) -> dict:
    pick = {
      "instrument_type": ctx["instrument_type"],
      "valuation_date": ctx["valuation_date"],
      "terms": ctx["terms"],
      "rights": normalize_rights(ctx["rights"]),
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
    return round_floats(pick, 10)            # null 제거는 round_floats 내부에서 수행

def input_hash(ctx: dict) -> str:
    blob = json.dumps(canonicalize(ctx), sort_keys=True,
                      separators=(",", ":"), ensure_ascii=False, default=str)
    return hashlib.sha256(blob.encode("utf-8")).hexdigest()
```

### 테스트 벡터 요구 (`shared/schemas/hash-test-vectors.json`, 5케이스)

| # | 케이스 | 검증 의도 |
|---|---|---|
| TV1 | CB 기본(리픽싱 無) | 표준 경로 |
| TV2 | CB 리픽싱 有 | 권리 객체 정규화 |
| TV3 | RCPS(상환·우선배당) | 우선주형 terms |
| TV4 | SO(만기 null, expected_term) | null 제거·주식보상형 |
| TV5 | CSO(tranche·market/perf condition) | 중첩 배열·조건 객체 |

> 각 케이스는 `{ "name", "input(final context)", "canonical_blob", "expected_hash" }`. `expected_hash`는 정본 구현으로 1회 생성해 동결하고, **Kotlin·Python 양측이 동일 hash를 내는지 CI에서 교차검증**한다.

---

# 3. 수정 후 산출물

## 3.1 Freeze Patch Summary

| 이슈 | 기존 문제 | 수정 방향 | 수정 후 기준 | 관련 파일 |
|---|---|---|---|---|
| **B-1** | RCPS 예시 Σcomponents(26,039.55) ≠ total(24,180.55) | `preferred_share_value` 20,619.00→18,760.00, total 고정 | Σ=total(24,180.55) 정합, golden fixture화 | `golden-values/rcps_case1.json`, `pricing-result.schema.json` |
| **B-2** | 폼 `curves.*_ref`가 final context에 없음(1:1 불변식 충돌) | rawForm/final 2단계 분리 + `resolve` 마커 + draft 스키마 | 폼 bind는 rawForm, Normalizer가 resolve로 포인트 치환 | `form-schema.ts`, `valuation-context.draft.schema.json`, `valuation-context.schema.json` |
| **B-3** | `bond` vs `bond_value` 등 동일값 이중명칭 | 표준 key 12종 확정 + 약식→표준 사전 | 전 문서·DB·프론트가 표준 key만 사용 | `pricing-result.schema.json`, `pricing-result.ts`, `result.py` |
| 2.1 parity | 입력·출력 양쪽 존재(해시 오염) | 입력 bind 제거, computed 표시만 | parity는 `key_parameters.parity`에만 | `form-schema.ts`, `valuation-context.*`, `result.py` |
| 2.2 asset 우선순위 | 자동채움 vs 수동 미정 | 수동 override, 부재 시 자동 | Normalizer 규칙 + `W206` | Normalizer, `validation-rules.ts`(코드표) |
| 2.3 enum | InstrumentType 등 3중 선언 | `instrument-types.ts` 단일 소스 | 타 파일 import만, 재선언 0 | `instrument-types.ts` 외 전부 |
| 2.4 warnings/errors | schema↔TS↔Pydantic 불일치 | required + default `[]` 통일 | 3측 동일 | `pricing-result.schema.json`, `.ts`, `result.py` |
| 2.5 input_hash | round_floats 미정의, null/빈권리 규칙 모호 | 정규화 8단계 확정 + 테스트벡터 5 | 양 언어 동일 hash(CI 교차검증) | `reproducer.py`, `InputHash.kt`, `hash-test-vectors.json` |

## 3.2 v0.1 Freeze 기준

| Freeze 항목 | 통과 기준 | 확인 방법 | 실패 시 영향 |
|---|---|---|---|
| F-1 합계 불변식 | 전 예시·fixture에서 Σcomponents=total(허용오차 0.01) | `result.py` `@model_validator` + CI | 합계검증 전면 실패 |
| F-2 rawForm/final 분리 | 폼 bind는 draft 스키마 통과, 치환 후 final 스키마 통과 | draft/final 두 스키마 검증 + `verify-bind` | 폼↔엔진 전 상품 실패 |
| F-3 component 표준 key | 표준 12종 외 키 사용 0건 | 사전 대조 린트 | 직렬화/DB 키 불일치 |
| F-4 parity 제거 | final context·input_hash에 parity 부재 | 스키마 `additionalProperties:false` + 해시 입력 점검 | 파생값 해시 오염 |
| F-5 enum 단일소스 | 타 파일 재선언 0 | import 린트 | enum drift |
| F-6 warnings/errors | 3측 required+default[] 동일 | 스키마/타입 대조 테스트 | null/누락 분기 |
| F-7 input_hash 양측 동일 | TV1~TV5 Kotlin=Python hash 일치 | CI 교차검증 | 캐시 오적중·재현성 붕괴 |
| F-8 부호 규칙 | §B-3 부호표 위반 시 에러 | `result.py` validator | 잘못된 결과 통과 |

## 3.3 Cowork 파일화 작업 기준 (W1~W10, 착수 전 W1~W7 우선)

| 작업 단위 | 생성/수정 파일 | 선행 조건 | 완료 기준 | 주의사항 |
|---|---|---|---|---|
| **W1** 공통 enum 단일소스 | `shared/schemas/instrument-types.ts` | — | InstrumentType·ModelName·ExerciseStyle·CurveKind·JobStatus export | TRINOMIAL 제외. 타 파일 재선언 금지 |
| **W2** Form Schema 타입 | `shared/schemas/form-schema.ts` | W1 | `ResolveSpec`·`resolve` 포함 컴파일 통과 | InstrumentType은 W1 import |
| **W3** Validation 카탈로그 | `shared/schemas/validation-rules.ts` | W1 | 20종 rule + params 타입 + severity + `W206` 코드표 | 폼/서버 공용 |
| **W4** ValuationContext 스키마/타입 | `valuation-context.schema.json`(final), `valuation-context.draft.schema.json`, `valuation-context.ts` | W1 | full=resolved·hash required / draft=ref 허용·hash 미요구. parity 제거 | 두 스키마 차이 §2.6 준수 |
| **W5** PricingResult 스키마/타입 | `pricing-result.schema.json`, `pricing-result.ts` | W1 | 표준 component key, warnings/errors required+default[] | components.total은 echo 주석 |
| **W6** Engine Pydantic | `pricing-engine/app/context.py`, `result.py` | W4,W5 | `extra="forbid"`, Σ=total·부호 `@model_validator`, parity 입력 불가 | 스키마와 1:1 |
| **W7** input_hash 공용 구현·벡터 | `pricing-engine/app/reproducer.py`, `backend/.../InputHash.kt`, `shared/schemas/hash-test-vectors.json` | W4 | TV1~TV5 양측 hash 일치(CI) | round_floats·null·빈권리 §2.5 동일 |
| W8 7개 상품 폼 스키마 | `frontend/src/forms/productSchemas/{cb,rcps,cps,eb,bw,so,cso}.json` | W2,W3 | bind가 매핑표와 100% 일치, resolve 마커 적용 | 착수 후 단계 |
| W9 OpenAPI 계약 | `backend/src/main/resources/openapi.yaml` | W4,W5 | price/result/curve I/O가 공유 스키마 `$ref` | 착수 후 단계 |
| W10 bind 일치 검증기 | `shared/scripts/verify-bind.ts` | W2,W4,W8 | 전 폼 bind↔(rawForm/final) 자동대조 통과 | CI 게이트 |

> **이번 Freeze 단계 범위 = W1~W7.** W8~W10은 freeze 확정 후 착수. frontend/backend 앱 전체 스캐폴딩은 이 단계에서 하지 않는다.

## 3.4 내가(사용자) 직접 수행해야 할 작업

| 구분 | 내가 해야 할 작업 | 목적 | 선행 조건 | 완료 기준 | Cowork/Claude 연결 | 우선순위 |
|---|---|---|---|---|---|---|
| 문서 | PRD·실행계획서·3대계약명세·검수문서를 `docs/`에 정리 | 단일 기준 보관 | — | `docs/`에 4문서 + 본 패치 커밋 | Cowork가 `docs/` 참조 | 상 |
| 승인 | **B-1·B-2·B-3 패치안 승인** | freeze 전제 확정 | 본 문서 검토 | 3건 명시 승인 | 미승인 시 W1~W7 보류 | 상 |
| 결정 | 기술스택 최종 선택(Kotlin/Spring·Next.js·FastAPI·PostgreSQL 확정) | 파일 골격 고정 | — | 스택 확정 기록 | W6/W7 언어 결정 | 상 |
| 승인 | Cowork 생성 **파일 목록 승인**(W1~W7) | 산출물 범위 합의 | 본 §3.3 | 파일 리스트 OK | Cowork 실행 트리거 | 상 |
| 승인 | **기존 파일 덮어쓰기 승인** | 무단 변경 방지 | 변경계획 확인 | 덮어쓰기 대상 확정 | Cowork가 계획 먼저 제시 | 상 |
| 데이터 | **CB 샘플 계약조건 준비** | golden·데모 입력 | — | `cb_case1` 입력값 확정 | W7 TV1/TV2·`cb_case1.json` | 상 |
| 데이터 | **RCPS 샘플 계약조건 준비** | golden·데모 입력 | — | `rcps_case1` 입력값 확정 | W7 TV3·B-1 fixture | 상 |
| 데이터 | **샘플 등급 커브 CSV 준비**(무위험·신용) | resolve·부트스트랩 입력 | — | CSV 2종(만기·금리) | W4 resolve 검증 | 상 |
| 데이터 | **Golden Value 기준값 준비**(Excel 수기) | 엔진 정합 검증 | 샘플 계약조건 | CB·RCPS 기대값 확정 | W6 validator·CI | 상 |
| 검수 | **생성된 shared schema 파일 검수** | 계약 무결성 확인 | W1~W5 완료 | 스키마 리뷰 승인 | Claude/Cowork 보고 검토 | 중 |
| 운영 | **Git commit 단위 관리**(W1~W7 분리 커밋) | 추적성 | 파일 생성 | 작업단위별 커밋 | Cowork PR 단위 | 중 |

## 3.5 Cowork에 전달할 최종 실행 프롬프트

> **"첨부한 「3대 계약 v0.1 Freeze 패치」 문서를 단일 기준으로, 실제 파일 생성 작업 W1~W7만 수행해줘. frontend/backend 앱 전체 스캐폴딩(Next.js·Spring 프로젝트 생성 등)은 이번에 하지 말고, 아래 7개 작업의 대상 파일만 만들어줘.**
>
> **대상: W1 `shared/schemas/instrument-types.ts`, W2 `shared/schemas/form-schema.ts`, W3 `shared/schemas/validation-rules.ts`, W4 `shared/schemas/valuation-context.schema.json`+`valuation-context.draft.schema.json`+`valuation-context.ts`, W5 `shared/schemas/pricing-result.schema.json`+`pricing-result.ts`, W6 `pricing-engine/app/context.py`+`result.py`, W7 `pricing-engine/app/reproducer.py`+`backend/src/main/resources/InputHash.kt`+`shared/schemas/hash-test-vectors.json`.**
>
> **반드시 반영할 패치: (B-1) RCPS 예시·fixture는 preferred_share_value=18,760.00로 Σ=total(24,180.55) 정합. (B-2) Form Schema에 `resolve` 마커 추가, 커브 selector는 rawForm `curves.risk_free_ref`/`credit_ref`에 bind하고 final 스키마는 `risk_free_curve`/`credit_curve` 포인트 배열만 허용(draft 스키마는 ref 허용·input_hash 미요구). (B-3) component key는 표준 12종(`bond_value`…`total_fair_value`)만 사용. (2.1) parity는 computed 표시만, final context·input_hash에서 제외하고 `key_parameters.parity`에만. (2.3) InstrumentType·ModelName·ExerciseStyle은 `instrument-types.ts`에서만 선언하고 나머지는 import. (2.4) warnings/errors는 schema·TS·Pydantic 모두 required+기본 `[]`. (2.5) input_hash는 §2.5의 정규화 8단계를 Kotlin·Python 동일하게 구현하고 `hash-test-vectors.json`에 TV1~TV5를 만들어 양측 동일 hash를 CI에서 교차검증하도록.**
>
> **작업 순서: (1) 먼저 생성/수정할 파일 목록과, 기존 파일이 있으면 변경 계획(diff 요지)을 표로 보여주고 내 승인을 기다린다. (2) 승인 후 W1→W7 순서로 생성한다. (3) 생성 후 파일별 요약과 §3.2 Freeze 기준(F-1~F-8) 체크리스트를 표로 보고한다. (4) B-1·B-2·B-3 패치가 반영됐는지 자가검증(RCPS 합계식 계산 결과, `*_ref`가 final 스키마에서 거부되는지, 표준 component key만 쓰였는지)을 함께 보고한다. 기존 파일 덮어쓰기는 내 승인 전에는 하지 않는다."**

---

# 4. v0.1 freeze 전 내가 승인해야 할 항목 (요약)

| # | 승인 항목 | 승인하면 잠기는 것 |
|---|---|---|
| 1 | **B-1** RCPS preferred_share_value=18,760.00 보정 | RCPS golden fixture 합계 기준 |
| 2 | **B-2** rawForm/final 분리 + `resolve` 마커 + draft 스키마 도입 | 폼↔엔진 경로 계약 |
| 3 | **B-3** component 표준 key 12종 + 약식→표준 사전 | 결과 직렬화/DB/프론트 키 |
| 4 | **2.1** parity 입력 제거(결과 전용) | input_hash 입력 범위 |
| 5 | **2.3** enum 단일 소스(`instrument-types.ts`) | 타입 선언 위치 |
| 6 | **2.5** input_hash 정규화 8단계 + 테스트벡터 5 | 재현성·캐시 키 |
| 7 | 기술스택 최종(Kotlin/Spring·Next.js·FastAPI·PostgreSQL) | W6/W7 구현 언어 |
| 8 | Cowork 생성 파일 목록(W1~W7) + 덮어쓰기 범위 | 이번 파일화 범위 |
| 9 | CB·RCPS 샘플 계약조건 + 등급 커브 CSV + Golden 기준값 | 검증 입력·기댓값 |

> 위 9건 승인 시 3대 계약은 **v0.1로 freeze**되고, Cowork가 W1~W7 파일 생성을 착수할 수 있다. W8~W10(상품 폼·OpenAPI·bind 검증기)은 freeze 확정 후 단계다.

---

*패치 종료. B-1·B-2·B-3 + 보완 5건이 반영되어 본 계약은 파일화 가능 상태다. §4의 9개 승인 항목 확정 후 W1~W7을 실행한다.*
