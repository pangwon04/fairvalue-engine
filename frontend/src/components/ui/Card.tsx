import { cn } from "@/lib/cn";
export function Card({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={cn("rounded-xl border border-slate-200 bg-white shadow-sm", className)}>{children}</div>;
}
export function CardHeader({ title, desc, right }: { title: string; desc?: string; right?: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between border-b border-slate-100 px-5 py-4">
      <div>
        <h3 className="text-base font-semibold text-slate-900">{title}</h3>
        {desc && <p className="mt-0.5 text-sm text-slate-500">{desc}</p>}
      </div>
      {right}
    </div>
  );
}
export function CardBody({ className, children }: { className?: string; children: React.ReactNode }) {
  return <div className={cn("px-5 py-4", className)}>{children}</div>;
}
