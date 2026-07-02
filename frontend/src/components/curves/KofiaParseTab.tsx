"use client";
import { useState } from "react";
import { parseKofia, uploadCurveJson, type KofiaParseResponse, type KofiaCurveCandidate } from "@/lib/api/curves";
import { ApiError } from "@/lib/apiClient";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";

export function KofiaParseTab({ onSaved }: { onSaved: () => void }) {
  const [parsing, setParsing] = useState(false);
  const [parsed, setParsed] = useState<KofiaParseResponse | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [asOf, setAsOf] = useState("");
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState("");
  const [saveMsg, setSaveMsg] = useState("");

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    setErr(""); setParsed(null); setSelected(new Set()); setSaveMsg("");
    setParsing(true);
    try {
      const fd = new FormData();
      fd.append("file", f);
      setParsed(await parseKofia(fd));
    } catch (e) {
      setErr(e instanceof ApiError ? `파싱 실패: ${e.message}` : "파싱 실패 (KOFIA 형식이 아닐 수 있습니다)");
    } finally { setParsing(false); }
  }

  const toggle = (i: number) =>
    setSelected((p) => { const n = new Set(p); if (n.has(i)) n.delete(i); else n.add(i); return n; });

  async function saveSelected() {
    setErr(""); setSaveMsg("");
    if (!asOf) { setErr("평가기준일(as_of)을 입력하세요."); return; }
    if (selected.size === 0) { setErr("저장할 커브를 1개 이상 선택하세요."); return; }
    const cands = (parsed?.candidates ?? []).filter((c) => selected.has(c.index));
    setSaving(true);
    let ok = 0, failMsgs: string[] = [];
    for (const c of cands) {
      try {
        const src = `${c.source}${parsed ? ` (KOFIA:${parsed.filename})` : ""}`;
        const res = await uploadCurveJson({
          as_of: asOf, kind: c.kind, grade: c.grade, source: src,
          points: c.points.map((p) => ({ tenor_years: p.tenor_years, rate_percent: p.rate_percent })),
        });
        if (res.validation && res.validation.length > 0) failMsgs.push(`${c.bond_type}${c.grade ? " " + c.grade : ""}: ${res.validation.map((v) => v.message).join(", ")}`);
        else ok++;
      } catch (e) {
        failMsgs.push(`${c.bond_type}: ${e instanceof ApiError ? e.message : "실패"}`);
      }
    }
    setSaving(false);
    setSaveMsg(`저장 완료 ${ok}건${failMsgs.length ? ` · 실패 ${failMsgs.length}건` : ""}`);
    if (failMsgs.length) setErr(failMsgs.join(" / "));
    if (ok > 0) onSaved();
  }

  return (
    <div className="space-y-4">
      <Field label="KOFIA/평가사 엑셀 파일 (.xls / .xlsx)"
        help="채권시가평가수익률 엑셀을 업로드하면 커브 후보를 미리보기합니다. 저장은 선택 후 진행합니다.">
        <input type="file" accept=".xls,.xlsx" onChange={onFile} className="text-sm" />
      </Field>
      {parsing && <Spinner label="파싱 중…" />}
      {err && <p className="text-sm text-danger">{err}</p>}

      {parsed && (
        <div className="space-y-3">
          <p className="text-xs text-slate-500">{parsed.filename} · 파싱 {new Date(parsed.parsed_at).toLocaleString("ko-KR")} · 후보 {parsed.candidates.length}건</p>
          <div className="grid grid-cols-2 gap-3">
            <Field label="평가기준일 (as_of)" required><Input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} /></Field>
          </div>
          <table className="w-full text-sm">
            <thead><tr className="border-b border-slate-200 text-left text-slate-500">
              <th className="py-2 font-medium">선택</th><th className="font-medium">종류</th><th className="font-medium">등급</th>
              <th className="font-medium">kind</th><th className="font-medium">고시기관</th><th className="font-medium">만기수</th>
            </tr></thead>
            <tbody>
              {parsed.candidates.map((c: KofiaCurveCandidate) => (
                <tr key={c.index} className="border-b border-slate-100">
                  <td className="py-2"><input type="checkbox" checked={selected.has(c.index)} onChange={() => toggle(c.index)} /></td>
                  <td className="text-slate-800">{c.bond_type} <span className="text-slate-400">{c.type_name}</span></td>
                  <td className="text-slate-600">{c.grade ?? "—"}</td>
                  <td><Badge tone={c.kind === "RISK_FREE" ? "navy" : "gray"}>{c.kind}</Badge></td>
                  <td className="text-slate-500">{c.source}</td>
                  <td className="tnum">{c.points.length}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {saveMsg && <p className="text-sm text-success">{saveMsg}</p>}
          <Button onClick={saveSelected} disabled={saving}>{saving ? "저장 중…" : `선택 커브 저장 (${selected.size})`}</Button>
        </div>
      )}
    </div>
  );
}
