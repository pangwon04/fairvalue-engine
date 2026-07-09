package com.fairvalue.report

import com.fairvalue.service.ReportExcelBuilder
import com.fairvalue.service.ReportPdfBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.lowagie.text.pdf.PdfReader
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * ★보고서 생성기 단위테스트(Spring 무의존): 합성 result(트리·이자율·민감도 포함) → PDF/엑셀 바이트 생성.
 *   PDF 는 %PDF, XLSX 는 PK(zip) 매직바이트 확인 + 한글 폰트 경로가 예외 없이 동작하는지.
 */
class ReportBuilderTest {

    private val mapper = ObjectMapper()

    private val RESULT = """
    {
      "job_id": 1, "instrument_id": 10, "instrument_type": "CB", "valuation_date": "2024-06-26",
      "status": "DONE", "total_fair_value": 10000.0, "per_unit_value": 10000.0,
      "components": {"bond_value": 9000.0, "conversion_option_value": 1000.0, "total_fair_value": 10000.0},
      "key_parameters": {"model_name": "TF_LATTICE", "volatility": 45.0, "risk_free_rate": 3.3, "discount_rate": 15.0, "u": 1.25, "d": 0.8, "lattice_steps": 1000},
      "reproducibility": {"input_hash": "abc123", "seed": 20240101, "model_version": "cb-1.0.0"},
      "trees": {
        "underlying_tree": [[3260.0, null, null], [2600.0, 4080.0, null], [2080.0, 3260.0, 5110.0]],
        "equity_tree": [[0.0, null, null], [0.0, 0.0, null], [0.0, 0.0, 5110.0]],
        "debt_tree": [[10000.0, null, null], [10000.0, 10000.0, null], [10000.0, 10000.0, 0.0]],
        "composite_tree": [[10000.0, null, null], [10000.0, 10000.0, null], [10000.0, 10000.0, 5110.0]],
        "risk_neutral_prob": [{"step": 0, "p": 0.46, "q": 0.54}, {"step": 1, "p": 0.46, "q": 0.54}],
        "tree_meta": {"steps_used": 2, "dt": 0.25, "u": 1.25, "d": 0.8, "display_nodes": 11, "rate_mode": "BOOTSTRAPPED_FORWARD", "model": "TF"}
      },
      "sensitivity": {"vol_axis": [0.40, 0.45, 0.50], "spot_axis": [3097.0, 3260.0, 3423.0],
        "total_grid": [[9500.0, 9600.0, 9700.0], [9800.0, 10000.0, 10200.0], [10100.0, 10300.0, 10500.0]],
        "per_unit_grid": [[9500.0, 9600.0, 9700.0], [9800.0, 10000.0, 10200.0], [10100.0, 10300.0, 10500.0]],
        "meta": {"steps_used": 27, "model": "TF_LATTICE", "vol_bump": 0.05, "spot_bump": 0.05, "vol_floor_applied": false}},
      "curve_bootstrap": {"rate_mode": "BOOTSTRAPPED_FORWARD",
        "rf": {"ytm": [[0.5, 3.4], [1.0, 3.3]], "spot": [[0.5, 3.4], [1.0, 3.3]], "forward": [[0.5, 3.4], [1.0, 3.2]], "grid": 0.5, "assumption": "연속 zero"},
        "rd": {"ytm": [[0.5, 13.4], [1.0, 14.5]], "spot": [[0.5, 13.4], [1.0, 14.5]], "forward": [[0.5, 13.4], [1.0, 15.9]], "grid": 0.5, "assumption": "연속 zero"}}
    }
    """.trimIndent()

    private val CONTEXT = """
    {"instrument_type": "CB", "valuation_date": "2024-06-26", "model": "TF_LATTICE",
     "terms": {"issue_date": "2023-09-13", "maturity_date": "2026-09-13", "face_value": 10000, "coupon_rate": 2}}
    """.trimIndent()

