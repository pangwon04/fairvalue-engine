package com.fairvalue.dto

import com.fairvalue.domain.Project
import jakarta.validation.constraints.NotBlank

/** openapi: POST /projects {name} → {id,name}; GET /projects → {items[]}. */
data class CreateProjectRequest(
    @field:NotBlank(message = "프로젝트 이름은 필수입니다.")
    val name: String = "",
)

data class ProjectDto(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(p: Project) = ProjectDto(id = p.id!!, name = p.name)
    }
}

data class ProjectListResponse(
    val items: List<ProjectDto>,
)
