package com.fairvalue.repository

import com.fairvalue.domain.YieldCurvePoint
import org.springframework.data.jpa.repository.JpaRepository

interface YieldCurvePointRepository : JpaRepository<YieldCurvePoint, Long> {
    fun findByUploadIdOrderBySeqAsc(uploadId: Long): List<YieldCurvePoint>

    /** ★5-10: 커브 hard delete 시 자식 만기점 명시 삭제(FK ON DELETE CASCADE 와 별개로 결정성 확보). */
    fun deleteByUploadId(uploadId: Long): Long
}
