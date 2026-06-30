package com.fairvalue.domain

/**
 * 상품군 (V1 enum instrument_type 과 1:1). openapi.yaml InstrumentType 과 동일.
 * shared/schemas/instrument-types.ts 의 7종.
 */
enum class InstrumentType {
    RCPS, CPS, CB, EB, BW, SO, CSO;

    /** productSchemas 파일명(cb.json 등) 매핑용. */
    fun schemaKey(): String = name.lowercase()
}
