package com.fairvalue.service

import com.fasterxml.jackson.databind.JsonNode
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

/**
 * 평가보고서 계산근거 raw 엑셀 (POI). 5-9 v2: ★numeric 셀+서식·열너비·헤더/라벨 스타일·틀고정·
 *   탭 색·Curves tenor 헤더·Summary 강조. 값·정밀도·시트 구성 불변(서식만).
 *   시트: Summary·Terms·Parameters·Curves·RiskNeutralProb·Tree_*(steps_used 전체)·Sensitivity·Reproducibility.
 */
@Component
class ReportExcelBuilder {

    private fun color(r: Int, g: Int, b: Int) = XSSFColor(byteArrayOf(r.toByte(), g.toByte(), b.toByte()), null)
    private val NAVY get() = color(9, 9, 70)
    private val LABEL get() = color(226, 232, 240)
    private val BASE get() = color(219, 227, 240)
    private val TAB_NAVY get() = color(9, 9, 70)
    private val TAB_GRAY get() = color(148, 163, 184)
    private val TAB_BLUE get() = color(191, 219, 254)

    private lateinit var wb: XSSFWorkbook
    // 스타일 캐시
    private lateinit var stHeader: CellStyle
    private lateinit var stLabel: CellStyle
    private lateinit var stText: CellStyle
    private lateinit var stAmount: CellStyle   // #,##0.0000
    private lateinit var stInt: CellStyle       // #,##0
    private lateinit var stRate: CellStyle      // 0.0000
    private lateinit var stProb: CellStyle      // 0.000000
    private lateinit var stBase: CellStyle      // 금액 + base 음영
    private lateinit var stTitle: CellStyle     // Summary 강조

    fun build(reportNo: String, instrumentName: String, instrumentType: String,
              result: JsonNode, context: JsonNode?): ByteArray {
        wb = XSSFWorkbook()
        initStyles()

        buildSummary(reportNo, instrumentName, instrumentType, result)
        context?.path("terms")?.takeIf { it.isObject }?.let { buildKvSheet("Terms", it, TAB_BLUE) }
        result.path("key_parameters").takeIf { it.isObject }?.let { buildKvSheet("Parameters", it, TAB_BLUE) }
        buildCurves(result.path("curve_bootstrap"))
        buildRiskNeutral(result.path("trees"))
        buildTrees(result.path("trees"))
        buildSensitivity(result.path("sensitivity"))
        buildReproducibility(result)

        val baos = ByteArrayOutputStream(); wb.write(baos); wb.close()
        return baos.toByteArray()
    }

    // ── 스타일 ──
    private fun style(fmt: String? = null, fill: XSSFColor? = null, white: Boolean = false,
                      bold: Boolean = false, align: HorizontalAlignment = HorizontalAlignment.LEFT, size: Int = 0): CellStyle {
        val s = wb.createCellStyle() as XSSFCellStyle
        fmt?.let { s.dataFormat = wb.createDataFormat().getFormat(it) }
        fill?.let { s.setFillForegroundColor(it); s.fillPattern = FillPatternType.SOLID_FOREGROUND }
        if (bold || white || size > 0) {
            val f = wb.createFont(); f.bold = bold || white
            if (white) f.color = IndexedColors.WHITE.index
            if (size > 0) f.fontHeightInPoints = size.toShort()
            s.setFont(f)
        }
        s.alignment = align
        s.borderTop = BorderStyle.THIN; s.borderBottom = BorderStyle.THIN
        s.borderLeft = BorderStyle.THIN; s.borderRight = BorderStyle.THIN
        return s
    }

    private fun initStyles() {
        stHeader = style(fill = NAVY, white = true, bold = true, align = HorizontalAlignment.CENTER)
        stLabel = style(fill = LABEL, bold = true, align = HorizontalAlignment.LEFT)
        stText = style(align = HorizontalAlignment.LEFT)
        stAmount = style("#,##0.0000", align = HorizontalAlignment.RIGHT)
        stInt = style("#,##0", align = HorizontalAlignment.RIGHT)
        stRate = style("0.0000", align = HorizontalAlignment.RIGHT)
        stProb = style("0.000000", align = HorizontalAlignment.RIGHT)
        stBase = style("#,##0.0000", fill = BASE, bold = true, align = HorizontalAlignment.RIGHT)
        stTitle = style(bold = true, size = 13)
    }

