-- ===========================================================================
-- V8 — 평가보고서 발급(valuation_reports). 5-8 파트 B.
--   DONE 평가에서 PDF(보고서)+엑셀(계산근거 raw) 생성·저장. bytea 보관.
--   report_no: org 별 발급번호(동시성 방지 위해 (org_id, report_no) UNIQUE). 재발급=새 레코드.
-- ===========================================================================
CREATE TABLE valuation_reports (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id         BIGINT       NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    job_id         BIGINT       NOT NULL REFERENCES pricing_jobs (id) ON DELETE CASCADE,
    instrument_id  BIGINT       NOT NULL,
    report_no      VARCHAR(40)  NOT NULL,
    valuation_date VARCHAR(20),
    issued_by      BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    issued_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    pdf_bytes      BYTEA        NOT NULL,
    excel_bytes    BYTEA        NOT NULL,
    CONSTRAINT uq_report_no UNIQUE (org_id, report_no)
);

CREATE INDEX idx_report_org ON valuation_reports (org_id, id DESC);
CREATE INDEX idx_report_job ON valuation_reports (job_id);
