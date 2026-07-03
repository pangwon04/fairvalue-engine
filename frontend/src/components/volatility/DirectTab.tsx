"use client";
import { useState } from "react";
import { registerVolatility } from "@/lib/api/volatilities";
import { ApiError } from "@/lib/apiClient";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

export function DirectTab({ onSaved }: { onSaved: () => void }) {
  const [asOf, setAsOf] = useState("");
  const [label, setLabel] = useState("");
  const [vol, setVol] = useState("");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState("");
  const [msg, setMsg] = useState("");

  async function save() {
    setErr(""); setMsg("");
    if (!asOf) { setErr("기준일을 입력하세요."); return; }
    if (!label.trim()) { setErr("대상 라벨을 입력하세요."); return; }
    const v = Number(vol);
    if (!vol || Number.isNaN(v) || v < 0) { setErr("연변동성(%)을 0 이상으로 입력하세요."); return; }
    setSaving(true);
    try {
      await registerVolatility({ as_of: asOf, label: label.trim(), method: "DIRECT", annual_vol_percent: v, source_note: note.trim() || undefined });
      setMsg("등록되었습니다.");
      setLabel(""); setVol(""); setNote("");
      onSaved();
    } catch (e) {
      setErr(e instanceof ApiError ? `등록 실패: ${e.message}` : "등록 실패");
    } finally { setSaving(false); }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-500">이미 산출된 연변동성 값을 직접 등록합니다(내부 추정·외부 제공값 등).</p>
      <div className="grid grid-cols-2 gap-3">
        <Field label="기준일 (as_of)" required><Input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} /></Field>
        <Field label="대상 라벨 (발행사/기초자산)" required><Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="예: 예시바이오 보통주" /></Field>
        <Field label="연변동성 (%)" required><Input type="number" value={vol} onChange={(e) => setVol(e.target.value)} placeholder="예: 45" /></Field>
        <Field label="산출방법 메모" help="근거·출처(선택)"><Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="예: 3사 평균, 내부 추정" /></Field>
      </div>
      {err && <p className="text-sm text-danger">{err}</p>}
      {msg && <p className="text-sm text-success">{msg}</p>}
      <Button onClick={save} disabled={saving}>{saving ? "저장 중…" : "변동성 등록"}</Button>
    </div>
  );
}
