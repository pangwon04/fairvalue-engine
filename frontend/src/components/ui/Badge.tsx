import { cn } from "@/lib/cn";
const tones: Record<string, string> = {
  navy: "bg-navy-50 text-navy-700 ring-navy-200",
  gray: "bg-slate-100 text-slate-600 ring-slate-200",
  success: "bg-green-50 text-success ring-green-200",
  danger: "bg-red-50 text-danger ring-red-200",
  warning: "bg-amber-50 text-warning ring-amber-200",
};
export function Badge({ tone = "gray", children }: { tone?: keyof typeof tones; children: React.ReactNode }) {
  return <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset", tones[tone])}>{children}</span>;
}
