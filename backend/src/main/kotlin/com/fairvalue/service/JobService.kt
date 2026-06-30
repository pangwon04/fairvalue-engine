package com.fairvalue.service

import com.fairvalue.domain.InstrumentStatus
import com.fairvalue.domain.JobStatus
import com.fairvalue.domain.PricingJobEntity
import com.fairvalue.dto.Issue
import com.fairvalue.dto.JobDto
import com.fairvalue.dto.PriceJobResponse
import com.fairvalue.dto.PricingTrigger
import com.fairvalue.error.ConflictException
import com.fairvalue.error.NotFoundException
import com.fairvalue.pricing.ContextResolver
import com.fairvalue.pricing.PricingEngineClient
import com.fairvalue.repository.InstrumentTermsRepository
import com.fairvalue.repository.PricingJobRepository
import com.fairvalue.security.AuthPrincipal
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
}
