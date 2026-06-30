package com.fairvalue.service

import com.fairvalue.domain.Project
import com.fairvalue.dto.CreateProjectRequest
import com.fairvalue.dto.ProjectDto
import com.fairvalue.repository.ProjectRepository
import com.fairvalue.security.AuthPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectService(private val projectRepo: ProjectRepository) {

    @Transactional
    fun create(caller: AuthPrincipal, req: CreateProjectRequest): ProjectDto {
        WriteAccess.require(caller)
        val saved = projectRepo.save(
            Project(orgId = caller.orgId, name = req.name, createdBy = caller.userId),
        )
        return ProjectDto.from(saved)
    }

    @Transactional(readOnly = true)
    fun list(caller: AuthPrincipal): List<ProjectDto> =
        projectRepo.findByOrgId(caller.orgId).map { ProjectDto.from(it) }
}
