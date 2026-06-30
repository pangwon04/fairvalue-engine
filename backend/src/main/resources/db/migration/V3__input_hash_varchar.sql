-- =============================================================================
-- FairValue Engine — V3 (Phase 1-B-3 hotfix)
-- pricing_jobs.input_hash : CHAR(64)(bpchar, Types#CHAR) → VARCHAR(64).
--   이유: Hibernate ddl-auto=validate 가 String 매핑을 VARCHAR 로 보는데 DB 가 CHAR 라
--         타입 분류 불일치로 SessionFactory 생성이 실패한다. columnDefinition 으로는
--         validate 의 JDBC 타입 코드를 못 바꾸므로 DB 컬럼을 VARCHAR 로 통일한다.
--   input_hash 는 항상 정확히 64자 SHA-256 hex 라 CHAR(64) 일 본질적 이유가 없다.
--   ★ V1/V2 는 수정하지 않는다(이미 적용된 환경 호환). 반드시 새 V3 로 변경한다.
--   ^[a-f0-9]{64}$ CHECK(chk_jobs_input_hash_hex)는 타입 변경 후에도 유지된다
--   (값 내용이 보존되며 Postgres 가 재검증). 인덱스(idx_pricing_jobs_input_hash)도 유지.
-- =============================================================================

ALTER TABLE pricing_jobs
    ALTER COLUMN input_hash TYPE VARCHAR(64);
