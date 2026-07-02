import { api } from "../apiClient";
import type { CurveListResponse } from "../types";

export type CurveKind = "RISK_FREE" | "CREDIT";

export interface CurvePoint { tenor_years: number; rate_percent: number; seq?: number; }
export interface CurveDetail {
  id: number; kind: CurveKind; grade: string | null; as_of: string;
  version: number; source: string | null; interpolation_method: string;
  origin: string; points: CurvePoint[];
}
export interface CurveValidationIssue { field: string | null; rule: string; severity: string; message: string; }
export interface CurveUploadResult { upload_id: number; validation: CurveValidationIssue[]; }

export interface KofiaCurveCandidate {
  index: number; bond_type: string; type_name: string; grade: string | null;
  source: string; kind: CurveKind; points: { tenor_years: number; rate_percent: number }[];
}
export interface KofiaParseResponse { filename: string; parsed_at: string; candidates: KofiaCurveCandidate[]; }

export interface CurveUploadBody {
  as_of: string; kind: CurveKind; grade?: string | null; source?: string | null;
  interpolation_method?: string | null;
  points: { tenor_years: number; rate_percent: number }[];
}

export const listCurves = (kind?: CurveKind) =>
  api.get<CurveListResponse>(`/curves${kind ? `?kind=${kind}` : ""}`);
export const getCurveDetail = (id: number) => api.get<CurveDetail>(`/curves/${id}`);
export const uploadCurveJson = (body: CurveUploadBody) => api.post<CurveUploadResult>("/curves", body);
export const uploadCurveCsv = (form: FormData) => api.postForm<CurveUploadResult>("/curves", form);
export const parseKofia = (form: FormData) => api.postForm<KofiaParseResponse>("/curves/parse-kofia", form);
