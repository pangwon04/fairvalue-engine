// ===========================================================================
// FairValue Engine — ValuationContext 타입 (W4, 계약 ②)
// ---------------------------------------------------------------------------
// final ValuationContext(계산 단계)의 TypeScript 표현.
// 패치 반영:
//   - 2.3: InstrumentType·ModelName·ExerciseStyle 등 enum 은 W1 에서 import(재선언 금지).
//   - 2.1: Conversion 에 parity 필드 없음(parity 는 결과 전용).
//   - B-2: curves 는 포인트 배열 스냅샷(risk_free_curve/credit_curve). *_ref 는 rawForm 전용이라 여기 없음.
//   - input_hash·model_version 은 필수.
// ===========================================================================

import type {
  InstrumentType,
  ModelName,
  ExerciseStyle,
} from './instrument-types';

export type { InstrumentType, ModelName, ExerciseStyle };

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
  input_hash: string;                // 64-hex SHA-256 (required)
  model_version: string;             // required
  options?: EngineOptions;
  metadata?: ContextMetadata;        // input_hash 비대상
}

export interface Terms {
  issue_date?: string;
  maturity_date?: string | null;
  issue_amount?: number | null;
  face_value?: number | null;
  issue_price?: number | null;
  coupon_rate?: number | null;
  coupon_freq_month?: 1 | 3 | 6 | 12 | null;
  guaranteed_yield?: number | null;
  redemption_type?: 'bullet' | 'amortizing' | null;
  interest_payment_type?: 'cash' | 'accrued' | 'compound' | null;
  dividend_cumulative?: boolean | null;
  host_type?: 'dated' | 'perpetual' | null;
  grant_date?: string | null;
  grant_quantity?: number | null;
  exercise_price?: number | null;
  expected_term?: number | null;
  tranche?: Record<string, unknown>[];
}

export interface Rights {
  conversion?: Conversion;
  exchange?: Exchange;
  warrant?: Warrant;
  redemption?: Redemption;
  issuer_call?: IssuerCall;
  sale_claim?: SaleClaim;
  refixing?: Refixing;
  dilution?: Dilution;
  vesting?: Vesting;
  performance_condition?: PerfCondition;
  market_condition?: MarketCondition;
}

/** 전환권. parity 필드 없음(패치 2.1 — 결과 key_parameters.parity 에만 존재). */
export interface Conversion {
  strike: number;
  ratio?: number | null;
  start?: string | null;
  end?: string | null;
  style?: ExerciseStyle | null;
}
export interface Exchange {
  enabled?: boolean;
  target_asset_id?: number | null;
  ratio?: number | null;
  strike?: number | null;
}
export interface Warrant {
  enabled?: boolean;
  strike?: number | null;
  quantity?: number | null;
  separable?: boolean | null;
  start?: string | null;
  end?: string | null;
}
export interface Redemption {
  put?: { enabled: boolean; yield?: number | null; start?: string | null; style?: ExerciseStyle | null };
  call?: { enabled: boolean; yield?: number | null; start?: string | null };
}
export interface IssuerCall {
  enabled: boolean;
  strike?: number | null;
  style?: ExerciseStyle | null;
  start?: string | null;
}
export interface SaleClaim {
  enabled: boolean;
  discount_type?: 'standalone_riskfree' | 'credit' | null;
  strike_pct?: number | null;
  style?: ExerciseStyle | null;
  beneficiary?: 'issuer' | 'third_party' | null;
}
export interface Refixing {
  enabled: boolean;
  start?: string | null;
  step_month?: number | null;
  floor?: number | null;
  init_strike?: number | null;
  direction?: 'DOWN' | 'BOTH' | null;
}
export interface Dilution {
  enabled: boolean;
  new_shares?: number | null;
}
export interface Vesting {
  schedule: { date: string; ratio: number }[];
  cliff_month?: number | null;
  forfeiture_rate?: number | null;
}
export interface PerfCondition {
  metric?: 'revenue' | 'ebitda' | 'ni' | 'custom' | null;
  target?: number | null;
  probability?: number | null;
}
export interface MarketCondition {
  type?: 'target_price' | 'tsr' | 'barrier' | null;
  target_price?: number | null;
  barrier?: number | null;
  knock?: 'in' | 'out' | null;
}

export interface Market {
  asset_id?: number | null;
  spot?: number | null;
  volatility?: number | null;
  dividend_yield?: number | null;
  shares_outstanding?: number | null;
  peer_companies?: Record<string, unknown>[];
  data_source?: string | null;
  as_of?: string | null;
}

export type CurvePoints = [number, number][]; // [tenor_year, rate_pct]

/** final 단계 커브: 포인트 배열 스냅샷만(*_ref 없음). */
export interface Curves {
  risk_free_curve: CurvePoints;
  credit_curve?: CurvePoints | null;
  curve_source?: string;
  curve_version: string;
  interpolation_method: 'linear' | 'log_linear' | 'monotone_convex';
  fallback_policy?: 'nearest_grade' | 'manual_only' | 'error';
}

export interface EngineOptions {
  lattice_steps?: number;
  simulation_paths?: number;
  variance_reduction?: 'none' | 'antithetic' | 'sobol';
}
export interface ContextMetadata {
  issuer?: string;
  instrument_name?: string;
  requested_by?: number;
  created_at?: string;
}

// ---------------------------------------------------------------------------
// rawForm(draft) 단계 커브 타입. Normalizer 입력. *_ref 허용.
// ---------------------------------------------------------------------------
export interface DraftCurves {
  risk_free_ref?: number | string | null;
  credit_ref?: number | string | null;
  risk_free_curve?: CurvePoints | null;
  credit_curve?: CurvePoints | null;
  interpolation_method?: 'linear' | 'log_linear' | 'monotone_convex';
  curve_version?: string | null;
  fallback_policy?: 'nearest_grade' | 'manual_only' | 'error';
}
