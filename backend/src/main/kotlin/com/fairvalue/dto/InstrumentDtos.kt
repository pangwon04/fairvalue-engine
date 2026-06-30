package com.fairvalue.dto

import com.fairvalue.domain.InstrumentEntity
import com.fairvalue.domain.InstrumentStatus
import com.fairvalue.domain.InstrumentType
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * openapi: POST /instruments {type,name,issuer} → Instrument; GET /instruments(/{id}).
 */
data class CreateInstrumentRequest(
    @field:NotNull(message = "상품 유형은 필수입니다.")
    val type: InstrumentType? = null,

    @field:NotBlank(message = "상품명은 필수입니다.")
    val name: String = "",

    @field:NotBlank(message = "발행사는 필수입니다.")
    val issuer: String = "",

    @JsonProperty("project_id")
    val projectId: Long? = null,
)

data class InstrumentDto(
    val id: Long,
    val type: InstrumentType,
    val name: String,
    val issuer: String,
    val status: InstrumentStatus,
    @JsonProperty("project_id")
    val projectId: Long?,
    @JsonProperty("organization_id")
    val organizationId: Long,
) {
    companion object {
        fun from(i: InstrumentEntity) = InstrumentDto(
            id = i.id!!, type = i.type, name = i.name, issuer = i.issuer,
            status = i.status, projectId = i.projectId, organizationId = i.orgId,
        )
    }
}

data class InstrumentListResponse(
    val items: List<InstrumentDto>,
)
