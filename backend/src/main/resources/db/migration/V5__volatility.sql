-- ===========================================================================
-- V5 — 변동성 파라미터 관리(volatility_data). E3b.
--   평가 파라미터(주가변동성)를 직접 입력 또는 유사회사 주가 CSV 산출로 등록·관리.
--   org_id 격리. 산출근거(유사회사표·거래일수·편집여부·파일명 등)는 detail_json(JSONB) 보존.
-- ===========================================================================
CREATE TABLE volatility_data (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id              BIGINT        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    as_of               DATE          NOT NULL,
    label               VARCHAR(200)  NOT NULL,
    annual_vol_percent  NUMERIC(12, 8) NOT NULL,      -- 채택(연변동성 %)
    method              VARCHAR(20)   NOT NULL DEFAULT 'DIRECT',
    trading_days_used   INT           NOT NULL DEFAULT 250,
    detail_json         JSONB,                        -- 유사회사 산출표·원산출평균·편집여부·파일명·warnings
    created_by          BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_vol_method CHECK (method IN ('DIRECT', 'PEER_CSV')),
    CONSTRAINT chk_vol_annual_nonneg CHECK (annual_vol_percent >= 0),
    CONSTRAINT chk_vol_trading_days CHECK (trading_days_used > 0)
);

CREATE INDEX idx_vol_lookup ON volatility_data (org_id, as_of);
CREATE INDEX idx_vol_org_label ON volatility_data (org_id, label);
