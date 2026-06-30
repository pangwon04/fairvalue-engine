package com.fairvalue.repository

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.domain.InstrumentStatus
import com.fairvalue.domain.InstrumentType
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 조직 격리: 조회는 항상 orgId 로 스코프한다.
 */
interface InstrumentRepository : JpaRepository<InstrumentEntity, Long> {
    fun findByOrgId(orgId: Long): List<InstrumentEntity>
    fun findByIdAndOrgId(id: Long, orgId: Long): InstrumentEntity?
    fun findByOrgIdAndType(orgId: Long, type: InstrumentType): List<InstrumentEntity>
    fun findByOrgIdAndStatus(orgId: Long, status: InstrumentStatus): List<InstrumentEntity>
    fun findByOrgIdAndTypeAndStatus(orgId: Long, type: InstrumentType, status: InstrumentStatus): List<InstrumentEntity>
}
