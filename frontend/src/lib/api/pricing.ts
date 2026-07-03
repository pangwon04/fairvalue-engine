import { api } from "../apiClient";
import type { PriceJobResponse, JobDto, PricingResult, PricingTrigger } from "../types";
export const priceInstrument = (id: number, trigger: PricingTrigger) =>
  api.post<PriceJobResponse>(`/instruments/${id}/price`, trigger);
export const getJob = (jobId: number) => api.get<JobDto>(`/jobs/${jobId}`);
export const getResult = (jobId: number) => api.get<PricingResult>(`/jobs/${jobId}/result`);

// ★5-7: 평가시점 입력 스냅샷(contextJson). 구 job 은 null(폴백 안내). org 격리는 백엔드 동일.
export interface JobContext { job_id: number; has_context: boolean; context: Record<string, unknown> | null; }
export const getJobContext = (jobId: number) => api.get<JobContext>(`/jobs/${jobId}/context`);

export interface JobSummary {
  job_id: number; instrument_id: number; instrument_name: string | null;
  instrument_type: string | null; valuation_date: string | null; model: string | null;
  status: "QUEUED" | "RUNNING" | "DONE" | "FAILED" | "PARTIAL";
  total_fair_value: number | null; created_at: string | null;
  hidden?: boolean;
}
export interface JobListResponse { items: JobSummary[]; }

export const listJobs = (params?: { instrument_type?: string; status?: string; from?: string; to?: string; include_hidden?: boolean }) => {
  const q = new URLSearchParams();
  if (params?.instrument_type) q.set("instrument_type", params.instrument_type);
  if (params?.status) q.set("status", params.status);
  if (params?.from) q.set("from", params.from);
  if (params?.to) q.set("to", params.to);
  if (params?.include_hidden) q.set("include_hidden", "true");
  const qs = q.toString();
  return api.get<JobListResponse>(`/jobs${qs ? `?${qs}` : ""}`);
};

// ★5-8: 이력 배치 삭제(DONE→숨김, FAILED→완전삭제, 진행중→skip).
export interface BatchDeleteResult { hidden_count: number; deleted_count: number; skipped: { job_id: number; reason: string }[]; }
export const batchDeleteJobs = (body: { job_ids?: number[]; all?: boolean; instrument_type?: string; status?: string }) =>
  api.post<BatchDeleteResult>("/jobs/batch-delete", body);
