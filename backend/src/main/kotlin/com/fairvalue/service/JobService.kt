package com.fairvalue.service

import com.fairvalue.domain.InstrumentStatus
import com.fairvalue.domain.InstrumentType
import com.fairvalue.domain.JobStatus
import com.fairvalue.domain.PricingJobEntity
import com.fairvalue.dto.Issue
import com.fairvalue.dto.JobContextDto
import com.fairvalue.dto.JobDto
import com.fairvalue.dto.JobSummaryDto
import com.fairvalue.dto.PriceJobResponse
import com.fairvalue.dto.PricingTrigger
import com.fairvalue.error.ConflictException
import com.fairvalue.error.NotFoundException
import com.fairvalue.pricing.ContextResolver
import com.fairvalue.pricing.PricingEngineClient
import com.fairvalue.repository.InstrumentRepository
import com.fairvalue.repository.InstrumentTermsRepository
import com.fairvalue.repository.PricingJobRepository
import com.fairvalue.security.AuthPrincipal
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Pricing Job 파이프라인(동기, Phase 1-B-3).
 *   trigger → terms 로드 → ContextResolver.resolve → InputHash → 캐시 → PricingEngineClient.price
 *           → result 저장 → 상태 QUEUED→RUNNING→DONE.
 *
 * ★ resolve·엔진은 더미(인터페이스 주입). 호출부는 인터페이스에만 의존 → Phase 3 교체 시 불변.
 * 조직 격리: instrument/job/result 모두 org_id 스코프. 권한: 실행=VALUATOR+, 조회=인증된 전원.
 */
@Service
class JobService(
    private val instrumentService: InstrumentService,
    private val termsRepo: InstrumentTermsRepository,
    private val jobRepo: PricingJobRepository,
    private val instrumentRepo: InstrumentRepository,
    private val resolver: ContextResolver,
    private val engine: PricingEngineClient,
    private val mapper: ObjectMapper,
) {

    @Transactional
    fun price(caller: AuthPrincipal, instrumentId: Long, trigger: PricingTrigger): PriceJobResponse {
        WriteAccess.require(caller) // 실행은 VALUATOR 이상
        val instrument = instrumentService.requireInstrument(caller, instrumentId) // 타 조직 404
        val terms = termsRepo.findByInstrumentIdAndOrgId(instrumentId, caller.orgId)
            ?: throw ConflictException("계약조건(terms)이 저장되지 않았습니다.")

        val rawForm = mapper.readTree(terms.termsJson)
        val ctx = resolver.resolve(rawForm, trigger, instrument.type, caller.orgId)

        // 캐시: 같은 (org, instrument, input_hash) 의 DONE Job 이 있으면 재사용.
        jobRepo.findFirstByOrgIdAndInstrumentIdAndInputHashAndStatusOrderByIdAsc(
            caller.orgId, instrumentId, ctx.inputHash, JobStatus.DONE,
        )?.let { return PriceJobResponse(it.id!!, JobStatus.DONE, cached = true) }

        // 신규 Job: QUEUED → RUNNING → DONE (동기).
        var job = jobRepo.save(
            PricingJobEntity(
                orgId = caller.orgId, instrumentId = instrumentId, status = JobStatus.QUEUED,
                inputHash = ctx.inputHash, seed = ctx.seed, modelVersion = ctx.modelVersion,
                cached = false, createdBy = caller.userId,
            ),
        )
        job.status = JobStatus.RUNNING
        // ★5-7(V6): 평가시점 입력 스냅샷 저장(DONE·FAILED 공통 — 감사 추적성).
        job.contextJson = mapper.writeValueAsString(ctx.contextJson)
        job = jobRepo.save(job)

        return try {
            val result = engine.price(ctx, instrument, job.id!!)
            job.resultJson = mapper.writeValueAsString(result)
            job.status = JobStatus.DONE
            job.completedAt = OffsetDateTime.now()
            jobRepo.save(job)
            instrument.status = InstrumentStatus.PRICED // 같은 tx 내 managed → flush
            PriceJobResponse(job.id!!, JobStatus.DONE, cached = false)
        } catch (e: Exception) {
            job.status = JobStatus.FAILED
            job.resultJson = mapper.writeValueAsString(
                mapOf("errors" to listOf(Issue(code = "E101", message = e.message ?: "계산 실패", stage = "engine"))),
            )
            jobRepo.save(job)
            PriceJobResponse(job.id!!, JobStatus.FAILED, cached = false)
        }
    }

    // Phase 5-5: 평가 이력 목록. Job(최신순) + instrument 조인 + DONE resultJson 파싱.
    @Transactional(readOnly = true)
    fun listJobs(
        caller: AuthPrincipal,
        type: InstrumentType?,
        status: JobStatus?,
        from: LocalDate?,
        to: LocalDate?,
    ): List<JobSummaryDto> {
        val instMap = instrumentRepo.findByOrgId(caller.orgId).associateBy { it.id }
        return jobRepo.findByOrgIdOrderByIdDesc(caller.orgId).mapNotNull { j ->
            val inst = instMap[j.instrumentId]
            if (type != null && inst?.type != type) return@mapNotNull null
            if (status != null && j.status != status) return@mapNotNull null
            val createdDate = j.createdAt?.toLocalDate()
            if (from != null && createdDate != null && createdDate.isBefore(from)) return@mapNotNull null
            if (to != null && createdDate != null && createdDate.isAfter(to)) return@mapNotNull null

            var total: Double? = null
            var valDate: String? = null
            var model: String? = null
            if (j.status == JobStatus.DONE && !j.resultJson.isNullOrBlank()) {
                runCatching {
                    val node = mapper.readTree(j.resultJson)
                    node.get("total_fair_value")?.takeIf { it.isNumber }?.let { total = it.asDouble() }
                    node.get("valuation_date")?.takeIf { it.isTextual }?.let { valDate = it.asText() }
                    node.path("key_parameters").get("model_name")?.takeIf { it.isTextual }?.let { model = it.asText() }
                }
            }
            JobSummaryDto(
                jobId = j.id!!, instrumentId = j.instrumentId,
                instrumentName = inst?.name, instrumentType = inst?.type?.name,
                valuationDate = valDate, model = model, status = j.status,
                totalFairValue = total, createdAt = j.createdAt?.toString(),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getJob(caller: AuthPrincipal, jobId: Long): JobDto {
        val job = jobRepo.findByIdAndOrgId(jobId, caller.orgId)
            ?: throw NotFoundException("Job 을 찾을 수 없습니다.")
        return JobDto.from(job)
    }

    @Transactional(readOnly = true)
    fun getResult(caller: AuthPrincipal, jobId: Long): JsonNode {
        val job = jobRepo.findByIdAndOrgId(jobId, caller.orgId)
            ?: throw NotFoundException("Job 을 찾을 수 없습니다.")
        val json = job.resultJson
            ?: throw ConflictException("결과가 아직 없습니다(status=${job.status}).")
        return mapper.readTree(json)
    }

    // ★5-7: 평가시점 입력 스냅샷(contextJson). org 격리 동일(타 조직 404). 구 job 은 has_context=false.
    @Transactional(readOnly = true)
    fun getContext(caller: AuthPrincipal, jobId: Long): JobContextDto {
        val job = jobRepo.findByIdAndOrgId(jobId, caller.orgId)
            ?: throw NotFoundException("Job 을 찾을 수 없습니다.")
        val ctx = job.contextJson?.let { runCatching { mapper.readTree(it) }.getOrNull() }
        return JobContextDto(jobId = job.id!!, hasContext = ctx != null, context = ctx)
    }
}
