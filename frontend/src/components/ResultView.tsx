"use client";
import type { PricingResult } from "@/lib/types";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";

const LABELS: Record<string, string> = {
  bond_value: "채권가치", preferred_share_value: "우선주가치",
  conversion_option_value: "전환옵션", exchange_option_value: "교환옵션",
  warrant_value: "신주인수권", redemption_option_value: "상환권",
  issuer_call_value: "발행자콜", sale_claim_value: "매도청구권",
  stock_option_value: "주식매수선택권", conditional_option_value: "조건부SO",
  dilution_effect: "희석효과",
};
const SUM_KEYS = Object.keys(LABELS);
const fmt = (n: number | null | undefined) =>
  n == null ? "—" : n.toLocaleString("ko-KR", { maximumFractionDigits: 2 });

export function ResultView({ result }: { result: PricingResult }) {
  const c = result.components;
  const sum = SUM_KEYS.reduce((a, k) => a + ((c as any)[k] ?? 0), 0);
  const total = c.total_fair_value ?? result.total_fair_value ?? 0;
  const ok = Math.abs(sum - total) <= 0.01;
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader title="평가 결과" desc={`모형 ${String(result.key_parameters?.model_name ?? "")} · ${result.instrument_type}`}
          right={<Badge tone={result.status === "DONE" ? "success" : "gray"}>{result.status}</Badge>} />
        <CardBody>
          <div className="grid grid-cols-2 gap-4">
            <div className="rounded-lg bg-navy-50 p-4">
              <div className="text-sm text-navy-700">공정가치 합계 (total)</div>
              <div className="mt-1 text-2xl font-semibold text-navy-900 tnum">{fmt(total)}</div>
            </div>
            <div className="rounded-lg bg-slate-50 p-4">
              <div className="text-sm text-slate-500">단위당 (per unit)</div>
              <div className="mt-1 text-2xl font-semibold text-slate-800 tnum">{fmt(result.per_unit_value)}</div>
            </div>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="컴포넌트 분해 (12키)" desc="부호 포함 Σ = total 검산" />
        <CardBody>
          <table className="w-full text-sm">
            <thead><tr className="border-b border-slate-200 text-left text-slate-500">
              <th className="py-2 font-medium">항목</th><th className="text-right font-medium">값</th></tr></thead>
            <tbody>
              {SUM_KEYS.map((k) => {
                const v = (c as any)[k] as number | null;
                if (v == null) return null;
                return (
                  <tr key={k} className="border-b border-slate-100">
                    <td className="py-1.5 text-slate-700">{LABELS[k]}</td>
                    <td className={`text-right tnum ${v < 0 ? "text-danger" : "text-slate-800"}`}>{fmt(v)}</td>
                  </tr>
                );
              })}
              <tr className="border-t-2 border-slate-300 font-semibold">
                <td className="py-2">합계 (Σ)</td>
                <td className="text-right tnum">{fmt(sum)}</td>
              </tr>
            </tbody>
          </table>
          <p className={`mt-2 text-xs ${ok ? "text-success" : "text-danger"}`}>
            {ok ? "✓ Σ(컴포넌트) = total 정합" : `⚠ Σ와 total 불일치 (차이 ${fmt(sum - total)})`}
          </p>
          {result.warnings?.length > 0 && (
            <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs text-warning">
              {result.warnings.map((w, i) => <div key={i}>⚠ {w.message}</div>)}
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
