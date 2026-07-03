"use client";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { listVolatilities, getVolatilityDetail, type CompanyVol } from "@/lib/api/volatilities";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";

const pct = (n: unknown) => (typeof n === "number" ? n.toLocaleString("ko-KR", { maximumFractionDigits: 4 }) : "—");

export function VolatilityList({ refreshKey }: { refreshKey: number }) {
  const [openId, setOpenId] = useState<number | null>(null);
  const { data, isLoading, isError } = useQuery({
    queryKey: ["volatilities", refreshKey],
    queryFn: () => listVolatilities(),
  });

  return (
    <Card>
      <CardHeader title="등록된 변동성" desc="기준일·대상·채택변동성·산출방법. 행을 클릭하면 산출근거를 봅니다." />
      <CardBody className="space-y-2">
        {isLoading && <Spinner label="불러오는 중…" />}
        {isError && <p className="text-sm text-danger">목록 로드 실패(백엔드 확인).</p>}
        {data && data.items.length === 0 && <p className="text-sm text-slate-500">등록된 변동성이 없습니다.</p>}
        {data && data.items.length > 0 && (
          <table className="w-full text-sm">
            <thead><tr className="border-b border-slate-200 text-left text-slate-500">
              <th className="py-2 font-medium">기준일</th><th className="font-medium">대상</th>
              <th className="text-right font-medium">채택변동성%</th><th className="font-medium">방법</th><th className="font-medium">거래일수</th>
            </tr></thead>
            <tbody>
              {data.items.map((v) => (
                <tr key={v.id} className="cursor-pointer border-b border-slate-100 hover:bg-slate-50" onClick={() => setOpenId(openId === v.id ? null : v.id)}>
                  <td className="py-2 tnum">{v.as_of}</td>
                  <td className="text-slate-800">{v.label}</td>
                  <td className="text-right tnum">{pct(v.annual_vol_percent)}</td>
                  <td><Badge tone={v.method === "PEER_CSV" ? "navy" : "gray"}>{v.method}</Badge></td>
                  <td className="tnum">{v.trading_days_used}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {openId != null && <VolatilityDetailView id={openId} />}
      </CardBody>
    </Card>
  );
}

function VolatilityDetailView({ id }: { id: number }) {
  const { data, isLoading } = useQuery({ queryKey: ["volatility", id], queryFn: () => getVolatilityDetail(id) });
  if (isLoading) return <Spinner label="상세 불러오는 중…" />;
  if (!data) return null;
  const d = (data.detail ?? {}) as Record<string, unknown>;
  const companies = (d.companies as CompanyVol[] | undefined) ?? [];
  const files = (d.source_filenames as string[] | undefined) ?? [];
  return (
    <div className="mt-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm">
      <p className="font-medium text-slate-700">#{data.id} · {data.label} · {data.method} · 거래일수 {data.trading_days_used}</p>
      <div className="mt-1 grid grid-cols-2 gap-2 text-xs text-slate-600">
        <div>원산출 평균: {pct(d.computed_average)}%</div>
        <div>채택값: {pct(d.adopted_value)}% {d.edited ? <Badge tone="warning">편집됨</Badge> : null}</div>
        {files.length > 0 && <div className="col-span-2">파일: {files.join(", ")}</div>}
        {typeof d.uploaded_at === "string" && <div className="col-span-2">업로드: {new Date(d.uploaded_at).toLocaleString("ko-KR")}</div>}
      </div>
      {companies.length > 0 && (
        <table className="mt-2 w-full text-xs">
          <thead><tr className="border-b border-slate-200 text-left text-slate-500">
            <th className="py-1 font-medium">회사</th><th className="font-medium">관측</th><th className="font-medium">기간</th>
            <th className="text-right font-medium">일변동성%</th><th className="text-right font-medium">연변동성%</th>
          </tr></thead>
          <tbody>
            {companies.map((c, i) => (
              <tr key={i} className="border-b border-slate-100">
                <td className="py-1">{c.name}</td><td className="tnum">{c.observations}</td>
                <td className="text-slate-500">{c.period_start ?? "—"} ~ {c.period_end ?? "—"}</td>
                <td className="text-right tnum">{pct(c.daily_vol_percent)}</td>
                <td className="text-right tnum">{pct(c.annual_vol_percent)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {Array.isArray(d.warnings) && (d.warnings as string[]).length > 0 && (
        <div className="mt-2 text-xs text-warning">{(d.warnings as string[]).map((w, i) => <div key={i}>⚠ {w}</div>)}</div>
      )}
    </div>
  );
}
