"use client";
import { useState } from "react";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

/** 자동 조회 — 골격만. 주가 데이터 API 연동은 키 심사 후. */
export function AutoTab() {
  const [asOf, setAsOf] = useState("");
  const [peers, setPeers] = useState("");
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Badge tone="warning">준비 중</Badge>
        <p className="text-sm text-slate-600">주가 데이터 API 키 심사 중입니다. 승인 후 기준일·유사회사 종목으로 자동 산출을 지원합니다.</p>
      </div>
      <div className="grid grid-cols-2 gap-3 opacity-60">
        <Field label="기준일 (as_of)"><Input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} disabled /></Field>
        <Field label="유사회사 종목코드 (쉼표 구분)"><Input value={peers} onChange={(e) => setPeers(e.target.value)} placeholder="예: 005930, 000660" disabled /></Field>
      </div>
      <Button disabled>자동 조회 실행 (비활성)</Button>
    </div>
  );
}
