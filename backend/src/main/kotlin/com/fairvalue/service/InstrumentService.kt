package com.fairvalue.service

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.domain.InstrumentStatus
import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.CreateInstrumentRequest
import com.fairvalue.dto.DeleteInstrumentResponse
import com.fairvalue.dto.InstrumentDto
import com.fairvalue.domain.JobStatus
import com.fairvalue.error.NotFoundException
import com.fairvalue.error.ValidationException
import com.fairvalue.repository.InstrumentRepository
import com.fairvalue.repository.InstrumentTermsRepository
import com.fairvalue.repository.PricingJobRepository
import com.fairvalue.repository.ProjectRepository
import com.fairvalue.security.AuthPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InstrumentService(
    private val instrumentRepo: InstrumentRepository,
    private val projectRepo: ProjectRepository,
    private val pricingJobRepo: PricingJobRepository,
    private val termsRepo: InstrumentTermsRepository,
) {

    @Transactional
    fun create(caller: AuthPrincipal, req: CreateInstrumentRequest): InstrumentDto {
        WriteAccess.require(caller)
        val type = req.type ?: throw ValidationException("상품 유형은 필수입니다.")
        // project_id 가 주어지면 같은 조직 소속이어야 한다(타 조직 project 참조 차단).
        req.projectId?.let { pid ->
            projectRepo.findByIdAndOrgId(pid, caller.orgId)
                ?: throw NotFoundException("프로젝트를 찾을 수 없습니다.")
        }
        val saved = instrumentRepo.save(
            InstrumentEntity(
                orgId = caller.orgId,
                type = type,
                name = req.name,
                issuer = req.issuer,
                status = InstrumentStatus.DRAFT,
                projectId = req.projectId,
                createdBy = caller.userId,
            ),
        )
        return InstrumentDto.from(saved)
    }

    @Transactional(readOnly = true)
    fun list(
        caller: AuthPrincipal,
        type: InstrumentType?,
        status: InstrumentStatus?,
        includeArchived: Boolean = false,
    ): List<InstrumentDto> {
        val items = when {
            type != null && status != null -> instrumentRepo.findByOrgIdAndTypeAndStatus(caller.orgId, type, status)
            type != null -> instrumentRepo.findByOrgIdAndType(caller.orgId, type)
            status != null -> instrumentRepo.findByOrgIdAndStatus(caller.orgId, status)
            else -> instrumentRepo.findByOrgId(caller.orgId)
        }
        // ★ 기본은 ARCHIVED 제외(활성 상품만). status 명시 또는 include_archived=true 면 포함.
        val visible = if (status != null || includeArchived) items
        else items.filter { it.status != InstrumentStatus.ARCHIVED }
        return visible.map { InstrumentDto.from(it) }
    }

    // ★ Phase 5-5: soft(ARCHIVED 보관) / hard(완전삭제) 분기 삭제.
    //   결과 있는 Job(DONE) 존재 또는 status=PRICED → soft(데이터·이력 보존, 감사 추적성).
    //   그 외 → hard(FK 순서 terms→jobs→instrument).
    @Transactional
    fun delete(caller: AuthPrincipal, id: Long): DeleteInstrumentResponse {
        WriteAccess.require(caller)                                  // VALUATOR 이상(403)
        val inst = instrumentRepo.findByIdAndOrgId(id, caller.orgId) // 타 조직/없음 404
            ?: throw NotFoundException("상품을 찾을 수 없습니다.")

        if (inst.status == InstrumentStatus.ARCHIVED) {
            return DeleteInstrumentResponse("soft", id, InstrumentStatus.ARCHIVED)   // 멱등(이미 보관)
        }

        val hasResult = inst.status == InstrumentStatus.PRICED ||
            pricingJobRepo.existsByOrgIdAndInstrumentIdAndStatus(caller.orgId, id, JobStatus.DONE)

        return if (hasResult) {
            inst.status = InstrumentStatus.ARCHIVED
            instrumentRepo.save(inst)
            DeleteInstrumentResponse("soft", id, InstrumentStatus.ARCHIVED)
        } else {
            // hard: 참조 무결성 위해 terms → jobs → instrument 순서로 삭제.
            termsRepo.findByInstrumentIdAndOrgId(id, caller.orgId)?.let { termsRepo.delete(it) }
            val jobs = pricingJobRepo.findByOrgIdAndInstrumentId(caller.orgId, id)
            if (jobs.isNotEmpty()) pricingJobRepo.deleteAll(jobs)
            instrumentRepo.delete(inst)
            DeleteInstrumentResponse("hard", id, null)
        }
    }

    @Transactional(readOnly = true)
    fun get(caller: AuthPrincipal, id: Long): InstrumentDto =
        InstrumentDto.from(requireInstrument(caller, id))

    /** 조직 격리: 같은 조직만 조회됨. 없으면 404(타 조직 포함). 다른 서비스에서 재사용. */
    fun requireInstrument(caller: AuthPrincipal, id: Long): InstrumentEntity =
        instrumentRepo.findByIdAndOrgId(id, caller.orgId)
            ?: throw NotFoundException("상품을 찾을 수 없습니다.")
}
