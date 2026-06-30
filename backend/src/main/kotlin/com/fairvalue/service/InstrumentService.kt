package com.fairvalue.service

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.domain.InstrumentStatus
import com.fairvalue.domain.InstrumentType
import com.fairvalue.dto.CreateInstrumentRequest
import com.fairvalue.dto.InstrumentDto
import com.fairvalue.error.NotFoundException
import com.fairvalue.error.ValidationException
import com.fairvalue.repository.InstrumentRepository
import com.fairvalue.repository.ProjectRepository
import com.fairvalue.security.AuthPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InstrumentService(
    private val instrumentRepo: InstrumentRepository,
    private val projectRepo: ProjectRepository,
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
    fun list(caller: AuthPrincipal, type: InstrumentType?, status: InstrumentStatus?): List<InstrumentDto> {
        val items = when {
            type != null && status != null -> instrumentRepo.findByOrgIdAndTypeAndStatus(caller.orgId, type, status)
            type != null -> instrumentRepo.findByOrgIdAndType(caller.orgId, type)
            status != null -> instrumentRepo.findByOrgIdAndStatus(caller.orgId, status)
            else -> instrumentRepo.findByOrgId(caller.orgId)
        }
        return items.map { InstrumentDto.from(it) }
    }

    @Transactional(readOnly = true)
    fun get(caller: AuthPrincipal, id: Long): InstrumentDto =
        InstrumentDto.from(requireInstrument(caller, id))

    /** 조직 격리: 같은 조직만 조회됨. 없으면 404(타 조직 포함). 다른 서비스에서 재사용. */
    fun requireInstrument(caller: AuthPrincipal, id: Long): InstrumentEntity =
        instrumentRepo.findByIdAndOrgId(id, caller.orgId)
            ?: throw NotFoundException("상품을 찾을 수 없습니다.")
}
