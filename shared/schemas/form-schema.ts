// ===========================================================================
// FairValue Engine — Dynamic Form Schema 타입 (W2, 계약 ①)
// ---------------------------------------------------------------------------
// 모든 상품 폼은 이 타입으로 표현된다. 상품별 차이는 schema 데이터로만 표현한다.
// 패치 반영:
//   - B-2: rawForm/final 2단계 분리. FieldSchema 에 `resolve` 마커 추가.
//          커브 selector 는 rawForm 경로(curves.*_ref)에 bind 하고,
//          resolve 로 final 경로(curves.*_curve)로의 변환 규칙을 가진다.
//   - 2.1: parity 는 computed (bind 없음, computeFrom 만).
//   - 2.3: InstrumentType 등 enum 은 instrument-types.ts 에서 import (재선언 금지).
// ===========================================================================

import type { InstrumentType } from './instrument-types';

export type { InstrumentType };

export type Primitive = string | number | boolean | null;

// --- 필드 타입 ---
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
// rule 식별자/severity 의 단일 카탈로그는 validation-rules.ts(W3) 에 정의한다.
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

// --- ★신규: rawForm → final ValuationContext 변환 규칙 (패치 B-2 §2.3) ---
export interface ResolveSpec {
  /** final ValuationContext 경로. 예: 'curves.risk_free_curve' */
  to: string;
  /** Normalizer 가 적용할 치환 규칙.
   *  curve : upload_id → 포인트 배열 스냅샷 동결
   *  asset : asset_id → market.{spot,volatility,dividend_yield} 보강(수동값 우선, §2.2) */
  kind: 'curve' | 'asset';
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
  bind?: string;                 // rawForm 경로 (예: 'curves.risk_free_ref', 'terms.issue_date')
  resolve?: ResolveSpec;         // ★신규: rawForm → final 변환 규칙 (selector field 전용)
  computeFrom?: string[];        // computed/readonly 전용 파생 입력 key 목록 (bind 금지)
}

// --- 스텝(Wizard 단계) ---
export interface StepSchema {
  id: string;                    // 'terms' | 'rights.refixing' ...
  title: string;
  description?: string;
  showWhen?: Condition;          // 스텝 전체 토글
  fields: FieldSchema[];
}

// --- 폼 전체 ---
export interface FormSchema {
  product: InstrumentType;
  version: string;               // schema semver, 예: '1.0.0'
  title: string;
  steps: StepSchema[];
}

// ---------------------------------------------------------------------------
// 패치된 공용 커브 selector 필드 (CB·RCPS 등 공통). 참조용 상수.
//   bind 는 rawForm 경로, resolve.to 는 final 경로.
// ---------------------------------------------------------------------------
export const RISK_FREE_CURVE_FIELD: FieldSchema = {
  key: 'risk_free_curve',
  label: '무위험수익률 커브',
  type: 'curveSelector',
  required: true,
  bind: 'curves.risk_free_ref',
  resolve: { to: 'curves.risk_free_curve', kind: 'curve' },
  validations: [
    {
      rule: 'curveRequired',
      params: { kind: 'RISK_FREE' },
      message: '무위험 커브를 선택하세요.',
      severity: 'error',
    },
  ],
};

export const CREDIT_CURVE_FIELD: FieldSchema = {
  key: 'credit_curve',
  label: '신용등급 커브',
  type: 'curveSelector',
  required: true,
  bind: 'curves.credit_ref',
  resolve: { to: 'curves.credit_curve', kind: 'curve' },
  validations: [
    {
      rule: 'curveRequired',
      params: { kind: 'CREDIT' },
      message: '신용 커브를 선택하세요.',
      severity: 'error',
    },
  ],
};

/** parity 는 입력이 아니다(패치 2.1). computed 표시 전용, bind 없음. */
export const PARITY_FIELD: FieldSchema = {
  key: 'parity',
  label: '패리티(자동계산)',
  type: 'computed',
  computeFrom: ['spot', 'conv_price'],
  help: 'spot / conv_price × 액면 (표시 전용)',
};
