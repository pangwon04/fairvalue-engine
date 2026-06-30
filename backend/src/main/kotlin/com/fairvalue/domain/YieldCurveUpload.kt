package com.fairvalue.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * yield_curve_uploads (V4) — 커브 헤더/메타. 조직 격리(org_id). 이력 보존(version).
 *   - kind: postgres enum curve_kind (@JdbcType PostgreSQLEnumJdbcType).
 *   - origin: varchar+CHECK (@Enumerated STRING) — postgres enum 아님.
 *   - as_of: DATE, created_at: TIMESTAMPTZ, 문자열은 전부 VARCHAR(CHAR 미사용).
 */
@Entity
@Table(name = "yield_curve_uploads")
class YieldCurveUpload(
    @Column(name = "org_id", nullable = false)
    var orgId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "kind", nullable = false, columnDefinition = "curve_kind")
    var kind: CurveKind,

    @Column(name = "as_of", nullable = false)
    var asOf: LocalDate,

    @Column(name = "version", nullable = false)
    var version: Int = 1,

    @Column(name = "grade", length = 20)
    var grade: String? = null,

    @Column(name = "source", length = 200)
    var source: String? = null,

    @Column(name = "interpolation_method", length = 30, nullable = false)
    var interpolationMethod: String = "linear",

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 20, nullable = false)
    var origin: CurveOrigin = CurveOrigin.UPLOAD,

    @Column(name = "uploaded_by")
    var uploadedBy: Long? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,
)
