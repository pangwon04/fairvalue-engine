"use client";
import { useQuery } from "@tanstack/react-query";
import type { FieldSchema, FormValues } from "./types";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import { listCurves } from "@/lib/api/curves";

function curveKindOf(key: string): "RISK_FREE" | "CREDIT" | undefined {
  if (key.includes("risk_free")) return "RISK_FREE";
  if (key.includes("credit")) return "CREDIT";
  return undefined;
}

export function FieldRenderer({ field, value, onChange, error, values }: {
  field: FieldSchema; value: unknown; onChange: (v: unknown) => void; error?: string; values: FormValues;
}) {
  const num = (e: React.ChangeEvent<HTMLInputElement>) =>
    onChange(e.target.value === "" ? "" : Number(e.target.value));

  // computed/readonly — 표시 전용
  if (field.type === "computed" || field.type === "readonly") {
    const parts = (field.computeFrom ?? []).map((k) => `${k}=${values[k] ?? "-"}`).join("  ·  ");
    return <Field label={field.label} help={field.help}><div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600 tnum">{parts || "—"}</div></Field>;
  }

  if (field.type === "toggle") {
    return <Field label={field.label} error={error} help={field.help}>
      <div className="pt-1"><Toggle checked={!!value} onChange={onChange} /></div>
    </Field>;
  }

  if (field.type === "select") {
    return <Field label={field.label} required={field.required} error={error} help={field.help}>
      <Select value={value == null ? "" : String(value)} invalid={!!error}
        onChange={(e) => {
          const opt = field.options?.find((o) => String(o.value) === e.target.value);
          onChange(opt ? opt.value : e.target.value);
        }}>
        <option value="">선택…</option>
        {field.options?.map((o) => <option key={String(o.value)} value={String(o.value)}>{o.label}</option>)}
      </Select>
    </Field>;
  }

  if (field.type === "curveSelector") {
    return <CurveSelectorField field={field} value={value} onChange={onChange} error={error} />;
  }

  // text/date/number/currency/percentage/assetSearch → Input
  const type = field.type === "date" ? "date"
    : ["number", "currency", "percentage", "assetSearch"].includes(field.type) ? "number" : "text";
  const isNum = type === "number";
  return (
    <Field label={field.label + (field.unit ? ` (${field.unit})` : "")} required={field.required} error={error} help={field.help}>
      <Input type={type} invalid={!!error} value={value == null ? "" : String(value)}
        onChange={isNum ? num : (e) => onChange(e.target.value)}
        placeholder={field.type === "assetSearch" ? "자산 ID(이번 슬라이스: 수동 입력)" : undefined} />
    </Field>
  );
}

function CurveSelectorField({ field, value, onChange, error }: {
  field: FieldSchema; value: unknown; onChange: (v: unknown) => void; error?: string;
}) {
  const kind = curveKindOf(field.key);
  const { data, isLoading, isError } = useQuery({
    queryKey: ["curves", kind], queryFn: () => listCurves(kind),
  });
  return (
    <Field label={field.label} required={field.required} error={error}
      help={isError ? "커브 목록을 불러오지 못했습니다(백엔드 확인)." : "업로드된 커브 중 평가기준일과 같은 as_of 선택"}>
      <Select value={value == null ? "" : String(value)} invalid={!!error} onChange={(e) => onChange(e.target.value === "" ? "" : Number(e.target.value))}>
        <option value="">{isLoading ? "불러오는 중…" : "커브 선택…"}</option>
        {data?.items.map((c) => (
          <option key={c.id} value={String(c.id)}>
            #{c.id} · {c.kind}{c.grade ? ` ${c.grade}` : ""} · {c.as_of}
          </option>
        ))}
      </Select>
    </Field>
  );
}
