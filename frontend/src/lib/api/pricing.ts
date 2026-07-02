import { api } from "../apiClient";
import type { PriceJobResponse, JobDto, PricingResult, PricingTrigger } from "../types";
export const priceInstrument = (id: number, trigger: PricingTrigger) =>
  api.post<PriceJobResponse>(`/instruments/${id}/price`, trigger);
export const getJob = (jobId: number) => api.get<JobDto>(`/jobs/${jobId}`);
export const getResult = (jobId: number) => api.get<PricingResult>(`/jobs/${jobId}/result`);

export interface JobSummary {
  job_id: number; instrument_id: number; instrument_name: string | null;
  instrument_type: string | null; valuation_date: string | null; model: string | null;
  status: "QUEUED" | "RUNNING" | "DONE" | "FAILED" | "PARTIAL";
  total_fair_value: number | null; created_at: string | null;
}
export interface JobListResponse { items: JobSummary[]; }

export const listJobs = (params?: { instrument_type?: string; status?: string; from?: string; to?: string }) => {
  const q = new URLSearchParams();
  if (params?.instrument_type) q.set("instrument_type", params.instrument_type);
  if (params?.status) q.set("status", params.status);
  if (params?.from) q.set("from", params.from);
  if (params?.to) q.set("to", params.to);
  const qs = q.toString();
  return api.get<JobListResponse>(`/jobs${qs ? `?${qs}` : ""}`);
};
