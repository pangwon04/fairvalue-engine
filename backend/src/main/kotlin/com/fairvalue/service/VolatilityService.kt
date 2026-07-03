package com.fairvalue.service

import com.fairvalue.domain.UserRole
import com.fairvalue.domain.VolatilityData
import com.fairvalue.dto.CompanyVolDto
import com.fairvalue.dto.VolatilityComputeResponse
import com.fairvalue.dto.VolatilityDetailDto
import com.fairvalue.dto.VolatilityDto
import com.fairvalue.dto.VolatilityRegisterRequest
import com.fairvalue.dto.VolatilityRegisterResult
import com.fairvalue.error.FieldErrorDto
import com.fairvalue.error.ForbiddenException
import com.fairvalue.error.NotFoundException
import com.fairvalue.error.ValidationException
import com.fairvalue.repository.VolatilityDataRepository
import com.fairvalue.security.AuthPrincipal
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 변동성 등록·산출·조회 (E3b).
 *   - 산출(compute): CSV들을 VolatilityCalculator 로 계산 → 미리보기만(저장 안 함, parse-kofia 패턴).
 *   - 등록(register): 미리보기 확인 후 채택값+detail 저장. DIRECT(직접) | PEER_CSV.
 *   - 조회: 인증 전원, org_id 격리(타 조직 404).
 *   - 등록/산출 권한: CURVE_MANAGER 또는 ORG_ADMIN(파라미터 관리 권한 — 커브와 동일 재사용).
 */
