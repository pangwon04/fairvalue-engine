"use client";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import type { FieldSchema, FormValues } from "./types";
import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import { listCurves } from "@/lib/api/curves";
import { listVolatilities } from "@/lib/api/volatilities";

function curveKindOf(key: string): "RISK_FREE" | "CREDIT" | undefined {
  if (key.includes("risk_free")) return "RISK_FREE";
  if (key.includes("credit")) return "CREDIT";
  return undefined;
}

export function FieldRenderer({ field, value, onChange, error, values, setField }: {
  field: FieldSchema; value: unknown; onChange: (v: unknown) => void; error?: string; values: FormValues;
  setField?: (key: string, value: unknown) => void;
}) {
  const num = (e: React.ChangeEvent<HTMLInputElement>) =>
    onChange(e.target.value === "" ? "" : Number(e.target.value));

  // ★E3b: hidden(예: volatility_ref) — 렌더 없음. buildRawForm 이 값 있을 때만 rawForm 에 싣는다.
  if (field.type === "hidden") return null;

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
  const isVolatility = field.bind === "market.volatility";
  return (
    <Field label={field.label + (field.unit ? ` (${field.unit})` : "")} required={field.required} error={error} help={field.help}>
      <Input type={type} invalid={!!error} value={value == null ? "" : String(value)}
        onChange={isNum ? num : (e) => onChange(e.target.value)}
        placeholder={field.type === "assetSearch" ? "자산 ID(이번 슬라이스: 수동 입력)" : undefined} />
      {isVolatility && (
        <VolatilityLoad
          onLoad={(id, vol) => {
            onChange(vol);
            setField?.("volatility_ref", id);   // ★rawForm→context→input_hash 에 참조 스냅샷 기록
          }}
          currentRef={values["volatility_ref"]}
        />
      )}
    </Field>
  );
}

/** ★E3b: 등록된 변동성 불러오기. 값 복사(필드 채움) + volatility_ref 기록. */
function VolatilityLoad({ onLoad, currentRef }: {
  onLoad: (id: number, vol: number) => void; currentRef: unknown;
}) {
  const [open, setOpen] = useState(false);
  const { data } = useQuery({ queryKey: ["volatilities", "picker"], queryFn: () => listVolatilities(), enabled: open });
  return (
    <div className="mt-1 text-xs">
      <button type="button" className="text-navy-700 hover:underline" onClick={() => setOpen((o) => !o)}>
        {open ? "닫기" : "등록된 변동성 불러오기"}
      </button>
      {currentRef != null && currentRef !== "" && <span className="ml-2 text-slate-500">참조: 변동성 #{String(currentRef)}</span>}
      {open && (
        <Select value="" onChange={(e) => {
          const item = data?.items.find((v) => String(v.id) === e.target.value);
          if (item) { onLoad(item.id, item.annual_vol_percent); setOpen(false); }
        }}>
          <option value="">{data ? "변동성 선택…" : "불러오는 중…"}</option>
          {data?.items.map((v) => (
            <option key={v.id} value={String(v.id)}>#{v.id} · {v.label} · {v.as_of} · {v.annual_vol_percent}%</option>
          ))}
        </Select>
      )}
    </div>
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
