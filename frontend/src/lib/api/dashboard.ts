import { api } from "../apiClient";

// ★5-10 대시보드 요약. 순수 현황(읽기 전용). 숨김 job 제외.
export interface DashboardRecentJob {
  job_id: number;
  instrument_name: string | null;
  type: string | null;
  valuation_date: string | null;
  model: string | null;
  total_fair_value: number | null;
  status: "QUEUED" | "RUNNING" | "DONE" | "FAILED" | "PARTIAL";
  report_issued: boolean;
}

export interface DashboardSummary {
  instruments: { active: number; archived: number };
  jobs: { done: number; failed: number };
  reports: { count: number };
  parameters: { curves: number; volatilities: number };
  by_type: { type: string; count: number }[];
  recent_jobs: DashboardRecentJob[];
}

export const getDashboardSummary = () => api.get<DashboardSummary>("/dashboard/summary");
