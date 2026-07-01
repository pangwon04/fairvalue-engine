"use client";
import { cn } from "@/lib/cn";
import { forwardRef, type SelectHTMLAttributes } from "react";
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }>(
  function Select({ className, invalid, children, ...p }, ref) {
    return (
      <select
        ref={ref}
        className={cn(
          "w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-900",
          "focus:outline-none focus:ring-2 focus:ring-navy-500 focus:border-navy-500",
          invalid ? "border-danger" : "border-slate-300", className,
        )}
        {...p}
      >
        {children}
      </select>
    );
  },
);
