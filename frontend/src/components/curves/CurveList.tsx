"use client";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { listCurves, getCurveDetail, deleteCurve, type CurveKind } from "@/lib/api/curves";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";
import { Select } from "@/components/ui/Select";
import { DeleteParameterButton } from "@/components/parameters/DeleteParameterButton";
import { CurveChart } from "./CurveChart";

export function CurveList({ refreshKey }: { refreshKey: number }) {
  const qc = useQueryClient();
  const [kind, setKind] = useState<CurveKind | "">("");
  const [openId, setOpenId] = useState<number | null>(null);
  const list = useQuery({
    queryKey: ["curves", kind, refreshKey],
    queryFn: () => listCurves(kind || undefined),
  });
  const detail = useQuery({
    queryKey: ["curve-detail", openId],
    queryFn: () => getCurveDetail(openId as number),
    enabled: openId != null,
  });

  return (
    <Card>
      <CardHeader title="등록된 커브" desc="조직에 저장된 무위험·신용 커브"
        right={
          <Select value={kind} onChange={(e) => setKind(e.target.value as CurveKind | "")} className="w-40">
            <option value="">전체 종류</option>
            <option value="RISK_FREE">무위험(RISK_FREE)</option>
            <option value="CREDIT">신용(CREDIT)</option>
          </Select>
        } />
      <CardBody className="space-y-4">
        {list.isLoading && <Spinner label="불러오는 중…" />}
        {list.isError && <p className="text-sm text-danger">목록 로드 실패</p>}
        {list.data && list.data.items.length === 0 && <p className="text-sm text-slate-500">등록된 커브가 없습니다.</p>}
        {list.data && list.data.items.length > 0 && (
          <table className="w-full text-sm">
            <thead><tr className="border-b border-slate-200 text-left text-slate-500">
              <th className="py-2 font-medium">ID</th><th className="font-medium">종류</th>
              <th className="font-medium">등급</th><th className="font-medium">기준일</th>
              <th className="font-medium">출처</th><th></th>
            </tr></thead>
            <tbody>
              {list.data.items.map((c) => (
                <tr key={c.id} className="border-b border-slate-100">
                  <td className="py-2 tnum">{c.id}</td>
                  <td><Badge tone={c.kind === "RISK_FREE" ? "navy" : "gray"}>{c.kind}</Badge></td>
                  <td className="text-slate-600">{c.grade ?? "—"}</td>
                  <td className="tnum">{c.as_of}</td>
                  <td className="text-slate-500">{c.source ?? "—"}</td>
                  <td className="text-right">
                    <div className="flex items-center justify-end gap-3">
                      <button className="text-navy-700 hover:underline" onClick={() => setOpenId(openId === c.id ? null : c.id)}>
                        {openId === c.id ? "닫기" : "상세"}
                      </button>
                      <DeleteParameterButton
                        kind="커브"
                        label={`#${c.id} · ${c.kind}${c.grade ? ` ${c.grade}` : ""} · ${c.as_of}`}
                        consequence="다른 커브로 폴백되거나 resolve 실패 가능"
                        onDelete={() => deleteCurve(c.id)}
                        onDeleted={() => { if (openId === c.id) setOpenId(null); qc.invalidateQueries({ queryKey: ["curves"] }); }}
                      />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {openId != null && (
          <div className="rounded-lg border border-slate-200 p-3">
            {detail.isLoading && <Spinner label="상세 불러오는 중…" />}
            {detail.data && (
              <div className="space-y-3">
                <CurveChart points={detail.data.points} />
                <table className="w-full text-xs">
                  <thead><tr className="text-left text-slate-400"><th className="py-1">만기(년)</th><th>수익률(%)</th></tr></thead>
                  <tbody>
                    {detail.data.points.map((p, i) => (
                      <tr key={i} className="border-t border-slate-100"><td className="py-1 tnum">{p.tenor_years}</td><td className="tnum">{p.rate_percent}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}
