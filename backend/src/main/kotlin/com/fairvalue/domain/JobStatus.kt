package com.fairvalue.domain

/**
 * 평가 Job 상태 (V1 enum job_status). openapi JobStatus 와 동일.
 * shared/schemas/instrument-types.ts 의 JobStatus.
 */
enum class JobStatus {
    QUEUED, RUNNING, DONE, FAILED, PARTIAL
}
