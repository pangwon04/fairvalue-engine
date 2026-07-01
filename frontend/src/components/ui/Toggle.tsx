"use client";
import { cn } from "@/lib/cn";
export function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={cn("relative inline-flex h-6 w-11 items-center rounded-full transition",
        checked ? "bg-navy-700" : "bg-slate-300")}
    >
      <span className={cn("inline-block h-4 w-4 transform rounded-full bg-white transition",
        checked ? "translate-x-6" : "translate-x-1")} />
    </button>
  );
}
