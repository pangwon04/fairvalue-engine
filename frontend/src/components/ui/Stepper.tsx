"use client";
import { cn } from "@/lib/cn";
export function Stepper({ steps, current, onSelect }: {
  steps: { id: string; title: string }[]; current: number; onSelect?: (i: number) => void;
}) {
  return (
    <ol className="space-y-1">
      {steps.map((s, i) => (
        <li key={s.id}>
          <button
            type="button"
            onClick={() => onSelect?.(i)}
            className={cn("flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm transition",
              i === current ? "bg-navy-50 text-navy-800 font-medium" : "text-slate-600 hover:bg-slate-50")}
          >
            <span className={cn("flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs",
              i === current ? "bg-navy-700 text-white" : i < current ? "bg-navy-200 text-navy-800" : "bg-slate-200 text-slate-500")}>
              {i + 1}
            </span>
            {s.title}
          </button>
        </li>
      ))}
    </ol>
  );
}
