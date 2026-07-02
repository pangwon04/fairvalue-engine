"use client";
import { useState } from "react";
import { deleteInstrument } from "@/lib/api/instruments";
import { ApiError } from "@/lib/apiClient";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";

// 결과 유무 예측(status)으로 안내 문구 분기. 실제 soft/hard 는 응답 deleted 로 확정.
export function DeleteInstrumentButton({ instrumentId, status, name, onDeleted }: {
  instrumentId: number; status: string; name: string; onDeleted?: (deleted: "soft" | "hard") => void;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const willArchive = status === "PRICED" || status === "ARCHIVED";

  async function confirm() {
    setBusy(true); setErr("");
    try {
      const r = await deleteInstrument(instrumentId);
      setOpen(false);
      onDeleted?.(r.deleted);
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "삭제 실패");
    } finally { setBusy(false); }
  }

  return (
    <>
      <Button variant="danger" onClick={() => setOpen(true)}>삭제</Button>
      <Modal open={open} title={`상품 삭제 — ${name}`} onClose={() => setOpen(false)} onConfirm={confirm}
        confirmLabel={willArchive ? "보관 처리" : "완전 삭제"} confirmVariant="danger" busy={busy}>
        {willArchive
          ? <p>이 상품은 <b>평가 이력이 있어 보관 처리(Archive)</b>됩니다. 데이터와 평가 기록은 보존되며 목록에서는 숨겨집니다.</p>
          : <p>이 상품을 <b>완전히 삭제</b>합니다. 되돌릴 수 없습니다.</p>}
        {err && <p className="mt-2 text-danger">{err}</p>}
      </Modal>
    </>
  );
}
