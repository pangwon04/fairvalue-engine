package com.fairvalue.dto

import com.fairvalue.domain.JobStatus
import com.fairvalue.domain.PricingJobEntity
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

/**
 * Pricing Job DTO — openapi.yaml PricingTrigger/Job + shared/schemas/pricing-result.schema.json.
 */

/** POST /instruments/{id}/price 요청. rawForm 전체는 싣지 않는다(trigger). */
data class PricingTrigger(
    val model: String? = null,
    val seed: Long? = null,
    val options: JsonNode? = null,
    @JsonProperty("params_override")
    val paramsOverride: JsonNode? = null,
)

/** POST price 응답. */
data class PriceJobResponse(
    @JsonProperty("job_id") val jobId: Long,
    val status: JobStatus,
    val cached: Boolean,
)

/** GET /jobs/{job_id} 응답. */
data class JobDto(
    @JsonProperty("job_id") val jobId: Long,
    @JsonProperty("instrument_id") val instrumentId: Long,
    val status: JobStatus,
    val cached: Boolean,
    @JsonProperty("input_hash") val inputHash: String?,
) {
    companion object {
        fun from(j: PricingJobEntity) = JobDto(
            jobId = j.id!!, instrumentId = j.instrumentId, status = j.status,
            cached = j.cached, inputHash = j.inputHash,
        )
    }
}

// =========================================================================
// PricingResult — shared/schemas/pricing-result.schema.json 1:1.
//   placeholder 단계에서도 형식을 정확히 지킨다(Phase 3 Dummy→Real 교체 시 불변).
// =========================================================================
@JsonInclude(JsonInclude.Include.ALWAYS)
data class PricingResult(
    @JsonProperty("job_id") val jobId: Long,
    @JsonProperty("instrument_id") val instrumentId: Long,
    @JsonProperty("instrument_type") val instrumentType: String,
    @JsonProperty("valuation_date") val valuationDate: String?,
    val status: String,
    @JsonProperty("total_fair_value") val totalFairValue: Double?,
    @JsonProperty("per_unit_value") val perUnitValue: Double?,
    val components: Components,
    @JsonProperty("key_parameters") val keyParameters: KeyParameters,
    val reproducibility: Reproducibility,
    val warnings: List<Issue>,
    val errors: List<Issue>,
)

/** 표준 component key 12종(약식 명칭 0). placeholder = 전부 0, Σ=total=0. */
data class Components(
    @JsonProperty("bond_value") val bondValue: Double?,
    @JsonProperty("preferred_share_value") val preferredShareValue: Double?,
    @JsonProperty("conversion_option_value") val conversionOptionValue: Double?,
    @JsonProperty("exchange_option_value") val exchangeOptionValue: Double?,
    @JsonProperty("warrant_value") val warrantValue: Double?,
    @JsonProperty("redemption_option_value") val redemptionOptionValue: Double?,
    @JsonProperty("issuer_call_value") val issuerCallValue: Double?,
    @JsonProperty("sale_claim_value") val saleClaimValue: Double?,
    @JsonProperty("stock_option_value") val stockOptionValue: Double?,
    @JsonProperty("conditional_option_value") val conditionalOptionValue: Double?,
    @JsonProperty("dilution_effect") val dilutionEffect: Double?,
    @JsonProperty("total_fair_value") val totalFairValue: Double?,
) {
    companion object {
        /** placeholder: 12종 전부 0.0. */
        fun zeros() = Components(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

data class KeyParameters(
    @JsonProperty("model_name") val modelName: String,
    @JsonProperty("model_version") val modelVersion: String,
    @JsonProperty("risk_free_rate") val riskFreeRate: Double? = null,
    val volatility: Double? = null,
    val parity: Double? = null,
    @JsonProperty("lattice_steps") val latticeSteps: Int? = null,
    @JsonProperty("simulation_paths") val simulationPaths: Int? = null,
)

data class Reproducibility(
    @JsonProperty("input_hash") val inputHash: String,
    val seed: Long,
    @JsonProperty("model_version") val modelVersion: String,
    @JsonProperty("computed_at") val computedAt: String,
)

data class Issue(
    val code: String,
    val message: String,
    val field: String? = null,
    val stage: String? = null,
)