@Service
class VolatilityService(
    private val repo: VolatilityDataRepository,
    private val mapper: ObjectMapper,
) {

    private val WRITE_ROLES = setOf(UserRole.ORG_ADMIN, UserRole.CURVE_MANAGER)

    private fun requireWrite(caller: AuthPrincipal) {
        if (caller.role !in WRITE_ROLES) {
            throw ForbiddenException("변동성 등록/산출 권한이 없습니다(CURVE_MANAGER 또는 ORG_ADMIN).")
        }
    }

    /** ★미리보기(저장 안 함). files = (파일명, CSV bytes). trading_days null → 기본 250. */
    fun compute(caller: AuthPrincipal, files: List<Pair<String, ByteArray>>, tradingDays: Int?): VolatilityComputeResponse {
        requireWrite(caller)
        if (files.isEmpty()) throw ValidationException("주가 CSV 파일이 필요합니다.", listOf(FieldErrorDto("files", "CSV 파일 1개 이상 업로드하세요.")))
        val decoded = files.map { (name, bytes) ->
            val text = try {
                VolatilityCalculator.decodeCsv(bytes)
            } catch (e: IllegalArgumentException) {
                throw ValidationException("CSV 해석 실패($name): ${e.message}", listOf(FieldErrorDto(name, e.message ?: "인코딩 오류")))
            }
            name to text
        }
        val td = tradingDays ?: VolatilityCalculator.ANNUAL_TRADING_DAYS_DEFAULT
        val r = VolatilityCalculator.compute(decoded, td)
        return VolatilityComputeResponse(
            companies = r.companies.map { CompanyVolDto.from(it) },
            averagePercent = r.average * 100.0,
            tradingDaysUsed = r.tradingDaysUsed,
            warnings = r.warnings,
        )
    }

    @Transactional
    fun register(caller: AuthPrincipal, req: VolatilityRegisterRequest): VolatilityRegisterResult {
        requireWrite(caller)
        val errs = mutableListOf<FieldErrorDto>()
        if (req.asOf == null) errs += FieldErrorDto("as_of", "기준일(as_of)은 필수입니다.")
        if (req.label.isNullOrBlank()) errs += FieldErrorDto("label", "대상 라벨은 필수입니다.")
        if (req.annualVolPercent == null) errs += FieldErrorDto("annual_vol_percent", "연변동성(채택값)은 필수입니다.")
        else if (req.annualVolPercent < BigDecimal.ZERO) errs += FieldErrorDto("annual_vol_percent", "연변동성은 0 이상이어야 합니다.")
        val method = (req.method ?: "DIRECT").uppercase()
        if (method != "DIRECT" && method != "PEER_CSV") errs += FieldErrorDto("method", "method 는 DIRECT 또는 PEER_CSV.")
        val td = req.tradingDaysUsed ?: VolatilityCalculator.ANNUAL_TRADING_DAYS_DEFAULT
        if (td <= 0) errs += FieldErrorDto("trading_days_used", "거래일수는 0보다 커야 합니다.")
        if (errs.isNotEmpty()) throw ValidationException("변동성 등록 검증에 실패했습니다.", errs)

        val detailJson = buildDetailJson(method, req, td)
        val saved = repo.save(
            VolatilityData(
                orgId = caller.orgId,
                asOf = req.asOf!!,
                label = req.label!!.trim(),
                annualVolPercent = req.annualVolPercent!!,
                method = method,
                tradingDaysUsed = td,
                detailJson = detailJson,
                createdBy = caller.userId,
            ),
        )
        return VolatilityRegisterResult(volatilityId = saved.id!!)
    }

    /**
     * detail_json 조립(추적성). ★필수 키: source_filenames, uploaded_at, companies, computed_average,
     *   adopted_value, edited, trading_days_used, warnings. (상세 화면·보고서가 그대로 읽음)
     */
    private fun buildDetailJson(method: String, req: VolatilityRegisterRequest, td: Int): String {
        val d = req.detail
        val adopted = req.annualVolPercent?.toDouble() ?: 0.0
        val computed = d?.computedAveragePercent ?: adopted
        val edited = d?.edited ?: (d?.computedAveragePercent != null &&
            kotlin.math.abs((d.computedAveragePercent) - adopted) > 1e-9)
        val map = linkedMapOf<String, Any?>(
            "method" to method,
            "uploaded_at" to OffsetDateTime.now().toString(),
            "source_filenames" to (d?.sourceFilenames ?: emptyList<String>()),
            "companies" to (d?.companies ?: emptyList<CompanyVolDto>()),
            "computed_average" to computed,
            "adopted_value" to adopted,
            "edited" to edited,
            "trading_days_used" to td,
            "warnings" to (d?.warnings ?: emptyList<String>()),
            "source_note" to req.sourceNote,
        )
        return mapper.writeValueAsString(map)
    }

    @Transactional(readOnly = true)
    fun list(caller: AuthPrincipal, asOf: LocalDate?, label: String?): List<VolatilityDto> {
        val spec = Specification<VolatilityData> { root, _, cb ->
            val ps = mutableListOf<Predicate>()
            ps += cb.equal(root.get<Long>("orgId"), caller.orgId)
            asOf?.let { ps += cb.equal(root.get<LocalDate>("asOf"), it) }
            label?.takeIf { it.isNotBlank() }?.let { ps += cb.equal(root.get<String>("label"), it) }
            cb.and(*ps.toTypedArray())
        }
        val sort = Sort.by(Sort.Order.desc("asOf"), Sort.Order.desc("id"))
        return repo.findAll(spec, sort).map { VolatilityDto.from(it) }
    }

    @Transactional(readOnly = true)
    fun getDetail(caller: AuthPrincipal, id: Long): VolatilityDetailDto {
        val v = repo.findByIdAndOrgId(id, caller.orgId)
            ?: throw NotFoundException("변동성 레코드를 찾을 수 없습니다.")
        val detail = v.detailJson?.let { runCatching { mapper.readTree(it) }.getOrNull() }
        return VolatilityDetailDto(
            id = v.id!!, asOf = v.asOf, label = v.label, annualVolPercent = v.annualVolPercent,
            method = v.method, tradingDaysUsed = v.tradingDaysUsed, detail = detail,
            createdBy = v.createdBy, createdAt = v.createdAt,
        )
    }
}
