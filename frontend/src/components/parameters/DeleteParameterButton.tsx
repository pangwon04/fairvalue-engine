"use client";
import { useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { Modal } from "@/components/ui/Modal";

/**
 * ★5-10 파라미터(커브·변동성) 행 삭제 버튼 + 확인 모달.
 *   단순 hard delete — 숨김 계층 없음. 과거 평가는 평가시점 스냅샷 보존 → 삭제해도 무영향.
 *   삭제 성공 시 onDeleted()로 목록 갱신.
 */
export function DeleteParameterButton({ kind, label, consequence, onDelete, onDeleted }: {
  kind: string;                       // "커브" | "변동성"
  label: string;                      // 표시용 식별(예: "#12 · RISK_FREE · 2024-06-26")
  consequence: string;                // 향후 참조 불가 시 결과 안내(파라미터별 상이)
  onDelete: () => Promise<unknown>;
  onDeleted: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  async function confirm() {
    setBusy(true); setErr("");
    try {
      await onDelete();
      setOpen(false);
      onDeleted();
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "삭제에 실패했습니다.");
    } finally { setBusy(false); }
  }

  return (
    <>
      <button
        type="button"
        className="text-danger hover:underline"
        onClick={(e) => { e.stopPropagation(); setOpen(true); }}
      >
        삭제
      </button>
      <Modal open={open} title={`${kind} 삭제 — ${label}`} onClose={() => setOpen(false)}
        onConfirm={confirm} confirmLabel="삭제" confirmVariant="danger" busy={busy}>
        <p>이 {kind}를 삭제하시겠습니까?</p>
        <p className="mt-2">
          과거 평가는 <b>평가시점 스냅샷을 보존</b>하므로 영향받지 않습니다. 단, 향후 평가에서 이 파라미터를
          더 이상 참조할 수 없습니다({consequence}).
        </p>
        {err && <p className="mt-2 text-danger">{err}</p>}
      </Modal>
    </>
  );
}
