"use client";
import { useState } from "react";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

// ★ 자동 조회 — API 키 심사 중이라 조회 비활성. 발급 후 백엔드 커넥터 연결(UI/파라미터 구조만 완성).
export function AutoFetchTab() {
  const [asOf, setAsOf] = useState("");
  const [agency, setAgency] = useState("나이스피앤아이");
  const [bondType, setBondType] = useState("국채");
  const [grade, setGrade] = useState("");

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-warning">
        <Badge tone="warning">준비 중</Badge> 채권수익률 공개 API 키 심사 중입니다. 키 발급 후 자동 조회가 활성화됩니다.
        그 전에는 <b>직접 업로드</b> 또는 <b>KOFIA 엑셀 파싱</b>을 이용하세요.
      </div>
      <div className="grid grid-cols-2 gap-4 opacity-70">
        <Field label="평가기준일"><Input type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} /></Field>
        <Field label="신용평가사">
          <Select value={agency} onChange={(e) => setAgency(e.target.value)}>
            <option>나이스피앤아이</option><option>KIS자산평가</option><option>한국자산평가</option>
          </Select>
        </Field>
        <Field label="채권종류">
          <Select value={bondType} onChange={(e) => setBondType(e.target.value)}>
            <option>국채</option><option>통안증권</option><option>특수채</option><option>금융채</option><option>회사채</option>
          </Select>
        </Field>
        <Field label="신용등급"><Input value={grade} onChange={(e) => setGrade(e.target.value)} placeholder="예: AA-" /></Field>
      </div>
      <Button disabled title="API 키 심사 중">조회 (준비 중)</Button>
    </div>
  );
}
