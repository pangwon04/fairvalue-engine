// ===========================================================================
// FairValue Engine — PricingResult 타입 (W5, 계약 ③)
// ---------------------------------------------------------------------------
// 패치 반영:
//   - B-3: components 는 표준 12종 key 만 사용(약식 명칭 금지).
//   - 2.4: warnings/errors 는 옵셔널 아님(필수 배열, 호출부 기본 []).
//   - 2.3: InstrumentType 은 W1 에서 import.
//   - 2.1: parity 는 key_parameters.parity 에만 존재(입력 아님).
// ===========================================================================

import type { InstrumentType } from './instrument-types';

export type { InstrumentType };

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
  warnings: Issue[];   // 필수. 기본 []
  errors: Issue[];     // 필수. 기본 []
}

/** 표준 component key 12종(패치 B-3). 부호는 §4.10 보유자 관점. */
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
  total_fair_value?: number | null;    // = 나머지 부호포함 합의 echo
}

export interface KeyParameters {
  risk_free_rate?: number | null;
  ytm?: number | null;
  credit_spread?: number | null;
  volatility?: number | null;
  dividend_yield?: number | null;
  parity?: number | null;              // 엔진 산출(입력 아님)
  discount_rate?: number | null;
  model_name: string;
  model_version: string;
  simulation_paths?: number | null;
  lattice_steps?: number | null;
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
  input_hash: string;
  seed: number;
  model_version: string;
  engine_commit?: string | null;
  computed_at?: string;
}

export interface Issue {
  code: string;
  message: string;
  field?: string | null;
  stage?: string | null;
}

/** 표준 component key 목록(린트·검증 공용). total_fair_value 제외 = 합산 대상. */
export const COMPONENT_KEYS = [
  'bond_value',
  'preferred_share_value',
  'conversion_option_value',
  'exchange_option_value',
  'warrant_value',
  'redemption_option_value',
  'issuer_call_value',
  'sale_claim_value',
  'stock_option_value',
  'conditional_option_value',
  'dilution_effect',
  'total_fair_value',
] as const;

/** 부호 음수(≤0) 강제 component key(§4.10). */
export const NEGATIVE_SIGN_KEYS = [
  'issuer_call_value',
  'sale_claim_value',
  'dilution_effect',
] as const;
