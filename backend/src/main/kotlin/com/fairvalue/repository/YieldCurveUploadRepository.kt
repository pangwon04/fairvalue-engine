package com.fairvalue.repository

import com.fairvalue.domain.CurveKind
import com.fairvalue.domain.YieldCurveUpload
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/**
 * 조직 격리: 조회는 orgId 로 스코프. grade NULL(무위험)은 IS NULL 로 정확히 매칭.
 *
 * 목록(GET /curves)은 JpaSpecificationExecutor 동적 쿼리로 처리한다(CurveService.list).
 *   - 과거 ':param IS NULL OR ...' @Query 는 PostgreSQL 이 null enum 파라미터의 타입을
 *     추론하지 못해 "could not determine data type of parameter" 로 거부했다.
 *   - Specification 은 null 필터를 WHERE 절에서 아예 제외하므로 untyped 파라미터가 없다.
 */
interface YieldCurveUploadRepository :
    JpaRepository<YieldCurveUpload, Long>,
    JpaSpecificationExecutor<YieldCurveUpload> {

    fun findByIdAndOrgId(id: Long, orgId: Long): YieldCurveUpload?

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
