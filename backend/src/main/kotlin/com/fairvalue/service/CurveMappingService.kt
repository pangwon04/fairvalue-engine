package com.fairvalue.service

import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.CurveOrigin
import com.fairvalue.domain.YieldCurveUpload
import com.fairvalue.repository.YieldCurveUploadRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 자동매핑 — (org, as_of, kind, grade) 로 적합 커브를 고른다. 조직 격리.
 * 조회 로직만(평가 resolve 연결은 1-B-3 더미 교체 시점 = Phase 3).
 */
@Service
class CurveMappingService(private val uploadRepo: YieldCurveUploadRepository) {

    /** 최신 version 의 적합 커브(origin 무관). 없으면 null. (2-A) */
    @Transactional(readOnly = true)
    fun findActiveCurve(orgId: Long, asOf: LocalDate, kind: CurveKind, grade: String?): YieldCurveUpload? =
        uploadRepo.findLatest(orgId, kind, grade?.takeIf { it.isNotBlank() }, asOf, PageRequest.of(0, 1))
            .firstOrNull()

    /**
     * 우선순위 선택(2-B): 같은 (org,kind,grade,as_of) 중
     *   origin MANUAL > UPLOAD > BOOTSTRAP, 동일 origin 내 최신 version. 없으면 null.
     */
    @Transactional(readOnly = true)
    fun findByPriority(orgId: Long, asOf: LocalDate, kind: CurveKind, grade: String?): YieldCurveUpload? {
        // findLatest 는 version DESC 정렬. origin 우선순위로 stable-sort 후 첫 항목.
        val all = uploadRepo.findLatest(orgId, kind, grade?.takeIf { it.isNotBlank() }, asOf, PageRequest.of(0, 1000))
        return all.sortedBy { originRank(it.origin) }.firstOrNull()
    }

    private fun originRank(o: CurveOrigin): Int = when (o) {
        CurveOrigin.MANUAL -> 0
        CurveOrigin.UPLOAD -> 1
        CurveOrigin.BOOTSTRAP -> 2
    }
}
