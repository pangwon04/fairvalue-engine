import { api } from "../apiClient";
import { getToken } from "../auth";

export interface ReportItem {
  report_id: number;
  report_no: string;
  job_id: number;
  instrument_id: number;
  instrument_name: string | null;
  instrument_type: string | null;
  valuation_date: string | null;
  issued_at: string | null;
}
export interface ReportListResponse { items: ReportItem[]; }

/** 발급(생성·저장). DONE + 계산근거(트리) 필요. 구버전은 409. 재발급=새 레코드. */
export const issueReport = (jobId: number) =>
  api.post<{ report_id: number; report_no: string }>(`/jobs/${jobId}/report`, {});

export const listReports = () => api.get<ReportListResponse>("/reports");

/** 인증 필요 다운로드 → blob 받아 파일 저장(Bearer 헤더 때문에 <a href> 직접 불가). */
export async function downloadReport(id: number, format: "pdf" | "excel", filename: string) {
  const res = await fetch(`${api.base}/reports/${id}/${format}`, {
    headers: { Authorization: `Bearer ${getToken() ?? ""}` },
  });
  if (!res.ok) throw new Error("다운로드 실패");
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.download = filename;
  document.body.appendChild(a); a.click(); a.remove();
  URL.revokeObjectURL(url);
}
