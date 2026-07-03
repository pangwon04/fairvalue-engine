"use client";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { listReports, downloadReport } from "@/lib/api/reports";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";

export default function ReportsPage() {
  const [busy, setBusy] = useState<string>("");
  const { data, isLoading, isError } = useQuery({ queryKey: ["reports"], queryFn: () => listReports() });

  async function dl(id: number, format: "pdf" | "excel", no: string) {
    setBusy(`${id}-${format}`);
    try { await downloadReport(id, format, `${no}.${format === "pdf" ? "pdf" : "xlsx"}`); }
    catch { /* noop */ } finally { setBusy(""); }
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">보고서</h1>
      <Card>
        <CardHeader title="발급 이력" desc="평가보고서(PDF)·계산근거(엑셀) 발급 이력. 평가 이력 상세의 ‘보고서 발급’으로 생성합니다." />
        <CardBody className="space-y-2">
          {isLoading && <Spinner label="불러오는 중…" />}
          {isError && <p className="text-sm text-danger">목록 로드 실패</p>}
          {data && data.items.length === 0 && <p className="text-sm text-slate-500">발급된 보고서가 없습니다. 평가 이력 상세에서 발급하세요.</p>}
          {data && data.items.length > 0 && (
            <table className="w-full text-sm">
              <thead><tr className="border-b border-slate-200 text-left text-slate-500">
                <th className="py-2 font-medium">발급번호</th><th className="font-medium">상품</th><th className="font-medium">종류</th>
                <th className="font-medium">평가일</th><th className="font-medium">발급일</th><th className="text-right font-medium">다운로드</th>
              </tr></thead>
              <tbody>
                {data.items.map((r) => (
                  <tr key={r.report_id} className="border-b border-slate-100">
                    <td className="py-2 font-mono text-xs text-slate-800">{r.report_no}</td>
                    <td className="text-slate-800">{r.instrument_name ?? `#${r.instrument_id}`}</td>
                    <td>{r.instrument_type && <Badge tone="navy">{r.instrument_type}</Badge>}</td>
                    <td className="tnum">{r.valuation_date ?? "—"}</td>
                    <td className="tnum text-slate-500">{r.issued_at ? new Date(r.issued_at).toLocaleString("ko-KR") : "—"}</td>
                    <td className="text-right">
                      <div className="flex justify-end gap-3">
                        <button className="text-navy-700 hover:underline disabled:text-slate-300" disabled={busy === `${r.report_id}-pdf`} onClick={() => dl(r.report_id, "pdf", r.report_no)}>PDF</button>
                        <button className="text-navy-700 hover:underline disabled:text-slate-300" disabled={busy === `${r.report_id}-excel`} onClick={() => dl(r.report_id, "excel", r.report_no)}>엑셀</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
