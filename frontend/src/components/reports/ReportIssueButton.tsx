"use client";
import { useState } from "react";
import type { PricingResult } from "@/lib/types";
import { issueReport, downloadReport } from "@/lib/api/reports";
import { ApiError } from "@/lib/apiClient";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";

export function ReportIssueButton({ result, jobId }: { result: PricingResult; jobId: number }) {
  const [issuing, setIssuing] = useState(false);
  const [issued, setIssued] = useState<{ id: number; no: string } | null>(null);
  const [err, setErr] = useState("");

  const canIssue = result.status === "DONE" && !!result.trees && !!result.curve_bootstrap;

  async function issue() {
    setErr(""); setIssuing(true);
    try {
      const r = await issueReport(jobId);
      setIssued({ id: r.report_id, no: r.report_no });
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "발급 실패");
    } finally { setIssuing(false); }
  }

  return (
    <Card>
      <CardHeader title="평가보고서 발급" desc="표준 양식 PDF(보고서) + 엑셀(계산근거 raw)을 생성·저장합니다." />
      <CardBody className="space-y-2">
        {!canIssue && (
          <p className="text-xs text-warning">
            이 평가에는 계산근거(가격트리·이자율 산정)가 없어 보고서를 발급할 수 없습니다. 재평가 후 발급하세요.
          </p>
        )}
        {!issued ? (
          <Button onClick={issue} disabled={!canIssue || issuing}>{issuing ? "발급 중…" : "보고서 발급"}</Button>
        ) : (
          <div className="space-y-2">
            <p className="text-sm text-success">발급 완료 · 발급번호 <b className="font-mono">{issued.no}</b></p>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => downloadReport(issued.id, "pdf", `${issued.no}.pdf`)}>PDF 다운로드</Button>
              <Button variant="secondary" onClick={() => downloadReport(issued.id, "excel", `${issued.no}.xlsx`)}>엑셀 다운로드</Button>
              <Button variant="ghost" onClick={() => setIssued(null)}>재발급</Button>
            </div>
          </div>
        )}
        {err && <p className="text-sm text-danger">{err}</p>}
      </CardBody>
    </Card>
  );
}
