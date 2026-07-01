// 백엔드 DTO/PricingResult 와 1:1(코드 확인 기반, 추측 아님).
export type InstrumentType = "RCPS" | "CPS" | "CB" | "EB" | "BW" | "SO" | "CSO";
export type InstrumentStatus = "DRAFT" | "TERMS_SAVED" | "PRICED" | "ARCHIVED";
export type JobStatus = "QUEUED" | "RUNNING" | "DONE" | "FAILED" | "PARTIAL";
export type UserRole = "ORG_ADMIN" | "VALUATOR" | "CURVE_MANAGER" | "VIEWER";

export interface User { id: number; email: string; role: UserRole; organization_id: number; }
export interface AuthResult { token: string; user: User; }

export interface InstrumentDto {
  id: number; type: InstrumentType; name: string; issuer: string;
  status: InstrumentStatus; project_id: number | null; organization_id: number;
}

export interface ValidationIssue { field: string | null; rule: string; severity: string; message: string; }
export interface TermsSaveResponse { saved: boolean; has_errors: boolean; validation: ValidationIssue[]; }
export interface TermsDraftResponse { instrument_id: number; rawForm: Record<string, unknown>; validation: ValidationIssue[]; }

export interface PricingTrigger {
  model?: string; seed?: number;
  options?: Record<string, unknown>; params_override?: Record<string, unknown>;
}
export interface PriceJobResponse { job_id: number; status: JobStatus; cached: boolean; }
export interface JobDto { job_id: number; instrument_id: number; status: JobStatus; cached: boolean; input_hash: string | null; }

export interface Components {
  bond_value: number | null; preferred_share_value: number | null;
  conversion_option_value: number | null; exchange_option_value: number | null;
  warrant_value: number | null; redemption_option_value: number | null;
  issuer_call_value: number | null; sale_claim_value: number | null;
  stock_option_value: number | null; conditional_option_value: number | null;
  dilution_effect: number | null; total_fair_value: number | null;
}
export interface PricingResult {
  job_id: number; instrument_id: number; instrument_type: string;
  valuation_date: string | null; status: string;
  total_fair_value: number | null; per_unit_value: number | null;
  components: Components;
  key_parameters: Record<string, unknown>;
  reproducibility: Record<string, unknown>;
  warnings: Array<{ code: string; message: string; stage?: string }>;
  errors: Array<{ code: string; message: string; stage?: string }>;
}

export interface CurveDto {
  id: number; kind: "RISK_FREE" | "CREDIT"; grade: string | null;
  as_of: string; version: number; source: string | null;
  interpolation_method: string; origin: string;
}
export interface CurveListResponse { items: CurveDto[]; }
