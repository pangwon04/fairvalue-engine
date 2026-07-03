"use client";
import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { listJobs, getResult, batchDeleteJobs, type JobSummary } from "@/lib/api/pricing";
import { ResultView } from "@/components/ResultView";
import { CalculationBasis } from "@/components/audit/CalculationBasis";
import { ReportIssueButton } from "@/components/reports/ReportIssueButton";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";
import { Select } from "@/components/ui/Select";
import { Button } from "@/components/ui/Button";
import { Toggle } from "@/components/ui/Toggle";
import { Modal } from "@/components/ui/Modal";

const statusTone: Record<string, "navy" | "success" | "danger" | "gray"> = {
  DONE: "success", FAILED: "danger", RUNNING: "navy", QUEUED: "gray", PARTIAL: "gray",
};
const fmt = (n: number | null) => (n == null ? "—" : n.toLocaleString("ko-KR", { maximumFractionDigits: 2 }));
const isHideable = (s: string) => s === "DONE" || s === "PARTIAL";
const isHard = (s: string) => s === "FAILED";

export default function JobsPage() {
  const qc = useQueryClient();
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [includeHidden, setIncludeHidden] = useState(false);
  const [openJob, setOpenJob] = useState<number | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [confirmMode, setConfirmMode] = useState<null | "selected" | "all">(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState("");

  const jobs = useQuery({
    queryKey: ["jobs", type, status, includeHidden],
    queryFn: () => listJobs({ instrument_type: type || undefined, status: status || undefined, include_hidden: includeHidden }),
  });
  const result = useQuery({
    queryKey: ["job-result", openJob],
    queryFn: () => getResult(openJob as number),
    enabled: openJob != null,
  });

  const items = jobs.data?.items ?? [];
  const toggleSel = (id: number) =>
    setSelected((p) => { const n = new Set(p); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const allChecked = items.length > 0 && items.every((j) => selected.has(j.job_id));
  const toggleAll = () => setSelected(allChecked ? new Set() : new Set(items.map((j) => j.job_id)));

  // 확인 모달 대상·분기 카운트
  const targets: JobSummary[] = useMemo(() => {
    if (confirmMode === "all") return items;
    if (confirmMode === "selected") return items.filter((j) => selected.has(j.job_id));
    return [];
  }, [confirmMode, items, selected]);
  const doneN = targets.filter((j) => isHideable(j.status)).length;
  const failN = targets.filter((j) => isHard(j.status)).length;
  const skipN = targets.length - doneN - failN;

  async function runDelete() {
    setBusy(true); setMsg("");
    try {
      const body = confirmMode === "all"
        ? { all: true, instrument_type: type || undefined, status: status || undefined }
        : { job_ids: Array.from(selected) };
      const r = await batchDeleteJobs(body);
      setMsg(`숨김 ${r.hidden_count}건 · 완전삭제 ${r.deleted_count}건${r.skipped.length ? ` · 건너뜀 ${r.skipped.length}건(진행 중)` : ""}`);
      setSelected(new Set());
      setConfirmMode(null);
      qc.invalidateQueries({ queryKey: ["jobs"] });
    } catch {
      setMsg("삭제 처리 실패");
    } finally { setBusy(false); }
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">평가 이력</h1>
      <Card>
        <CardHeader title="평가 Job 목록" desc="행 클릭하면 결과·계산근거. 선택/전체 삭제 — DONE은 숨김(데이터 보존), FAILED는 완전삭제."
          right={
            <div className="flex items-center gap-2">
              <label className="flex items-center gap-1.5 text-sm text-slate-600">숨긴 이력 <Toggle checked={includeHidden} onChange={setIncludeHidden} /></label>
              <Select value={type} onChange={(e) => setType(e.target.value)} className="w-28">
                <option value="">전체 상품</option>
                {["CB", "RCPS", "CPS", "EB", "BW"].map((t) => <option key={t} value={t}>{t}</option>)}
              </Select>
              <Select value={status} onChange={(e) => setStatus(e.target.value)} className="w-28">
                <option value="">전체 상태</option>
                {["DONE", "FAILED", "RUNNING", "QUEUED"].map((s) => <option key={s} value={s}>{s}</option>)}
              </Select>
            </div>
          } />
        <CardBody className="space-y-2">
          <div className="flex items-center gap-2">
            <Button variant="secondary" disabled={selected.size === 0} onClick={() => setConfirmMode("selected")}>선택 삭제 ({selected.size})</Button>
            <Button variant="secondary" disabled={items.length === 0} onClick={() => setConfirmMode("all")}>전체 삭제</Button>
            {msg && <span className="text-sm text-success">{msg}</span>}
          </div>
          {jobs.isLoading && <Spinner label="불러오는 중…" />}
          {jobs.isError && <p className="text-sm text-danger">이력 로드 실패</p>}
          {jobs.data && items.length === 0 && <p className="text-sm text-slate-500">평가 이력이 없습니다.</p>}
          {items.length > 0 && (
            <table className="w-full text-sm">
              <thead><tr className="border-b border-slate-200 text-left text-slate-500">
                <th className="py-2 w-8"><input type="checkbox" checked={allChecked} onChange={toggleAll} /></th>
                <th className="font-medium">Job</th><th className="font-medium">상품</th><th className="font-medium">종류</th>
                <th className="font-medium">평가일</th><th className="font-medium">모형</th><th className="font-medium">상태</th>
                <th className="text-right font-medium">공정가치</th><th></th>
              </tr></thead>
              <tbody>
                {items.map((j) => (
                  <tr key={j.job_id} className="border-b border-slate-100">
                    <td className="py-2"><input type="checkbox" checked={selected.has(j.job_id)} onChange={() => toggleSel(j.job_id)} /></td>
                    <td className="tnum">{j.job_id}</td>
                    <td className="text-slate-800">{j.instrument_name ?? "—"}{j.hidden && <Badge tone="gray">숨김</Badge>}</td>
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
        <div className="space-y-4">
          {result.isLoading && <Spinner label="결과 불러오는 중…" />}
          {result.isError && <p className="text-sm text-danger">결과를 불러오지 못했습니다.</p>}
          {result.data && (
            <>
              <ResultView result={result.data} />
              <CalculationBasis result={result.data} jobId={openJob} />
              <ReportIssueButton result={result.data} jobId={openJob} />
            </>
          )}
        </div>
      )}

      <Modal open={confirmMode != null} title="평가 이력 삭제" onClose={() => setConfirmMode(null)}
        onConfirm={runDelete} confirmLabel="삭제 진행" confirmVariant="danger" busy={busy}>
        <p>대상 {targets.length}건 중:</p>
        <ul className="mt-2 list-disc space-y-1 pl-5">
          <li><b>DONE {doneN}건</b>은 <b>숨김 처리</b>(데이터·감사자료 보존, ‘숨긴 이력 보기’로 조회).</li>
          <li><b>FAILED {failN}건</b>은 <b>완전 삭제</b>됩니다. <span className="text-danger">되돌릴 수 없습니다.</span></li>
          {skipN > 0 && <li>진행 중 {skipN}건은 건너뜁니다(삭제 불가).</li>}
        </ul>
      </Modal>
    </div>
  );
}
