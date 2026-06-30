package com.fairvalue.repository

import com.fairvalue.domain.InstrumentTermsEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InstrumentTermsRepository : JpaRepository<InstrumentTermsEntity, Long> {
    fun findByInstrumentIdAndOrgId(instrumentId: Long, orgId: Long): InstrumentTermsEntity?
}
