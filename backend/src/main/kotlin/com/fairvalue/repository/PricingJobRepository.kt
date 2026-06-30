package com.fairvalue.repository

import com.fairvalue.domain.JobStatus
import com.fairvalue.domain.PricingJobEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 조직 격리: 조회는 orgId 로 스코프. 캐시: 같은 (org,instrument,input_hash,DONE) Job 재사용.
 */
interface PricingJobRepository : JpaRepository<PricingJobEntity, Long> {
    fun findByIdAndOrgId(id: Long, orgId: Long): PricingJobEntity?

    fun findFirstByOrgIdAndInstrumentIdAndInputHashAndStatusOrderByIdAsc(
        orgId: Long, instrumentId: Long, inputHash: String, status: JobStatus,
    ): PricingJobEntity?
}