    private fun tab(sheet: Sheet, c: XSSFColor) { (sheet as XSSFSheet).setTabColor(c) }
    private fun widths(sheet: Sheet, vararg chars: Int) { chars.forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) } }

    private fun cellS(sheet: Sheet, r: Int, c: Int, text: String, st: CellStyle) {
        val row = sheet.getRow(r) ?: sheet.createRow(r); row.createCell(c).apply { setCellValue(text); cellStyle = st }
    }
    private fun cellN(sheet: Sheet, r: Int, c: Int, v: Double, st: CellStyle) {
        val row = sheet.getRow(r) ?: sheet.createRow(r); row.createCell(c).apply { setCellValue(v); cellStyle = st }
    }
    /** JSON 값 → 숫자면 numeric+서식, 아니면 텍스트. */
    private fun cellV(sheet: Sheet, r: Int, c: Int, v: JsonNode, numStyle: CellStyle) {
        if (v.isNumber) cellN(sheet, r, c, v.asDouble(), numStyle) else cellS(sheet, r, c, v.asText(""), stText)
    }

    // ── Summary ──
    private fun buildSummary(reportNo: String, name: String, type: String, result: JsonNode) {
        val s = wb.createSheet("Summary"); tab(s, TAB_NAVY); widths(s, 22, 24)
        s.createFreezePane(0, 1)
        cellS(s, 0, 0, "평가보고서", stHeader); cellS(s, 0, 1, reportNo, stHeader)
        var r = 1
        cellS(s, r, 0, "상품명", stLabel); cellS(s, r++, 1, name, stTitle)
        cellS(s, r, 0, "상품유형", stLabel); cellS(s, r++, 1, type, stText)
        cellS(s, r, 0, "평가기준일", stLabel); cellS(s, r++, 1, result.path("valuation_date").asText(""), stText)
        cellS(s, r, 0, "모형", stLabel); cellS(s, r++, 1, result.path("key_parameters").path("model_name").asText(""), stText)
        cellS(s, r, 0, "공정가치(총액)", stLabel); cellV(s, r++, 1, result.path("total_fair_value"), stAmount)
        cellS(s, r, 0, "단위당(1좌)", stLabel); cellV(s, r++, 1, result.path("per_unit_value"), stAmount)
        r++
        cellS(s, r++, 0, "구성요소(12키)", stHeader)
        val comp = result.path("components")
        for (k in COMP_KEYS) {
            val v = comp.path(k)
            if (!v.isNull && !v.isMissingNode) { cellS(s, r, 0, k, stLabel); cellV(s, r++, 1, v, stAmount) }
        }
    }

    // ── 2열 라벨/값(Terms·Parameters) ──
    private fun buildKvSheet(name: String, node: JsonNode, tabColor: XSSFColor) {
        val s = wb.createSheet(name); tab(s, tabColor); widths(s, 22, 20); s.createFreezePane(0, 1)
        cellS(s, 0, 0, "항목", stHeader); cellS(s, 0, 1, "값", stHeader)
        var r = 1
        node.fields().forEach { (k, v) ->
            cellS(s, r, 0, k, stLabel)
            val st = if (k == "u" || k == "d") stProb else if (k.contains("steps") || k.contains("amount") || k.contains("face")) stInt else stAmount
            cellV(s, r++, 1, v, st)
        }
    }

    // ── Curves (tenor 헤더 행 추가, →X1) ──
    private fun buildCurves(cb: JsonNode) {
        if (!cb.isObject) return
        val s = wb.createSheet("Curves"); tab(s, TAB_BLUE); s.createFreezePane(0, 1)
        widths(s, 12, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10)
        var r = 0
        for ((leg, legName) in listOf("rf" to "무위험(rf)", "rd" to "위험(rd)")) {
            val tbl = cb.path(leg); if (!tbl.isObject) continue
            cellS(s, r++, 0, "$legName 이자율(%)", stHeader)
            val spot = tbl.path("spot")
            // ★tenor 헤더 행
            cellS(s, r, 0, "만기(y)", stLabel)
            for (i in 0 until spot.size()) cellN(s, r, i + 1, spot.get(i).get(0).asDouble(), stRate)
            r++
            for ((lb, key) in listOf("YTM" to "ytm", "SPOT" to "spot", "FORWARD" to "forward")) {
                val arr = tbl.path(key)
                cellS(s, r, 0, lb, stLabel)
                for (i in 0 until arr.size()) cellN(s, r, i + 1, arr.get(i).get(1).asDouble(), stRate)
                r++
            }
            r++
        }
    }

    // ── RiskNeutralProb ──
    private fun buildRiskNeutral(trees: JsonNode) {
        val prob = trees.path("risk_neutral_prob"); if (!prob.isArray) return
        val s = wb.createSheet("RiskNeutralProb"); tab(s, TAB_BLUE); widths(s, 12, 12, 12); s.createFreezePane(1, 1)
        cellS(s, 0, 0, "step", stHeader); cellS(s, 0, 1, "p", stHeader); cellS(s, 0, 2, "1-p", stHeader)
        var r = 1
        prob.forEach { x ->
            cellN(s, r, 0, x.path("step").asDouble(), stInt)
            cellN(s, r, 1, x.path("p").asDouble(), stProb)
            cellN(s, r, 2, x.path("q").asDouble(), stProb)
            r++
        }
    }

    // ── Trees(전체 steps) ──
    private fun buildTrees(trees: JsonNode) {
        if (!trees.isObject) return
        matrixSheet("Tree_Underlying", trees.path("underlying_tree"), false)
        matrixSheet("Tree_Composite", trees.path("composite_tree"), false)
        matrixSheet("Tree_Equity", trees.path("equity_tree"), false)
        matrixSheet("Tree_Debt", trees.path("debt_tree"), false)
        if (trees.path("conversion_prob_tree").isArray) matrixSheet("Tree_ConvProb", trees.path("conversion_prob_tree"), true)
    }

    private fun matrixSheet(name: String, tree: JsonNode, prob: Boolean) {
        if (!tree.isArray || tree.size() == 0) return
        val s = wb.createSheet(name); tab(s, TAB_GRAY); s.createFreezePane(1, 1)
        val n = tree.size()
        s.setColumnWidth(0, 10 * 256)
        for (j in 0 until n) s.setColumnWidth(j + 1, 13 * 256)
        cellS(s, 0, 0, "t\\j", stHeader)
        for (j in 0 until n) cellN(s, 0, j + 1, j.toDouble(), stHeader)
        val numStyle = if (prob) stProb else stAmount
        for (st in 0 until n) {
            cellN(s, st + 1, 0, st.toDouble(), stLabel)
            val tr = tree.get(st)
            for (j in 0 until tr.size()) {
                val v = tr.get(j)
                if (v != null && !v.isNull) cellN(s, st + 1, j + 1, v.asDouble(), numStyle)
            }
        }
    }

    // ── Sensitivity ──
    private fun buildSensitivity(sens: JsonNode) {
        if (!sens.isObject) return
        val s = wb.createSheet("Sensitivity"); tab(s, TAB_BLUE); s.createFreezePane(1, 1)
        val spot = sens.path("spot_axis"); val vol = sens.path("vol_axis"); val grid = sens.path("total_grid")
        widths(s, 14, 14, 14, 14, 14)
        cellS(s, 0, 0, "vol＼spot", stHeader)
        for (i in 0 until spot.size()) cellN(s, 0, i + 1, spot.get(i).asDouble(), stHeader)
        for (ri in 0 until grid.size()) {
            cellN(s, ri + 1, 0, vol.get(ri).asDouble(), stRate)
            for (ci in 0 until grid.get(ri).size()) {
                val base = ri == 1 && ci == 1
                cellN(s, ri + 1, ci + 1, grid.get(ri).get(ci).asDouble(), if (base) stBase else stAmount)
            }
        }
    }

    // ── Reproducibility ──
    private fun buildReproducibility(result: JsonNode) {
        val s = wb.createSheet("Reproducibility"); tab(s, TAB_BLUE); widths(s, 18, 74); s.createFreezePane(0, 1)
        cellS(s, 0, 0, "항목", stHeader); cellS(s, 0, 1, "값", stHeader)
        val repro = result.path("reproducibility"); val meta = result.path("trees").path("tree_meta")
        var r = 1
        listOf(
            "input_hash" to repro.path("input_hash").asText(""),
            "seed" to repro.path("seed").asText(""),
            "model_version" to repro.path("model_version").asText(""),
            "rate_mode" to meta.path("rate_mode").asText(""),
            "steps_used" to meta.path("steps_used").asText(""),
        ).forEach { (k, v) -> cellS(s, r, 0, k, stLabel); cellS(s, r++, 1, v, stText) }
    }

    companion object {
        val COMP_KEYS = listOf(
            "bond_value", "preferred_share_value", "conversion_option_value", "exchange_option_value",
            "warrant_value", "redemption_option_value", "issuer_call_value", "sale_claim_value",
            "stock_option_value", "conditional_option_value", "dilution_effect", "total_fair_value",
        )
    }
}
