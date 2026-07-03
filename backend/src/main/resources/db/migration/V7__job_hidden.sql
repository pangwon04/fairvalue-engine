-- ===========================================================================
-- V7 — 평가 이력 숨김(soft). 5-8 파트 A.
--   DONE job 은 삭제 대신 hidden_at 기록(데이터·감사자료 보존). FAILED 는 행 삭제(hard).
--   ★감사 불변식: 상품 삭제 판정의 'DONE job 존재' 쿼리는 hidden_at 무관(전체 기준) — 이 컬럼 미참조.
-- ===========================================================================
ALTER TABLE pricing_jobs
    ADD COLUMN hidden_at TIMESTAMPTZ;

CREATE INDEX idx_job_visible ON pricing_jobs (org_id, hidden_at);
