"use client";
import { useMemo, useState } from "react";
import type { ProductSchema, FormValues } from "./types";
import { visible } from "./showWhen";
import { validateForm } from "./validate";
import { buildRawForm, initialValues } from "./bindPath";
import { FieldRenderer } from "./FieldRenderer";
import { Stepper } from "@/components/ui/Stepper";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import type { ValidationIssue } from "@/lib/types";

export function FormRenderer({ schema, onSave, onPrice, saving, pricing, serverValidation }: {
  schema: ProductSchema;
  onSave: (rawForm: Record<string, unknown>, values: FormValues) => void;
  onPrice: (rawForm: Record<string, unknown>, values: FormValues) => void;
  saving?: boolean; pricing?: boolean;
  serverValidation?: ValidationIssue[];
}) {
  const [values, setValues] = useState<FormValues>(() => initialValues(schema));
  const [step, setStep] = useState(0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = (k: string, v: unknown) => setValues((p) => ({ ...p, [k]: v }));

  const steps = schema.steps;
  const cur = steps[step];
  const summary = useMemo(() => {
    const pick = (k: string) => values[k];
    return [
      ["발행사", pick("issuer")], ["상품명", pick("name")], ["평가기준일", pick("valuation_date")],
      ["발행금액", pick("issue_amount")], ["전환가액", pick("conv_price")], ["평가모형", pick("model")],
    ] as [string, unknown][];
  }, [values]);

  const doSave = () => {
    const errs = validateForm(schema, values);
    setErrors(errs);
    if (Object.keys(errs).length > 0) return false;
    onSave(buildRawForm(schema, values), values);
    return true;
  };
  const doPrice = () => {
    const errs = validateForm(schema, values);
    setErrors(errs);
    if (Object.keys(errs).length > 0) return;
    onPrice(buildRawForm(schema, values), values);
  };

  return (
    <div className="grid grid-cols-[220px_1fr_260px] gap-6">
      {/* 좌: 스텝 */}
      <Card className="h-fit"><CardBody><Stepper steps={steps} current={step} onSelect={setStep} /></CardBody></Card>

      {/* 중: 현재 스텝 필드 */}
      <Card>
        <CardHeader title={cur.title} desc={`${step + 1} / ${steps.length} 단계`} />
        <CardBody className="space-y-4">
          {cur.fields.filter((f) => visible(f, values)).map((f) => (
            <FieldRenderer key={f.key} field={f} value={values[f.key]} values={values}
              error={errors[f.key]} onChange={(v) => set(f.key, v)} />
          ))}
          {serverValidation && serverValidation.length > 0 && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm">
              <p className="font-medium text-warning">서버 검증 메시지</p>
              <ul className="mt-1 list-disc pl-5 text-slate-700">
                {serverValidation.map((v, i) => (
                  <li key={i} className={v.severity === "error" ? "text-danger" : "text-warning"}>
                    {v.field ? `${v.field}: ` : ""}{v.message}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div className="flex items-center justify-between pt-2">
            <Button variant="secondary" disabled={step === 0} onClick={() => setStep((s) => Math.max(0, s - 1))}>이전</Button>
            <div className="flex gap-2">
              {step < steps.length - 1 && <Button variant="secondary" onClick={() => setStep((s) => Math.min(steps.length - 1, s + 1))}>다음</Button>}
              <Button onClick={doSave} disabled={saving}>{saving ? "저장 중…" : "입력 저장"}</Button>
              <Button onClick={doPrice} disabled={pricing}>{pricing ? "평가 중…" : "평가 실행"}</Button>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* 우: 요약 */}
      <Card className="h-fit">
        <CardHeader title="입력 요약" />
        <CardBody>
          <dl className="space-y-2 text-sm">
            {summary.map(([k, v]) => (
              <div key={k} className="flex justify-between gap-2">
                <dt className="text-slate-500">{k}</dt>
                <dd className="text-right font-medium text-slate-800 tnum">{v == null || v === "" ? "—" : String(v)}</dd>
              </div>
            ))}
          </dl>
        </CardBody>
      </Card>
    </div>
  );
}
