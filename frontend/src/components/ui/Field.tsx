"use client";
import { cn } from "@/lib/cn";
export function Field({ label, required, error, help, children }: {
  label?: string; required?: boolean; error?: string; help?: string; children: React.ReactNode;
}) {
  return (
    <div className="space-y-1">
      {label && (
        <label className="block text-sm font-medium text-slate-700">
          {label} {required && <span className="text-danger">*</span>}
        </label>
      )}
      {children}
      {help && !error && <p className="text-xs text-slate-500">{help}</p>}
      {error && <p className={cn("text-xs text-danger")}>{error}</p>}
    </div>
  );
}
