"use client";
import { cn } from "@/lib/cn";
import { PRODUCTS, CATEGORIES } from "@/lib/products";
import { Badge } from "@/components/ui/Badge";
import type { InstrumentType } from "@/lib/types";

// 카테고리 그룹 + 상품 카드 선택기. 활성 5종만 선택 가능, SO/CSO 는 "준비 중"으로 비활성.
export function ProductPicker({ value, onChange }: {
  value: InstrumentType | null; onChange: (t: InstrumentType) => void;
}) {
  return (
    <div className="space-y-5">
      {CATEGORIES.map((cat) => (
        <div key={cat}>
          <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">{cat}</div>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {PRODUCTS.filter((p) => p.category === cat).map((p) => {
              const selected = value === p.code;
              const disabled = !p.active;
              return (
                <button
                  key={p.code}
                  type="button"
                  disabled={disabled}
                  aria-pressed={selected}
                  onClick={() => !disabled && onChange(p.code)}
                  className={cn(
                    "rounded-lg border p-3 text-left transition focus:outline-none focus:ring-2 focus:ring-navy-500",
                    disabled
                      ? "cursor-not-allowed border-slate-200 bg-slate-50 opacity-70"
                      : selected
                        ? "border-navy-600 bg-navy-50 ring-1 ring-navy-500"
                        : "border-slate-300 bg-white hover:border-navy-400",
                  )}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-semibold text-slate-900">
                      {p.name} <span className="font-normal text-slate-400">({p.code})</span>
                    </span>
                    {p.active
                      ? (selected && <Badge tone="navy">선택됨</Badge>)
                      : <Badge tone="warning">준비 중</Badge>}
                  </div>
                  <p className="mt-1 text-xs text-slate-500">{p.desc}</p>
                  <p className="mt-1 text-xs text-slate-400">모형: {p.model}</p>
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
