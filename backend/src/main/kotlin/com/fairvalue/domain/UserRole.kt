package com.fairvalue.domain

/**
 * 사용자 역할 (V1 enum user_role 과 1:1). 권한 위계는 RBAC 에서 해석한다.
 * openapi.yaml 의 Role enum 과 동일.
 */
enum class UserRole {
    ORG_ADMIN,
    CURVE_MANAGER,
    VALUATOR,
    AUDITOR,
    VIEWER,
}
