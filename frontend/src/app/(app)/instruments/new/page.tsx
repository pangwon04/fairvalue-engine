"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { createInstrument } from "@/lib/api/instruments";
import { ApiError } from "@/lib/apiClient";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Field } from "@/components/ui/Field";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";

export default function NewInstrumentPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [issuer, setIssuer] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(""); setBusy(true);
    try {
      const inst = await createInstrument("CB", name, issuer);
      router.replace(`/instruments/${inst.id}`);
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "생성 실패");
    } finally { setBusy(false); }
  }

  return (
    <div className="max-w-lg space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">CB 상품 생성</h1>
      <Card>
        <CardHeader title="전환사채(CB)" desc="생성 후 입력폼에서 계약조건을 채웁니다." />
        <CardBody>
          <form onSubmit={submit} className="space-y-4">
            <Field label="상품명"><Input value={name} onChange={(e) => setName(e.target.value)} required placeholder="예: 예시바이오 3CB" /></Field>
            <Field label="발행사" error={err}><Input value={issuer} onChange={(e) => setIssuer(e.target.value)} required placeholder="예: 예시바이오" /></Field>
            <Button type="submit" disabled={busy}>{busy ? "생성 중…" : "생성하고 입력 이동"}</Button>
          </form>
        </CardBody>
      </Card>
    </div>
  );
}
