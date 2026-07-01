"use client";
import { cn } from "@/lib/cn";
import { forwardRef, type InputHTMLAttributes } from "react";
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(
  function Input({ className, invalid, ...p }, ref) {
    return (
      <input
        ref={ref}
        className={cn(
          "w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-900 tnum",
          "placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-navy-500 focus:border-navy-500",
          invalid ? "border-danger" : "border-slate-300", className,
        )}
        {...p}
      />
    );
  },
);
