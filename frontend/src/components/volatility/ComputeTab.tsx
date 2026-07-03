"use client";
import { useState } from "react";
import { computeVolatility, registerVolatility, type VolatilityComputeResponse } from "@/lib/api/volatilities";
import { ApiError } from "@/lib/apiClient";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";

const pct = (n: number) => n.toLocaleString("ko-KR", { maximumFractionDigits: 4 });

export function ComputeTab({ onSaved }: { onSaved: () => void }) {
  const [files, setFiles] = useState<File[]>([]);
  const [tradingDays, setTradingDays] = useState("250");
  const [computing, setComputing] = useState(false);
  const [preview, setPreview] = useState<VolatilityComputeResponse | null>(null);
  const [names, setNames] = useState<string[]>([]);
  const [asOf, setAsOf] = useState("");
  const [label, setLabel] = useState("");
  const [adopted, setAdopted] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");

  function onPick(e: React.ChangeEvent<HTMLInputElement>) {
    setFiles(Array.from(e.target.files ?? []));
    setPreview(null); setMsg(""); setErr("");
  }

  async function runCompute() {
    setErr(""); setMsg(""); setPreview(null);
    if (files.length === 0) { setErr("유사회사 주가 CSV를 1개 이상 선택하세요."); return; }
    const td = Number(tradingDays);
    if (!td || td <= 0) { setErr("연간 거래일수를 0보다 크게 입력하세요."); return; }
    setComputing(true);
    try {
      const fd = new FormData();
      files.forEach((f) => fd.append("files", f));
      fd.append("trading_days", String(td));
      const res = await computeVolatility(fd);
      setPreview(res);
      setNames(res.companies.map((c) => c.name));
      setAdopted(String(res.average_percent.toFixed(4)));
    } catch (e) {
      setErr(e instanceof ApiError ? `산출 실패: ${e.message}` : "산출 실패 (CSV 형식/인코딩 확인)");
    } finally { setComputing(false); }
  }

  async function save() {
    setErr(""); setMsg("");
    if (!preview) return;
    if (!asOf) { setErr("기준일을 입력하세요."); return; }
    if (!label.trim()) { setErr("대상 라벨을 입력하세요."); return; }
    const adopt = Number(adopted);
    if (Number.isNaN(adopt) || adopt < 0) { setErr("채택 변동성(%)을 확인하세요."); return; }
    const edited = Math.abs(adopt - preview.average_percent) > 1e-9 || names.some((n, i) => n !== preview.companies[i].name);
    setSaving(true);
    try {
      await registerVolatility({
        as_of: asOf, label: label.trim(), method: "PEER_CSV",
        annual_vol_percent: adopt, trading_days_used: preview.trading_days_used,
        detail: {
          companies: preview.companies.map((c, i) => ({ ...c, name: names[i] ?? c.name })),
          computed_average_percent: preview.average_percent, edited,
          source_filenames: files.map((f) => f.name), warnings: preview.warnings,
        },
      });
      setMsg("등록되었습니다(산출근거 저장).");
      onSaved();
    } catch (e) {
      setErr(e instanceof ApiError ? `등록 실패: ${e.message}` : "등록 실패");
    } finally { setSaving(false); }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-500">유사회사 주가 CSV(날짜, 종가)를 여러 개 업로드하면 일별 로그수익률 변동성을 √거래일수로 연환산해 평균을 산출합니다(미리보기, 저장은 확인 후).</p>
      <div className="grid grid-cols-2 gap-3">
        <Field label="유사회사 주가 CSV (여러 개 선택)" help="UTF-8/CP949·천단위콤마·yyyy-MM-dd|yyyy.MM.dd 허용">
          <input type="file" accept=".csv,text/csv" multiple onChange={onPick} className="text-sm" />
        </Field>
        <Field label="연간 거래일수" help="실보고서 관례(기본 250, 해당년 실거래일 편집 가능)">
          <Input type="number" value={tradingDays} onChange={(e) => setTradingDays(e.target.value)} />
        </Field>
      </div>
      {files.length > 0 && <p className="text-xs text-slate-500">선택: {files.map((f) => f.name).join(", ")}</p>}
      <Button variant="secondary" onClick={runCompute} disabled={computing}>{computing ? "산출 중…" : "변동성 산출(미리보기)"}</Button>
      {computing && <Spinner label="산출 중…" />}
      {err && <p className="text-sm text-danger">{err}</p>}

      {preview && (
        <div className="space-y-3 rounded-lg border border-slate-200 p-3">
          <table className="w-full text-sm">
            <thead><tr className="border-b border-slate-200 text-left text-slate-500">
              <th className="py-2 font-medium">회사(파일)</th><th className="font-medium">관측일수</th>
              <th className="font-medium">기간</th><th className="text-right font-medium">일변동성%</th><th className="text-right font-medium">연변동성%</th>
            </tr></thead>
            <tbody>
              {preview.companies.map((c, i) => (
                <tr key={i} className="border-b border-slate-100">
                  <td className="py-1.5"><Input value={names[i] ?? c.name} onChange={(e) => setNames((p) => p.map((x, j) => (j === i ? e.target.value : x)))} /></td>
                  <td className="tnum">{c.observations}</td>
                  <td className="text-slate-500">{c.period_start ?? "—"} ~ {c.period_end ?? "—"}</td>
                  <td className="text-right tnum">{pct(c.daily_vol_percent)}</td>
                  <td className="text-right tnum">{pct(c.annual_vol_percent)}</td>
                </tr>
              ))}
              <tr className="border-t-2 border-slate-300 font-semibold">
                <td className="py-2" colSpan={4}>단순평균(원산출)</td>
                <td className="text-right tnum">{pct(preview.average_percent)}</td>
              </tr>
            </tbody>
          </table>
          {preview.warnings.length > 0 && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs text-warning">
              {preview.warnings.map((w, i) => <div key={i}>⚠ {w}</div>)}
            </div>
          )}
          <div className="grid grid-cols-3 gap-3">
            <Field label="기준일 (as_of)" required><Input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} /></Field>
            <Field label="대상 라벨" required><Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="예: 유사3사 평균" /></Field>
            <Field label="채택 연변동성 (%)" help="기본=단순평균, 편집 가능(원값·채택값 모두 저장)"><Input type="number" value={adopted} onChange={(e) => setAdopted(e.target.value)} /></Field>
          </div>
          {msg && <p className="text-sm text-success">{msg}</p>}
          <Button onClick={save} disabled={saving}>{saving ? "저장 중…" : "채택값으로 등록"}</Button>
        </div>
      )}
    </div>
  );
}
