package com.fairvalue.repository

import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.YieldCurveUpload
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/**
 * 조직 격리: 조회는 orgId 로 스코프. grade NULL(무위험)은 IS NULL 로 정확히 매칭.
 */
interface YieldCurveUploadRepository : JpaRepository<YieldCurveUpload, Long> {

    fun findByIdAndOrgId(id: Long, orgId: Long): YieldCurveUpload?

    /** 목록(필터: kind/grade/asOf 선택). null 파라미터는 해당 필터 미적용. */
    @Query(
        """
        SELECT u FROM YieldCurveUpload u
        WHERE u.orgId = :orgId
          AND (:kind IS NULL OR u.kind = :kind)
          AND (:asOf IS NULL OR u.asOf = :asOf)
          AND (:grade IS NULL OR u.grade = :grade)
        ORDER BY u.asOf DESC, u.kind ASC, u.version DESC
        """,
    )
    fun search(
        @Param("orgId") orgId: Long,
        @Param("kind") kind: CurveKind?,
        @Param("grade") grade: String?,
        @Param("asOf") asOf: LocalDate?,
    ): List<YieldCurveUpload>

    /** (org,kind,grade,as_of) 의 최대 version. grade NULL 은 IS NULL 로 정확 매칭. 없으면 0. */
    @Query(
        """
        SELECT COALESCE(MAX(u.version), 0) FROM YieldCurveUpload u
        WHERE u.orgId = :orgId AND u.kind = :kind AND u.asOf = :asOf
          AND ((:grade IS NULL AND u.grade IS NULL) OR u.grade = :grade)
        """,
    )
    fun maxVersion(
        @Param("orgId") orgId: Long,
        @Param("kind") kind: CurveKind,
        @Param("grade") grade: String?,
        @Param("asOf") asOf: LocalDate,
    ): Int

    /** 자동매핑: (org,as_of,kind,grade) 일치 중 version 내림차순. 첫 1건이 최신. */
    @Query(
        """
        SELECT u FROM YieldCurveUpload u
        WHERE u.orgId = :orgId AND u.kind = :kind AND u.asOf = :asOf
          AND ((:grade IS NULL AND u.grade IS NULL) OR u.grade = :grade)
        ORDER BY u.version DESC
        """,
    )
    fun findLatest(
        @Param("orgId") orgId: Long,
        @Param("kind") kind: CurveKind,
        @Param("grade") grade: String?,
        @Param("asOf") asOf: LocalDate,
        pageable: Pageable,
    ): List<YieldCurveUpload>
}
