"use client";
import { cn } from "@/lib/cn";
export function Tabs({ tabs, active, onChange }: {
  tabs: { id: string; label: string }[]; active: string; onChange: (id: string) => void;
}) {
  return (
    <div className="flex gap-1 border-b border-slate-200">
      {tabs.map((t) => (
        <button
          key={t.id} type="button" onClick={() => onChange(t.id)}
          className={cn("-mb-px border-b-2 px-4 py-2 text-sm font-medium transition",
            active === t.id ? "border-navy-700 text-navy-800" : "border-transparent text-slate-500 hover:text-slate-700")}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