    @Test
    fun `PDF 생성 — %PDF 매직바이트·한글 폰트 무예외`() {
        val result = mapper.readTree(RESULT)
        val ctx = mapper.readTree(CONTEXT)
        val pdf = ReportPdfBuilder().build("FVE-2026-000001", "예시바이오 3CB", "CB", "예시바이오", result, ctx, "2026-07-03T00:00:00Z")
        assertTrue(pdf.size > 1000, "PDF 바이트 존재: ${pdf.size}")
        assertTrue(pdf[0] == '%'.code.toByte() && pdf[1] == 'P'.code.toByte() && pdf[2] == 'D'.code.toByte() && pdf[3] == 'F'.code.toByte(), "%PDF 매직바이트")
        // ★5-9v2: 결과요약 직후 강제 개행 → 최소 2페이지
        val reader = PdfReader(pdf)
        assertTrue(reader.numberOfPages >= 2, "페이지수 ≥ 2(결과요약 개행): ${reader.numberOfPages}")
        reader.close()
    }

    @Test
    fun `GS 방법론 문구 반영 — 예외 없이 생성`() {
        val node = mapper.readTree(RESULT) as com.fasterxml.jackson.databind.node.ObjectNode
        (node.get("key_parameters") as com.fasterxml.jackson.databind.node.ObjectNode).put("model_name", "GS")
        (node.get("trees").get("tree_meta") as com.fasterxml.jackson.databind.node.ObjectNode).put("model", "GS")
        val pdf = ReportPdfBuilder().build("FVE-2026-000002", "GS 상품", "RCPS", "발행사", node, null, "2026-07-03T00:00:00Z")
        assertTrue(pdf.size > 1000)
    }

    @Test
    fun `엑셀 생성 — PK(zip) 매직바이트·시트 존재`() {
        val result = mapper.readTree(RESULT)
        val ctx = mapper.readTree(CONTEXT)
        val xlsx = ReportExcelBuilder().build("FVE-2026-000001", "예시바이오 3CB", "CB", result, ctx)
        assertTrue(xlsx.size > 1000, "XLSX 바이트 존재")
        assertTrue(xlsx[0] == 'P'.code.toByte() && xlsx[1] == 'K'.code.toByte(), "PK(zip) 매직바이트")
    }

    @Test
    fun `엑셀 서식 — numeric 셀·열너비·틀고정·탭색`() {
        val result = mapper.readTree(RESULT)
        val xlsx = ReportExcelBuilder().build("FVE-2026-000001", "예시바이오 3CB", "CB", result, mapper.readTree(CONTEXT))
        XSSFWorkbook(ByteArrayInputStream(xlsx)).use { wb ->
            // 대표 숫자 셀 NUMERIC(문자열 저장 아님)
            val sens = wb.getSheet("Sensitivity")
            assertEquals(CellType.NUMERIC, sens.getRow(1).getCell(1).cellType, "민감도 값 numeric")
            val summary = wb.getSheet("Summary")
            // 열너비 명시(기본 8.43자≈2340 초과)
            assertTrue(summary.getColumnWidth(0) > 3000, "Summary A열 열너비 설정")
            // 틀고정
            assertNotNull(summary.paneInformation, "Summary freezePane")
            assertNotNull(wb.getSheet("Tree_Underlying").paneInformation, "Tree freezePane(B2)")
            // 트리 값 numeric + 탭 색
            val tree = wb.getSheet("Tree_Underlying")
            assertEquals(CellType.NUMERIC, tree.getRow(1).getCell(1).cellType, "트리 값 numeric")
            assertNotNull(tree.tabColor, "Tree 탭 색")
            assertNotNull(summary.tabColor, "Summary 탭 색")
            // Curves tenor 헤더 행 존재(만기(y))
            val curves = wb.getSheet("Curves")
            assertNotNull(curves, "Curves 시트")
        }
    }
}
