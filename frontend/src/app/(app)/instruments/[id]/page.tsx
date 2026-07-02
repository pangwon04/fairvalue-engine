"use client";
import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getInstrument } from "@/lib/api/instruments";
import { saveTerms } from "@/lib/api/terms";
import { priceInstrument, getJob, getResult } from "@/lib/api/pricing";
import { ApiError } from "@/lib/apiClient";
import { isActiveProduct } from "@/lib/products";
import type { ProductSchema, FormValues } from "@/forms/types";
import type { PricingResult, ValidationIssue } from "@/lib/types";
import { FormRenderer } from "@/forms/FormRenderer";
import { ResultView } from "@/components/ResultView";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Spinner } from "@/components/ui/Spinner";
import cbSchema from "@/forms/productSchemas/cb.json";
import rcpsSchema from "@/forms/productSchemas/rcps.json";
import cpsSchema from "@/forms/productSchemas/cps.json";
import ebSchema from "@/forms/productSchemas/eb.json";
import bwSchema from "@/forms/productSchemas/bw.json";

// 활성 5종 스키마 맵(스키마 구동 — type 만 맞으면 자동 렌더). SO/CSO 는 준비 중이라 미포함.
const SCHEMAS: Record<string, unknown> = {
  CB: cbSchema, RCPS: rcpsSchema, CPS: cpsSchema, EB: ebSchema, BW: bwSchema,
};

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export default function InstrumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const instId = Number(id);
  const { data: inst, isLoading } = useQuery({ queryKey: ["instrument", instId], queryFn: () => getInstrument(instId) });

  const [saving, setSaving] = useState(false);
  const [pricing, setPricing] = useState(false);
  const [serverValidation, setServerValidation] = useState<ValidationIssue[]>([]);
  const [msg, setMsg] = useState("");
  const [result, setResult] = useState<PricingResult | null>(null);

  async function onSave(rawForm: Record<string, unknown>) {
    setSaving(true); setMsg("");
    try {
      const r = await saveTerms(instId, rawForm);
      setServerValidation(r.validation ?? []);
      setMsg(r.has_errors ? "저장됨(검증 오류 있음)" : "저장 완료");
    } catch (e) {
      setMsg(e instanceof ApiError ? `저장 실패: ${e.message}` : "저장 실패");
    } finally { setSaving(false); }
  }

  async function onPrice(rawForm: Record<string, unknown>, values: FormValues) {
    setPricing(true); setMsg(""); setResult(null);
    try {
      const saved = await saveTerms(instId, rawForm);   // 평가 전 최신 rawForm 저장(정합)
      setServerValidation(saved.validation ?? []);
      if (saved.has_errors) { setMsg("검증 오류로 평가를 중단했습니다. 메시지를 확인하세요."); setPricing(false); return; }
      const trigger = {
        model: (values.model as string) || undefined,
        seed: values.seed != null ? Number(values.seed) : undefined,
        options: values.lattice_steps != null ? { lattice_steps: Number(values.lattice_steps) } : undefined,
      };
      const job = await priceInstrument(instId, trigger);
      let status = job.status;
      for (let i = 0; i < 60 && status !== "DONE" && status !== "FAILED"; i++) {
        await sleep(500);
        const j = await getJob(job.job_id);
        status = j.status;
      }
      if (status !== "DONE") { setMsg(`평가 종료 상태: ${status}`); setPricing(false); return; }
      const res = await getResult(job.job_id);
      setResult(res);
      setMsg("평가 완료");
    } catch (e) {
      setMsg(e instanceof ApiError ? `평가 실패: ${e.message}` : "평가 실패");
    } finally { setPricing(false); }
  }

  if (isLoading) return <Spinner label="상품 불러오는 중…" />;
  if (!inst) return <p className="text-danger">상품을 찾을 수 없습니다.</p>;

  const schema = SCHEMAS[inst.type] as ProductSchema | undefined;
  const active = isActiveProduct(inst.type);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">{inst.name}</h1>
          <p className="text-sm text-slate-500">{inst.issuer} · <Badge tone="navy">{inst.type}</Badge></p>
        </div>
        {msg && <span className="text-sm text-slate-600">{msg}</span>}
      </div>

      {active && schema ? (
        // 스키마 구동 렌더 — 활성 5종(CB·RCPS·CPS·EB·BW)
        <FormRenderer schema={schema} onSave={onSave} onPrice={onPrice}
          saving={saving} pricing={pricing} serverValidation={serverValidation} />
      ) : (
        // ★ SO/CSO 등 준비 중 — 평가 진입 물리적 차단(FormRenderer·평가 버튼 미표시)
        <Card>
          <CardHeader title="평가 모형 준비 중" />
          <CardBody className="space-y-2 text-sm text-slate-600">
            <p><Badge tone="warning">준비 중</Badge> {inst.type} 상품의 평가 모형은 아직 제공되지 않습니다.</p>
            <p className="text-slate-500">모형 정확성을 위해 이 상품의 입력·평가는 비활성화되어 있습니다. 활성 상품(CB·RCPS·CPS·EB·BW)을 이용해 주세요.</p>
          </CardBody>
        </Card>
      )}

      {result && <ResultView result={result} />}
    </div>
  );
}
