package com.fairvalue.service

import com.fairvalue.curve.InterpolatedCurve
import com.fairvalue.error.NotFoundException
import com.fairvalue.repository.YieldCurvePointRepository
import com.fairvalue.repository.YieldCurveUploadRepository
import com.fairvalue.security.AuthPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 저장된 커브로 zero/df/forward 를 계산한다(Phase 2-B). 조직 격리(org_id).
 * 실제 보간/복리 정의는 InterpolatedCurve 참조. 음의 forward 경고는 curve.warnings() 로 노출.
 */
@Service
class CurveCalcService(
    private val uploadRepo: YieldCurveUploadRepository,
    private val pointRepo: YieldCurvePointRepository,
) {

    /** uploadId(같은 조직) → 보간 커브. 없으면 404. */
    @Transactional(readOnly = true)
    fun curveOf(caller: AuthPrincipal, uploadId: Long): InterpolatedCurve {
        val u = uploadRepo.findByIdAndOrgId(uploadId, caller.orgId)
            ?: throw NotFoundException("커브를 찾을 수 없습니다.")
        val pts = pointRepo.findByUploadIdOrderBySeqAsc(uploadId)
            .map { it.tenorYears.toDouble() to it.ratePercent.toDouble() }
        if (pts.isEmpty()) throw NotFoundException("커브 포인트가 없습니다.")
        return InterpolatedCurve(pts, u.interpolationMethod)
    }

    @Transactional(readOnly = true)
    fun zeroRate(caller: AuthPrincipal, uploadId: Long, t: Double): Double = curveOf(caller, uploadId).zeroRate(t)

    @Transactional(readOnly = true)
    fun discountFactor(caller: AuthPrincipal, uploadId: Long, t: Double): Double = curveOf(caller, uploadId).discountFactor(t)

    @Transactional(readOnly = true)
    fun forwardRate(caller: AuthPrincipal, uploadId: Long, t1: Double, t2: Double): Double =
        curveOf(caller, uploadId).forwardRate(t1, t2)
}
