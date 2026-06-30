package com.fairvalue.domain

/**
 * 상품 라이프사이클 상태 (V1 enum instrument_status). openapi InstrumentStatus 와 동일.
 */
enum class InstrumentStatus {
    DRAFT, TERMS_SAVED, PRICED, ARCHIVED
}
