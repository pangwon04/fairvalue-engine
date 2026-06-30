package com.fairvalue.service

import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.YieldCurveUpload
import com.fairvalue.repository.YieldCurveUploadRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 자동매핑 — (org, as_of, kind, grade) 로 적합 커브를 고른다(최신 version 1건).
 * 조회 로직만(평가 resolve 연결은 1-B-3 더미 교체 시점 = Phase 3). 조직 격리.
 */
@Service
class CurveMappingService(private val uploadRepo: YieldCurveUploadRepository) {

    /** 최신 version 의 적합 커브. 없으면 null. */
    @Transactional(readOnly = true)
    fun findActiveCurve(orgId: Long, asOf: LocalDate, kind: CurveKind, grade: String?): YieldCurveUpload? =
        uploadRepo.findLatest(orgId, kind, grade?.takeIf { it.isNotBlank() }, asOf, PageRequest.of(0, 1))
            .firstOrNull()
}
