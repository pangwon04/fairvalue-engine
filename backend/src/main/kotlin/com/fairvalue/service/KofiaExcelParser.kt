package com.fairvalue.service

import com.fairvalue.dto.KofiaCurveCandidate
import com.fairvalue.dto.KofiaParseResponse
import com.fairvalue.dto.KofiaPoint
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime

/**
 * KOFIA 채권시가평가수익률 엑셀(.xls HSSF / .xlsx XSSF) 파서.
 *   - 0행 헤더: 종류·종류명·신용등급·고시기관 + 만기 컬럼(3월…50년).
 *   - 1행부터 각 행 = 커브 후보. 만기 라벨 → tenor_years 매핑(라벨 기반, 컬럼 이동에 강건).
 *   - ★ 값이 '-'·빈칸이면 해당 tenor '제외'(0으로 읽지 않음 — 커브 오염 방지).
 *   - kind: 국채·통안증권 → RISK_FREE, 그 외 → CREDIT.
 *   - grade: 신용등급 컬럼(국고채류는 '-'/빈칸 → null).
 */
@Component
class KofiaExcelParser {

    companion object {
        // 만기 라벨 → tenor_years. 공백 제거 후 매칭.
        val TENOR: Map<String, Double> = mapOf(
            "3월" to 0.25, "6월" to 0.5, "9월" to 0.75,
            "1년" to 1.0, "1년6월" to 1.5, "2년" to 2.0, "2년6월" to 2.5,
            "3년" to 3.0, "4년" to 4.0, "5년" to 5.0, "7년" to 7.0,
            "10년" to 10.0, "15년" to 15.0, "20년" to 20.0, "30년" to 30.0, "50년" to 50.0,
        )
        val RISK_FREE_TYPES: Set<String> = setOf("국채", "통안증권")
    }

    fun parse(bytes: ByteArray, filename: String): KofiaParseResponse {
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
                ?: throw IllegalArgumentException("엑셀에 시트가 없습니다.")
            val header = sheet.getRow(0)
                ?: throw IllegalArgumentException("헤더(0행)가 없습니다.")

            val labelCol = HashMap<String, Int>()
            val tenorCols = ArrayList<Pair<Int, Double>>()
            for (c in 0 until header.lastCellNum.toInt()) {
                val label = cellText(header.getCell(c)).replace(" ", "").trim()
                if (label.isEmpty()) continue
                labelCol[label] = c
                TENOR[label]?.let { tenorCols.add(c to it) }
            }

            val colType = labelCol["종류"]
                ?: throw IllegalArgumentException("'종류' 컬럼을 찾지 못했습니다. KOFIA 형식이 아닙니다.")
            val colTypeName = labelCol["종류명"] ?: colType
            val colGrade = labelCol["신용등급"]
            val colSource = labelCol["고시기관"]
            if (tenorCols.isEmpty()) {
                throw IllegalArgumentException("만기 컬럼(3월…50년)을 찾지 못했습니다. KOFIA 형식이 아닙니다.")
            }

            val candidates = ArrayList<KofiaCurveCandidate>()
            var idx = 0
            for (r in 1..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val bondType = cellText(row.getCell(colType)).trim()
                if (bondType.isEmpty()) continue                       // 빈 행 skip

                val typeName = cellText(row.getCell(colTypeName)).trim()
                val gradeRaw = colGrade?.let { cellText(row.getCell(it)).trim() } ?: ""
                val grade = if (gradeRaw.isEmpty() || gradeRaw == "-") null else gradeRaw
                val source = colSource?.let { cellText(row.getCell(it)).trim() } ?: ""
                val kind = if (bondType in RISK_FREE_TYPES) "RISK_FREE" else "CREDIT"

                val points = ArrayList<KofiaPoint>()
                for ((c, tenor) in tenorCols) {
                    val rate = readRate(row.getCell(c)) ?: continue     // ★ '-'·빈칸 → 제외
                    points.add(KofiaPoint(tenor, rate))
                }
                if (points.isEmpty()) continue

                candidates.add(KofiaCurveCandidate(idx++, bondType, typeName, grade, source, kind, points))
            }
            return KofiaParseResponse(filename, OffsetDateTime.now().toString(), candidates)
        }
    }

    /** 셀 → 문자열(숫자·문자·수식 대응). */
    private fun cellText(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                val d = cell.numericCellValue
                if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> runCatching { cell.stringCellValue }
                .getOrElse { runCatching { cell.numericCellValue.toString() }.getOrDefault("") }
            else -> ""
        }
    }

    /** 셀 → 수익률(Double). '-'·빈칸·파싱불가 → null(제외). */
    private fun readRate(cell: Cell?): Double? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> {
                val s = cell.stringCellValue.trim()
                if (s.isEmpty() || s == "-") null else s.replace(",", "").toDoubleOrNull()
            }
            CellType.BLANK -> null
            CellType.FORMULA -> runCatching { cell.numericCellValue }.getOrNull()
            else -> null
        }
    }
}
