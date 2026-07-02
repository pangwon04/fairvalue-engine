"use client";
import { useState } from "react";
import { uploadCurveJson, uploadCurveCsv, type CurveKind, type CurveUploadResult } from "@/lib/api/curves";
import { ApiError } from "@/lib/apiClient";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

type Row = { tenor: string; rate: string };

export function DirectUploadTab({ onSaved }: { onSaved: () => void }) {
  const [mode, setMode] = useState<"manual" | "csv">("manual");
  const [asOf, setAsOf] = useState("");
  const [kind, setKind] = useState<CurveKind>("RISK_FREE");
  const [grade, setGrade] = useState("");
  const [source, setSource] = useState("");
  const [rows, setRows] = useState<Row[]>([{ tenor: "", rate: "" }]);
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [result, setResult] = useState<CurveUploadResult | null>(null);

  const setRow = (i: number, k: keyof Row, v: string) =>
    setRows((p) => p.map((r, j) => (j === i ? { ...r, [k]: v } : r)));

  function validateMeta(): string | null {
    if (!asOf) return "평가기준일(as_of)은 필수입니다.";
    if (kind === "CREDIT" && !grade.trim()) return "신용(CREDIT) 커브는 신용등급이 필요합니다.";
    return null;
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setErr(""); setResult(null);
    const v = validateMeta();
    if (v) { setErr(v); return; }
    setBusy(true);
    try {
      let res: CurveUploadResult;
      if (mode === "csv") {
        if (!file) { setErr("CSV 파일을 선택하세요."); setBusy(false); return; }
        const fd = new FormData();
        fd.append("as_of", asOf); fd.append("kind", kind);
        if (grade) fd.append("grade", grade);
        if (source) fd.append("source", source);
        fd.append("file", file);
        res = await uploadCurveCsv(fd);
      } else {
        const points = rows
          .filter((r) => r.tenor !== "" && r.rate !== "")
          .map((r) => ({ tenor_years: Number(r.tenor), rate_percent: Number(r.rate) }));
        if (points.length === 0) { setErr("포인트를 1개 이상 입력하세요."); setBusy(false); return; }
        res = await uploadCurveJson({ as_of: asOf, kind, grade: grade || null, source: source || null, points });
      }
      setResult(res);
      if (!res.validation || res.validation.length === 0) onSaved();
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "업로드 실패");
    } finally { setBusy(false); }
  }

  return (
    <form onSubmit={save} className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <Field label="평가기준일 (as_of)" required><Input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} /></Field>
        <Field label="종류 (kind)" required>
          <Select value={kind} onChange={(e) => setKind(e.target.value as CurveKind)}>
            <option value="RISK_FREE">무위험 (RISK_FREE)</option>
            <option value="CREDIT">신용 (CREDIT)</option>
          </Select>
        </Field>
        {kind === "CREDIT" && (
          <Field label="신용등급 (grade)" required><Input value={grade} onChange={(e) => setGrade(e.target.value)} placeholder="예: AA-" /></Field>
        )}
        <Field label="출처 (source)"><Input value={source} onChange={(e) => setSource(e.target.value)} placeholder="예: KISPRICING" /></Field>
      </div>

      <div className="flex gap-2 text-sm">
        <button type="button" onClick={() => setMode("manual")} className={mode === "manual" ? "font-semibold text-navy-800" : "text-slate-500"}>수동 입력</button>
        <span className="text-slate-300">|</span>
        <button type="button" onClick={() => setMode("csv")} className={mode === "csv" ? "font-semibold text-navy-800" : "text-slate-500"}>CSV 업로드</button>
      </div>

      {mode === "manual" ? (
        <div className="space-y-2">
          {rows.map((r, i) => (
            <div key={i} className="flex items-center gap-2">
              <Input className="w-32" placeholder="만기(년)" value={r.tenor} onChange={(e) => setRow(i, "tenor", e.target.value)} />
              <Input className="w-32" placeholder="수익률(%)" value={r.rate} onChange={(e) => setRow(i, "rate", e.target.value)} />
              <button type="button" className="text-xs text-danger" onClick={() => setRows((p) => p.filter((_, j) => j !== i))}>삭제</button>
            </div>
          ))}
          <Button type="button" variant="secondary" onClick={() => setRows((p) => [...p, { tenor: "", rate: "" }])}>+ 포인트 추가</Button>
        </div>
      ) : (
        <Field label="CSV 파일" help="형식: tenor_years,rate_percent (meta 헤더 허용)">
          <input type="file" accept=".csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} className="text-sm" />
        </Field>
      )}

      {err && <p className="text-sm text-danger">{err}</p>}
      {result && (
        <div className="rounded-lg border border-slate-200 p-3 text-sm">
          {result.validation && result.validation.length > 0
            ? <div><Badge tone="warning">검증 메시지</Badge><ul className="mt-1 list-disc pl-5 text-slate-700">{result.validation.map((v, i) => <li key={i} className={v.severity === "error" ? "text-danger" : "text-warning"}>{v.message}</li>)}</ul></div>
            : <p className="text-success">✓ 저장 완료 (upload_id {result.upload_id})</p>}
        </div>
      )}
      <Button type="submit" disabled={busy}>{busy ? "저장 중…" : "커브 저장"}</Button>
    </form>
  );
}
