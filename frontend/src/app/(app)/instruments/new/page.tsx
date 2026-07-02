"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { createInstrument } from "@/lib/api/instruments";
import { ApiError } from "@/lib/apiClient";
import { isActiveProduct } from "@/lib/products";
import type { InstrumentType } from "@/lib/types";
import { ProductPicker } from "@/components/ProductPicker";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Field } from "@/components/ui/Field";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";

export default function NewInstrumentPage() {
  const router = useRouter();
  const [type, setType] = useState<InstrumentType | null>(null);
  const [name, setName] = useState("");
  const [issuer, setIssuer] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr("");
    if (!type) { setErr("상품 종류를 선택하세요."); return; }
    // ★ SO/CSO 는 평가 모형 준비 중 → 생성 차단(오계산 원천 방지).
    if (!isActiveProduct(type)) { setErr("선택한 상품은 평가 모형 준비 중입니다."); return; }
    setBusy(true);
    try {
      const inst = await createInstrument(type, name, issuer);
      router.replace(`/instruments/${inst.id}`);
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "생성 실패");
    } finally { setBusy(false); }
  }

  return (
    <div className="max-w-3xl space-y-4">
      <h1 className="text-xl font-semibold text-slate-900">상품 생성</h1>

      <Card>
        <CardHeader title="상품 종류 선택" desc="평가 대상 복합금융상품군을 선택하세요. 준비 중 상품은 선택할 수 없습니다." />
        <CardBody><ProductPicker value={type} onChange={setType} /></CardBody>
      </Card>

      <Card>
        <CardHeader title="기본정보" desc="상품명과 발행사를 입력합니다. 계약조건은 다음 화면에서 채웁니다." />
        <CardBody>
          <form onSubmit={submit} className="space-y-4">
            <Field label="상품명" required>
              <Input value={name} onChange={(e) => setName(e.target.value)} required placeholder="예: 예시바이오 3CB" />
            </Field>
            <Field label="발행사" required error={err}>
              <Input value={issuer} onChange={(e) => setIssuer(e.target.value)} required placeholder="예: 예시바이오" />
            </Field>
            <Button type="submit" disabled={busy || !type}>{busy ? "생성 중…" : "생성하고 입력 이동"}</Button>
          </form>
        </CardBody>
      </Card>
    </div>
  );
}
