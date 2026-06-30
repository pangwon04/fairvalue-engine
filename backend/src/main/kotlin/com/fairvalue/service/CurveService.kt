package com.fairvalue.service

import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.CurveOrigin
import com.fairvalue.domain.UserRole
import com.fairvalue.domain.YieldCurvePoint
import com.fairvalue.domain.YieldCurveUpload
import com.fairvalue.dto.CurveDetailDto
import com.fairvalue.dto.CurvePointDto
import com.fairvalue.dto.CurveUploadCommand
import com.fairvalue.dto.CurveUploadDto
import com.fairvalue.dto.CurveUploadResult
import com.fairvalue.error.FieldErrorDto
import com.fairvalue.error.ForbiddenException
import com.fairvalue.error.NotFoundException
import com.fairvalue.error.ValidationException
import com.fairvalue.repository.YieldCurvePointRepository
import com.fairvalue.repository.YieldCurveUploadRepository
import com.fairvalue.security.AuthPrincipal
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 커브 업로드·저장·버전·조회 (Phase 2-A). 계산(보간/부트스트랩)은 2-B.
 *   - 업로드: CURVE_MANAGER 또는 ORG_ADMIN. 검증 위반 422. 같은 키 재업로드 시 version 자동 증가.
 *   - 조회: 인증 전원, org_id 격리(타 조직 404).
 */
@Service
class CurveService(
    private val uploadRepo: YieldCurveUploadRepository,
    private val pointRepo: YieldCurvePointRepository,
) {

    private val WRITE_ROLES = setOf(UserRole.ORG_ADMIN, UserRole.CURVE_MANAGER)

    @Transactional
    fun upload(caller: AuthPrincipal, cmd: CurveUploadCommand): CurveUploadResult {
        if (caller.role !in WRITE_ROLES) {
            throw ForbiddenException("커브 업로드 권한이 없습니다(CURVE_MANAGER 또는 ORG_ADMIN).")
        }
        validate(cmd) // 위반 시 422

        val kind = cmd.kind!!
        val asOf = cmd.asOf!!
        val grade = cmd.grade?.takeIf { it.isNotBlank() }
        val nextVersion = uploadRepo.maxVersion(caller.orgId, kind, grade, asOf) + 1

        val upload = uploadRepo.save(
            YieldCurveUpload(
                orgId = caller.orgId, kind = kind, asOf = asOf, version = nextVersion,
                grade = grade, source = cmd.source,
                interpolationMethod = cmd.interpolationMethod?.takeIf { it.isNotBlank() } ?: "linear",
                origin = cmd.origin ?: CurveOrigin.UPLOAD,
                uploadedBy = caller.userId,
            ),
        )
        cmd.points.forEachIndexed { i, (t, r) ->
            pointRepo.save(YieldCurvePoint(uploadId = upload.id!!, tenorYears = t!!, ratePercent = r!!, seq = i + 1))
        }
        return CurveUploadResult(uploadId = upload.id!!, validation = emptyList())
    }

    private fun validate(cmd: CurveUploadCommand) {
        val errs = mutableListOf<FieldErrorDto>()
        if (cmd.asOf == null) errs += FieldErrorDto("as_of", "평가기준일(as_of)은 필수입니다.")
        if (cmd.kind == null) errs += FieldErrorDto("kind", "커브 종류(kind)는 필수입니다.")
        if (cmd.kind == CurveKind.CREDIT && cmd.grade.isNullOrBlank()) {
            errs += FieldErrorDto("grade", "신용커브는 등급(grade)이 필수입니다.")
        }
        if (cmd.points.isEmpty()) {
            errs += FieldErrorDto("points", "만기점이 비어 있습니다.")
        }
        val seen = mutableListOf<BigDecimal>()
        var prev: BigDecimal? = null
        cmd.points.forEachIndexed { i, (t, r) ->
            if (t == null || r == null) {
                errs += FieldErrorDto("points[$i]", "tenor_years/rate_percent 는 숫자여야 합니다.")
            } else {
                if (t <= BigDecimal.ZERO) errs += FieldErrorDto("points[$i].tenor_years", "만기는 0보다 커야 합니다.")
                if (seen.any { it.compareTo(t) == 0 }) errs += FieldErrorDto("points[$i].tenor_years", "만기 중복: $t")
                seen += t
                if (prev != null && t <= prev) errs += FieldErrorDto("points[$i].tenor_years", "만기는 오름차순이어야 합니다.")
                prev = t
            }
        }
        if (errs.isNotEmpty()) throw ValidationException("커브 검증에 실패했습니다.", errs)
    }

    @Transactional(readOnly = true)
    fun list(caller: AuthPrincipal, kind: CurveKind?, grade: String?, asOf: java.time.LocalDate?): List<CurveUploadDto> {
        // ★ null 필터는 WHERE 절에서 제외(IS NULL OR 패턴 제거 → PG 타입추론 에러 원천 차단).
        val spec = Specification<YieldCurveUpload> { root, _, cb ->
            val ps = mutableListOf<Predicate>()
            ps += cb.equal(root.get<Long>("orgId"), caller.orgId)
            kind?.let { ps += cb.equal(root.get<CurveKind>("kind"), it) }
            grade?.takeIf { it.isNotBlank() }?.let { ps += cb.equal(root.get<String>("grade"), it) }
            asOf?.let { ps += cb.equal(root.get<java.time.LocalDate>("asOf"), it) }
            cb.and(*ps.toTypedArray())
        }
        val sort = Sort.by(Sort.Order.desc("asOf"), Sort.Order.asc("kind"), Sort.Order.desc("version"))
        return uploadRepo.findAll(spec, sort).map { CurveUploadDto.from(it) }
    }

    @Transactional(readOnly = true)
    fun getDetail(caller: AuthPrincipal, id: Long): CurveDetailDto {
        val u = uploadRepo.findByIdAndOrgId(id, caller.orgId)
            ?: throw NotFoundException("커브를 찾을 수 없습니다.")
        val points = pointRepo.findByUploadIdOrderBySeqAsc(id).map { CurvePointDto.from(it) }
        return CurveDetailDto(
            id = u.id!!, kind = u.kind, grade = u.grade, asOf = u.asOf, version = u.version,
            source = u.source, interpolationMethod = u.interpolationMethod, origin = u.origin, points = points,
        )
    }

    /** CSV(템플릿) → 만기점 목록. 상단 #메타·헤더 줄은 건너뛴다. */
    fun parsePointsCsv(text: String): List<Pair<BigDecimal?, BigDecimal?>> {
        val out = mutableListOf<Pair<BigDecimal?, BigDecimal?>>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val cols = line.split(',', ';', '\t').map { it.trim() }
            if (cols.size < 2) continue
            if (cols[0].lowercase().contains("tenor")) continue // 헤더
            out += cols[0].toBigDecimalOrNull() to cols[1].toBigDecimalOrNull()
        }
        return out
    }
}
