-- =============================================================================
-- FairValue Engine — V2 (Phase 1-B-3)
-- pricing_jobs 에 결과 저장 컬럼 추가.
--   V1 은 input_hash/seed/status/model_version/cached 만 가지며 PricingResult 를
--   담을 곳이 없다. V1 은 수정 금지이므로 V2 로 result_json(jsonb)·completed_at 를 더한다.
--   (PricingResult 형식은 shared/schemas/pricing-result.schema.json.)
-- =============================================================================

ALTER TABLE pricing_jobs
    ADD COLUMN result_json  JSONB,
    ADD COLUMN completed_at TIMESTAMPTZ;
