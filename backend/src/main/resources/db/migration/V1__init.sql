-- =============================================================================
-- FairValue Engine — V1 init (Phase 1-A)
-- 범위: Phase 1 핵심 7개 테이블 + enum 3종.
--   organizations / users / projects / instruments / instrument_terms /
--   pricing_jobs / audit_logs
-- 나머지(curve/option/redemption/result/simulation 등)는 V2~ 후속 마이그레이션.
-- 전제: 조직 격리(org_id) — 주요 테이블에 org_id FK + index.
-- =============================================================================

-- ---- enum 타입 (계약/실행계획서 §8 준수) ----
CREATE TYPE user_role AS ENUM ('ORG_ADMIN', 'CURVE_MANAGER', 'VALUATOR', 'AUDITOR', 'VIEWER');
CREATE TYPE instrument_type AS ENUM ('RCPS', 'CPS', 'CB', 'EB', 'BW', 'SO', 'CSO');
CREATE TYPE job_status AS ENUM ('QUEUED', 'RUNNING', 'DONE', 'FAILED', 'PARTIAL');

-- 상품 상태(폼/평가 라이프사이클). openapi.yaml InstrumentStatus 와 정렬.
CREATE TYPE instrument_status AS ENUM ('DRAFT', 'TERMS_SAVED', 'PRICED', 'ARCHIVED');

-- =============================================================================
-- organizations — 격리 루트 단위
-- =============================================================================
CREATE TABLE organizations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    org_code    VARCHAR(50)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- =============================================================================
-- users — 조직 소속 사용자(5역할 RBAC)
-- =============================================================================
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id         BIGINT       NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    role           user_role    NOT NULL DEFAULT 'VIEWER',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 조직 내 email 유일(조직 격리 전제)
    CONSTRAINT uq_users_org_email UNIQUE (org_id, email)
);
CREATE INDEX idx_users_org ON users (org_id);

-- =============================================================================
-- projects — 평가 묶음
-- =============================================================================
CREATE TABLE projects (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id      BIGINT       NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    created_by  BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_projects_org ON projects (org_id);

-- =============================================================================
-- instruments — 평가 대상 상품
-- =============================================================================
CREATE TABLE instruments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id      BIGINT            NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    project_id  BIGINT            REFERENCES projects (id) ON DELETE SET NULL,
    type        instrument_type   NOT NULL,
    name        VARCHAR(200)      NOT NULL,
    issuer      VARCHAR(200)      NOT NULL,
    status      instrument_status NOT NULL DEFAULT 'DRAFT',
    created_by  BIGINT            REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ       NOT NULL DEFAULT now()
);
CREATE INDEX idx_instruments_org ON instruments (org_id);
CREATE INDEX idx_instruments_project ON instruments (project_id);
CREATE INDEX idx_instruments_type ON instruments (type);

-- =============================================================================
-- instrument_terms — 계약·권리조건(rawForm draft) 저장
--   PUT /instruments/{id}/terms 의 본문(rawForm)을 JSONB 로 보관.
--   instrument 당 1행(최신본). 이력은 후속(버전 테이블)에서 다룬다.
-- =============================================================================
CREATE TABLE instrument_terms (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id   BIGINT      NOT NULL REFERENCES instruments (id) ON DELETE CASCADE,
    org_id          BIGINT      NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    terms_json      JSONB       NOT NULL,
    valuation_date  DATE,
    issue_date      DATE,
    maturity_date   DATE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- instrument 당 단일 최신 terms
    CONSTRAINT uq_instrument_terms_instrument UNIQUE (instrument_id),
    -- 만기 > 발행 (둘 다 있을 때만)
    CONSTRAINT chk_terms_maturity_after_issue
        CHECK (maturity_date IS NULL OR issue_date IS NULL OR maturity_date > issue_date)
);
CREATE INDEX idx_instrument_terms_org ON instrument_terms (org_id);

-- =============================================================================
-- pricing_jobs — 평가 Job(재현성 메타 포함)
--   input_hash CHAR(64) = SHA-256 hex. seed BIGINT. cached = 캐시 적중 여부.
-- =============================================================================
CREATE TABLE pricing_jobs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id         BIGINT      NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    instrument_id  BIGINT      NOT NULL REFERENCES instruments (id) ON DELETE CASCADE,
    status         job_status  NOT NULL DEFAULT 'QUEUED',
    input_hash     CHAR(64),
    seed           BIGINT,
    model_version  VARCHAR(50),
    cached         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- input_hash 는 64자리 소문자 hex (있을 때만)
    CONSTRAINT chk_jobs_input_hash_hex
        CHECK (input_hash IS NULL OR input_hash ~ '^[a-f0-9]{64}$')
);
CREATE INDEX idx_pricing_jobs_org ON pricing_jobs (org_id);
CREATE INDEX idx_pricing_jobs_instrument ON pricing_jobs (instrument_id);
CREATE INDEX idx_pricing_jobs_input_hash ON pricing_jobs (input_hash);

-- =============================================================================
-- audit_logs — 추적성(누가/무엇을/언제)
-- =============================================================================
CREATE TABLE audit_logs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id      BIGINT      NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id     BIGINT      REFERENCES users (id) ON DELETE SET NULL,
    action      VARCHAR(100) NOT NULL,
    entity      VARCHAR(100),
    entity_id   BIGINT,
    detail      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_org ON audit_logs (org_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
