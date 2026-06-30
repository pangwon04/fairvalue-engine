package com.fairvalue.domain

/**
 * 커브 출처/우선순위 (V4 origin varchar+CHECK). 우선순위 수기>업로드>부트스트랩(2-B 에서 사용).
 * varchar 매핑(@Enumerated STRING)이라 postgres enum 타입 마찰이 없다.
 */
enum class CurveOrigin {
    MANUAL, UPLOAD, BOOTSTRAP
}
