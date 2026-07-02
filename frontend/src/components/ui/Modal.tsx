"use client";
import { Button } from "@/components/ui/Button";

export function Modal({ open, title, children, onClose, onConfirm, confirmLabel, confirmVariant = "primary", busy }: {
  open: boolean; title: string; children: React.ReactNode;
  onClose: () => void; onConfirm: () => void; confirmLabel: string;
  confirmVariant?: "primary" | "danger"; busy?: boolean;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div className="w-full max-w-md rounded-xl bg-white shadow-lg" onClick={(e) => e.stopPropagation()}>
        <div className="border-b border-slate-100 px-5 py-4">
          <h3 className="text-base font-semibold text-slate-900">{title}</h3>
        </div>
        <div className="px-5 py-4 text-sm text-slate-600">{children}</div>
        <div className="flex justify-end gap-2 border-t border-slate-100 px-5 py-3">
          <Button variant="secondary" onClick={onClose} disabled={busy}>취소</Button>
          <Button variant={confirmVariant} onClick={onConfirm} disabled={busy}>{busy ? "처리 중…" : confirmLabel}</Button>
        </div>
      </div>
    </div>
  );
}
