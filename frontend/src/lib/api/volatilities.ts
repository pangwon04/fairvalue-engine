import { api } from "../apiClient";

// ── 타입 ──────────────────────────────────────────────────────────────────
export type VolatilityMethod = "DIRECT" | "PEER_CSV";

export interface CompanyVol {
  name: string;
  observations: number;
  period_start?: string | null;
  period_end?: string | null;
  daily_vol_percent: number;
  annual_vol_percent: number;
  warnings: string[];
}

export interface VolatilityComputeResponse {
  companies: CompanyVol[];
  average_percent: number;
  trading_days_used: number;
  warnings: string[];
}

export interface VolatilityItem {
  id: number;
  as_of: string;
  label: string;
  annual_vol_percent: number;
  method: VolatilityMethod;
  trading_days_used: number;
  created_at?: string | null;
}

export interface VolatilityListResponse { items: VolatilityItem[]; }

export interface VolatilityDetail extends VolatilityItem {
  detail?: Record<string, unknown> | null;
  created_by?: number | null;
}

export interface VolatilityRegisterBody {
  as_of: string;
  label: string;
  method: VolatilityMethod;
  annual_vol_percent: number;
  trading_days_used?: number;
  source_note?: string;
  detail?: {
    companies?: CompanyVol[];
    computed_average_percent?: number;
    edited?: boolean;
    source_filenames?: string[];
    warnings?: string[];
  };
}

// ── API ───────────────────────────────────────────────────────────────────
/** 미리보기 산출(저장 안 함). CSV N개 + 거래일수. */
export const computeVolatility = (form: FormData) =>
  api.postForm<VolatilityComputeResponse>("/volatilities/compute", form);

/** 등록(DIRECT | PEER_CSV). */
export const registerVolatility = (body: VolatilityRegisterBody) =>
  api.post<{ volatility_id: number }>("/volatilities", body);

/** 목록(필터 as_of·label). */
export const listVolatilities = (asOf?: string, label?: string) => {
  const q = new URLSearchParams();
  if (asOf) q.set("as_of", asOf);
  if (label) q.set("label", label);
  const qs = q.toString();
  return api.get<VolatilityListResponse>(`/volatilities${qs ? `?${qs}` : ""}`);
};

/** 단건(detail 포함). */
export const getVolatilityDetail = (id: number) =>
  api.get<VolatilityDetail>(`/volatilities/${id}`);
