-- =============================================================================
-- FairValue Engine — V4 (Phase 2-A) 커브 업로드·저장·버전·자동매핑
--   yield_curve_uploads(헤더/메타) + yield_curve_points(만기점). 조직 격리.
--   ★ V1/V2/V3 는 수정하지 않는다(이미 적용된 환경 호환). 반드시 새 V4.
--   ★ 타입 마찰 예방(1-B-3 교훈): 문자열은 전부 VARCHAR(CHAR 금지),
--      날짜=DATE, 시각=TIMESTAMPTZ, 금리=NUMERIC, enum 은 postgres enum(kind) 또는
--      varchar+CHECK(origin). 모든 컬럼이 엔티티 매핑과 1:1.
-- =============================================================================

-- 커브 종류 enum (신규). 엔티티는 @JdbcType(PostgreSQLEnumJdbcType) 로 매핑.
CREATE TYPE curve_kind AS ENUM ('RISK_FREE', 'CREDIT');

-- =============================================================================
-- yield_curve_uploads — 커브 헤더(메타). 이력 보존(덮어쓰기 금지, 버전 증가).
-- =============================================================================
CREATE TABLE yield_curve_uploads (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id               BIGINT       NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    kind                 curve_kind   NOT NULL,
    grade                VARCHAR(20),                       -- 신용등급(무위험은 NULL)
    as_of                DATE         NOT NULL,
    version              INT          NOT NULL DEFAULT 1,
    source               VARCHAR(200),
    interpolation_method VARCHAR(30)  NOT NULL DEFAULT 'linear',
    origin               VARCHAR(20)  NOT NULL DEFAULT 'UPLOAD',   -- MANUAL/UPLOAD/BOOTSTRAP (2-B 우선순위)
    uploaded_by          BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_curve_origin CHECK (origin IN ('MANUAL', 'UPLOAD', 'BOOTSTRAP')),
    CONSTRAINT chk_curve_version_positive CHECK (version >= 1),
    -- 같은 (org,as_of,kind,grade) 의 동일 version 중복 금지(이력은 version 으로 구분).
    CONSTRAINT uq_curve_upload UNIQUE (org_id, as_of, kind, grade, version)
);
CREATE INDEX idx_curve_lookup ON yield_curve_uploads (org_id, as_of, grade);
CREATE INDEX idx_curve_org_kind ON yield_curve_uploads (org_id, kind);

-- =============================================================================
-- yield_curve_points — 만기별 (tenor_years, rate_percent). seq 로 순서 보존.
-- =============================================================================
CREATE TABLE yield_curve_points (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    upload_id     BIGINT        NOT NULL REFERENCES yield_curve_uploads (id) ON DELETE CASCADE,
    tenor_years   NUMERIC(6, 3) NOT NULL,    -- 0.25=3M ... 50
    rate_percent  NUMERIC(12, 8) NOT NULL,   -- 연 %
    seq           INT           NOT NULL,
    CONSTRAINT chk_point_tenor_positive CHECK (tenor_years > 0),
    CONSTRAINT uq_curve_point UNIQUE (upload_id, tenor_years)
);
CREATE INDEX idx_curve_points_upload ON yield_curve_points (upload_id);
