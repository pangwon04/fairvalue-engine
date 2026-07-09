package com.fairvalue.repository

import com.fairvalue.domain.VolatilityData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * volatility_data 리포지토리. org_id 격리는 서비스 Specification / findByIdAndOrgId 로 강제.
 */
interface VolatilityDataRepository :
    JpaRepository<VolatilityData, Long>,
    JpaSpecificationExecutor<VolatilityData> {

    fun findByIdAndOrgId(id: Long, orgId: Long): VolatilityData?

    /** ★5-10 대시보드: 조직의 등록 변동성 수. */
    fun countByOrgId(orgId: Long): Long
}
