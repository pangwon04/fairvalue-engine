package com.fairvalue.repository

import com.fairvalue.domain.Project
import org.springframework.data.jpa.repository.JpaRepository

interface ProjectRepository : JpaRepository<Project, Long> {
    fun findByOrgId(orgId: Long): List<Project>
    fun findByIdAndOrgId(id: Long, orgId: Long): Project?
}
