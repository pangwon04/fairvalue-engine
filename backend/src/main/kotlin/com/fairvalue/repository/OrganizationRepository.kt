package com.fairvalue.repository

import com.fairvalue.domain.Organization
import org.springframework.data.jpa.repository.JpaRepository

interface OrganizationRepository : JpaRepository<Organization, Long> {
    fun findByOrgCode(orgCode: String): Organization?
    fun existsByOrgCode(orgCode: String): Boolean
}
