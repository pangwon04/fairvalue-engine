"use client";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { listJobs, getResult } from "@/lib/api/pricing";
import { ResultView } from "@/components/ResultView";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";
import { Select } from "@/components/ui/Select";

const statusTone: Record<string, "navy" | "success" | "danger" | "gray"> = {
  DONE: "success", FAILED: "danger", RUNNING: "navy", QUEUED: "gray", PARTIAL: "gray",
};
const fmt = (n: number | null) => (n == null ? "—" : n.toLocaleString("ko-KR", { maximumFractionDigits: 2 }));

export default function JobsPage() {
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [openJob, setOpenJob] = useState<number | null>(null);

  const jobs = useQuery({
    queryKey: ["jobs", type, status],
    queryFn: () => listJobs({ instrument_type: type || undefined, status: status || undefined }),
  });
  const result = useQuery({
    queryKey: ["job-result", openJob],
    queryFn: () => getResult(openJob as number),
    enabled: openJob != null,
  });

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">평가 이력</h1>
      <Card>
        <CardHeader title="평가 Job 목록" desc="과거 평가와 결과. 행을 클릭하면 12키 분해와 재현성 정보를 확인합니다."
          right={
            <div className="flex gap-2">
              <Select value={type} onChange={(e) => setType(e.target.value)} className="w-32">
                <option value="">전체 상품</option>
                {["CB", "RCPS", "CPS", "EB", "BW"].map((t) => <option key={t} value={t}>{t}</option>)}
              </Select>
              <Select value={status} onChange={(e) => setStatus(e.target.value)} className="w-32">
                <option value="">전체 상태</option>
                {["DONE", "FAILED", "RUNNING", "QUEUED"].map((s) => <option key={s} value={s}>{s}</option>)}
              </Select>
            </div>
          } />
        <CardBody>
          {jobs.isLoading && <Spinner label="불러오는 중…" />}
          {jobs.isError && <p className="text-sm text-danger">이력 로드 실패</p>}
          {jobs.data && jobs.data.items.length === 0 && <p className="text-sm text-slate-500">평가 이력이 없습니다.</p>}
          {jobs.data && jobs.data.items.length > 0 && (
            <table className="w-full text-sm">
              <thead><tr className="border-b border-slate-200 text-left text-slate-500">
                <th className="py-2 font-medium">Job</th><th className="font-medium">상품</th><th className="font-medium">종류</th>
                <th className="font-medium">평가일</th><th className="font-medium">모형</th><th className="font-medium">상태</th>
                <th className="text-right font-medium">공정가치</th><th></th>
              </tr></thead>
              <tbody>
                {jobs.data.items.map((j) => (
                  <tr key={j.job_id} className="border-b border-slate-100">
                    <td className="py-2 tnum">{j.job_id}</td>
                    <td className="text-slate-800">{j.instrument_name ?? "—"}</td>
                    <td>{j.instrument_type && <Badge tone="navy">{j.instrument_type}</Badge>}</td>
                    <td className="tnum">{j.valuation_date ?? "—"}</td>
                    <td className="text-slate-500">{j.model ?? "—"}</td>
                    <td><Badge tone={statusTone[j.status] ?? "gray"}>{j.status}</Badge></td>
                    <td className="text-right tnum">{fmt(j.total_fair_value)}</td>
                    <td className="text-right">
                      <button className="text-navy-700 hover:underline" onClick={() => setOpenJob(openJob === j.job_id ? null : j.job_id)}>
                        {openJob === j.job_id ? "닫기" : "상세"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardBody>
      </Card>

      {openJob != null && (
        <div>
          {result.isLoading && <Spinner label="결과 불러오는 중…" />}
          {result.isError && <p className="text-sm text-danger">결과를 불러오지 못했습니다.</p>}
          {result.data && <ResultView result={result.data} />}
        </div>
      )}
    </div>
  );
}
