package com.fairvalue.repository

import com.fairvalue.domain.YieldCurvePoint
import org.springframework.data.jpa.repository.JpaRepository

interface YieldCurvePointRepository : JpaRepository<YieldCurvePoint, Long> {
    fun findByUploadIdOrderBySeqAsc(uploadId: Long): List<YieldCurvePoint>
}
