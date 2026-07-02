package com.fairvalue.service

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/**
 * KOFIA 파서 정확성 = BLOCKING(커브가 조용히 틀리면 그 커브의 모든 평가가 틀림).
 *   (1) 만기 매핑(1년6월=1.5, 2년6월=2.5 등)
 *   (2) '-'·빈칸은 0이 아니라 '제외'(silent 오염 방지)
 *   (3) kind 판정(국채·통안증권=RISK_FREE, 그 외=CREDIT)
 *   (4) grade 추출(국고채류 null)
 */
class KofiaExcelParserTest {

    private val parser = KofiaExcelParser()

    // 헤더 컬럼 인덱스: 0종류 1종류명 2신용등급 3고시기관
    //   4:3월 5:6월 6:9월 7:1년 8:1년6월 9:2년 10:2년6월 11:3년 12:4년 13:5년 14:7년 15:10년 …
    private fun buildXls(): ByteArray {
        val wb = HSSFWorkbook()
        val sh = wb.createSheet("sheet")
        val header = listOf(
            "종류", "종류명", "신용등급", "고시기관",
            "3월", "6월", "9월", "1년", "1년6월", "2년", "2년6월", "3년",
            "4년", "5년", "7년", "10년", "15년", "20년", "30년", "50년",
        )
        val h = sh.createRow(0)
        header.forEachIndexed { i, s -> h.createCell(i).setCellValue(s) }

        // 국고채: 등급 없음('-'), 2년6월은 '-'(제외 대상)
        val r1 = sh.createRow(1)
        r1.createCell(0).setCellValue("국채")
        r1.createCell(1).setCellValue("국고채권")
        r1.createCell(2).setCellValue("-")
        r1.createCell(3).setCellValue("나이스피앤아이")
        r1.createCell(4).setCellValue(3.50)   // 3월  = 0.25
        r1.createCell(8).setCellValue(3.20)   // 1년6월 = 1.5
        r1.createCell(10).setCellValue("-")   // 2년6월 = 2.5 → 제외
        r1.createCell(15).setCellValue(3.30)  // 10년 = 10.0

        // 회사채 I: 등급 AA-
        val r2 = sh.createRow(2)
        r2.createCell(0).setCellValue("회사채 I")
        r2.createCell(1).setCellValue("공모사채")
        r2.createCell(2).setCellValue("AA-")
        r2.createCell(3).setCellValue("나이스피앤아이")
        r2.createCell(4).setCellValue(4.20)   // 3월
        r2.createCell(9).setCellValue(4.80)   // 2년 = 2.0
        r2.createCell(10).setCellValue(4.90)  // 2년6월 = 2.5

        val bos = ByteArrayOutputStream()
        wb.write(bos); wb.close()
        return bos.toByteArray()
    }

    @Test
    fun `만기매핑·kind·grade·빈값제외 정확성`() {
        val res = parser.parse(buildXls(), "test.xls")
        assertEquals(2, res.candidates.size)

        val gov = res.candidates.first { it.bondType == "국채" }
        // (3) kind
        assertEquals("RISK_FREE", gov.kind)
        // (4) grade null(국고채류)
        assertNull(gov.grade)
        // (1) 만기 매핑
        assertTrue(gov.points.any { it.tenorYears == 0.25 && it.ratePercent == 3.50 })
        assertTrue(gov.points.any { it.tenorYears == 1.5 && it.ratePercent == 3.20 }, "1년6월=1.5 매핑")
        assertTrue(gov.points.any { it.tenorYears == 10.0 && it.ratePercent == 3.30 })
        // (2) '-' 제외 — 2년6월(2.5)이 0으로 들어오지 않고 아예 없어야 함
        assertFalse(gov.points.any { it.tenorYears == 2.5 }, "'-'는 제외(0 오염 아님)")
        assertEquals(3, gov.points.size)

        val corp = res.candidates.first { it.bondType == "회사채 I" }
        assertEquals("CREDIT", corp.kind)          // (3)
        assertEquals("AA-", corp.grade)            // (4)
        assertEquals("나이스피앤아이", corp.source)
        assertTrue(corp.points.any { it.tenorYears == 2.0 && it.ratePercent == 4.80 })
        assertTrue(corp.points.any { it.tenorYears == 2.5 && it.ratePercent == 4.90 }, "2년6월=2.5 매핑")
    }
}
