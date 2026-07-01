"use client";
import { cn } from "@/lib/cn";
import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
export function Button({ variant = "primary", className, ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  const styles: Record<Variant, string> = {
    primary: "bg-navy-800 text-white hover:bg-navy-700 focus:ring-navy-500",
    secondary: "bg-white text-navy-800 border border-slate-300 hover:bg-slate-50 focus:ring-navy-400",
    ghost: "bg-transparent text-navy-700 hover:bg-navy-50 focus:ring-navy-300",
    danger: "bg-danger text-white hover:opacity-90 focus:ring-red-400",
  };
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center rounded-lg px-4 py-2 text-sm font-medium transition",
        "focus:outline-none focus:ring-2 focus:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed",
        styles[variant], className,
      )}
      {...p}
    />
  );
}
