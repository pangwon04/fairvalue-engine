"use client";
import { useState, type ReactNode } from "react";

/**
 * 경량 아코디언(접기). 자식은 ★열렸을 때만 마운트(지연 렌더) — 큰 트리 표 성능 보호.
 * children 을 컴포넌트로 넘기면 .map 등 무거운 렌더가 open 시점에만 실행된다.
 */
export function Disclosure({ title, right, defaultOpen = false, children }: {
  title: ReactNode; right?: ReactNode; defaultOpen?: boolean; children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="rounded-lg border border-slate-200">
      <button type="button" onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between gap-2 px-4 py-2.5 text-left hover:bg-slate-50">
        <span className="flex items-center gap-2 text-sm font-medium text-slate-800">
          <span className={`text-slate-400 transition-transform ${open ? "rotate-90" : ""}`}>▶</span>
          {title}
        </span>
        <span className="flex items-center gap-2 text-xs text-slate-500">{right}</span>
      </button>
      {open && <div className="border-t border-slate-100 px-4 py-3">{children}</div>}
    </div>
  );
}
