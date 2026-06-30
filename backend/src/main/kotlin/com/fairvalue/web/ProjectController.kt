package com.fairvalue.web

import com.fairvalue.dto.CreateProjectRequest
import com.fairvalue.dto.ProjectDto
import com.fairvalue.dto.ProjectListResponse
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.ProjectService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** openapi: POST /projects(201), GET /projects. 조직 격리. */
@RestController
class ProjectController(private val projectService: ProjectService) {

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @Valid @RequestBody req: CreateProjectRequest,
    ): ProjectDto = projectService.create(caller, req)

    @GetMapping("/projects")
    fun list(@AuthenticationPrincipal caller: AuthPrincipal): ProjectListResponse =
        ProjectListResponse(items = projectService.list(caller))
}
