package com.fairvalue.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * KOFIA/신용평가사 채권시가평가수익률 엑셀 파싱 결과(미리보기).
 *   - POST /curves/parse-kofia 응답. 저장은 별도(POST /curves).
 *   - ★ 재현성/추적성: filename·parsed_at·source(고시기관) 기록.
 */
data class KofiaPoint(
    @JsonProperty("tenor_years") val tenorYears: Double,
    @JsonProperty("rate_percent") val ratePercent: Double,
)

data class KofiaCurveCandidate(
    val index: Int,                                 // 선택용 인덱스
    @JsonProperty("bond_type") val bondType: String,    // 종류(국채·회사채 I 등)
    @JsonProperty("type_name") val typeName: String,    // 종류명
    val grade: String?,                             // 신용등급(국고채류 null)
    val source: String,                             // 고시기관(예: 나이스피앤아이)
    val kind: String,                               // RISK_FREE | CREDIT
    val points: List<KofiaPoint>,                   // '-'·빈칸 제외
)

data class KofiaParseResponse(
    val filename: String,
    @JsonProperty("parsed_at") val parsedAt: String,
    val candidates: List<KofiaCurveCandidate>,
)
