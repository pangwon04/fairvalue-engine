package com.fairvalue.service

import com.fasterxml.jackson.databind.JsonNode
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

/**
 * 평가보고서 계산근거 raw 엑셀 (POI). 시트: Summary·Terms·Parameters·Curves·RiskNeutralProb·
 *   Tree_*(★steps_used 전체)·Sensitivity·Reproducibility.
 */
@Component
class ReportExcelBuilder {

    fun build(reportNo: String, instrumentName: String, instrumentType: String,
              result: JsonNode, context: JsonNode?): ByteArray {
        val wb: Workbook = XSSFWorkbook()

        // Summary
        wb.createSheet("Summary").let { s ->
            var r = 0
            put(s, r++, "발급번호", reportNo)
            put(s, r++, "상품명", instrumentName)
            put(s, r++, "상품유형", instrumentType)
            put(s, r++, "평가기준일", result.path("valuation_date").asText(""))
            put(s, r++, "모형", result.path("key_parameters").path("model_name").asText(""))
            put(s, r++, "공정가치(총액)", result.path("total_fair_value").asText(""))
            put(s, r++, "단위당(1좌)", result.path("per_unit_value").asText(""))
            r++
            s.createRow(r++).createCell(0).setCellValue("구성요소(12키)")
            val comp = result.path("components")
            for (k in COMP_KEYS) {
                val v = comp.path(k)
                if (!v.isNull && !v.isMissingNode) put(s, r++, k, v.asText())
            }
        }

        // Terms
        context?.path("terms")?.let { terms ->
            val s = wb.createSheet("Terms"); var r = 0
            terms.fields().forEach { (k, v) -> put(s, r++, k, v.asText("")) }
        }

        // Parameters
        wb.createSheet("Parameters").let { s ->
            var r = 0
            result.path("key_parameters").fields().forEach { (k, v) -> put(s, r++, k, v.asText("")) }
        }

        // Curves (rf/rd × YTM/SPOT/FORWARD)
        val cb = result.path("curve_bootstrap")
        if (cb.isObject) {
            val s = wb.createSheet("Curves"); var r = 0
            for (leg in listOf("rf", "rd")) {
                val tbl = cb.path(leg)
                if (!tbl.isObject) continue
                s.createRow(r++).createCell(0).setCellValue(leg.uppercase())
                for (rowName in listOf("ytm", "spot", "forward")) {
                    val arr = tbl.path(rowName)
                    val row = s.createRow(r++); row.createCell(0).setCellValue(rowName.uppercase())
                    for (i in 0 until arr.size()) row.createCell(i + 1).setCellValue(arr.get(i).get(1).asDouble())
                }
                r++
            }
        }

        // RiskNeutralProb
        val trees = result.path("trees")
        if (trees.path("risk_neutral_prob").isArray) {
            val s = wb.createSheet("RiskNeutralProb"); var r = 0
            val head = s.createRow(r++); head.createCell(0).setCellValue("step"); head.createCell(1).setCellValue("p"); head.createCell(2).setCellValue("1-p")
            trees.path("risk_neutral_prob").forEach { x ->
                val row = s.createRow(r++)
                row.createCell(0).setCellValue(x.path("step").asDouble())
                row.createCell(1).setCellValue(x.path("p").asDouble())
                row.createCell(2).setCellValue(x.path("q").asDouble())
            }
        }

        // Trees — ★전체(steps_used 전부)
        if (trees.isObject) {
            matrixSheet(wb, "Tree_Underlying", trees.path("underlying_tree"))
            matrixSheet(wb, "Tree_Composite", trees.path("composite_tree"))
            matrixSheet(wb, "Tree_Equity", trees.path("equity_tree"))
            matrixSheet(wb, "Tree_Debt", trees.path("debt_tree"))
            if (trees.path("conversion_prob_tree").isArray) matrixSheet(wb, "Tree_ConvProb", trees.path("conversion_prob_tree"))
        }

        // Sensitivity
        val sens = result.path("sensitivity")
        if (sens.isObject) {
            val s = wb.createSheet("Sensitivity"); var r = 0
            val spot = sens.path("spot_axis"); val vol = sens.path("vol_axis"); val grid = sens.path("total_grid")
            val head = s.createRow(r++); head.createCell(0).setCellValue("vol\\spot")
            for (i in 0 until spot.size()) head.createCell(i + 1).setCellValue(spot.get(i).asDouble())
            for (ri in 0 until grid.size()) {
                val row = s.createRow(r++); row.createCell(0).setCellValue(vol.get(ri).asDouble())
                for (ci in 0 until grid.get(ri).size()) row.createCell(ci + 1).setCellValue(grid.get(ri).get(ci).asDouble())
            }
        }

        // Reproducibility
        wb.createSheet("Reproducibility").let { s ->
            var r = 0
            val repro = result.path("reproducibility")
            put(s, r++, "input_hash", repro.path("input_hash").asText(""))
            put(s, r++, "seed", repro.path("seed").asText(""))
            put(s, r++, "model_version", repro.path("model_version").asText(""))
            put(s, r++, "rate_mode", trees.path("tree_meta").path("rate_mode").asText(""))
            put(s, r++, "steps_used", trees.path("tree_meta").path("steps_used").asText(""))
        }

        val baos = ByteArrayOutputStream()
        wb.write(baos); wb.close()
        return baos.toByteArray()
    }

    private fun put(s: Sheet, r: Int, k: String, v: String) {
        val row = s.createRow(r); row.createCell(0).setCellValue(k); row.createCell(1).setCellValue(v)
    }

    /** matrix JsonNode([step][j], 상삼각 외 null) → 시트. 전체 steps 기록. */
    private fun matrixSheet(wb: Workbook, name: String, tree: JsonNode) {
        if (!tree.isArray || tree.size() == 0) return
        val s = wb.createSheet(name)
        val n = tree.size()
        val head = s.createRow(0); head.createCell(0).setCellValue("step\\node")
        for (j in 0 until n) head.createCell(j + 1).setCellValue(j.toDouble())
        for (st in 0 until n) {
            val row = s.createRow(st + 1); row.createCell(0).setCellValue(st.toDouble())
            val tr = tree.get(st)
            for (j in 0 until tr.size()) {
                val v = tr.get(j)
                if (v != null && !v.isNull) row.createCell(j + 1).setCellValue(v.asDouble())
            }
        }
    }

    companion object {
        val COMP_KEYS = listOf(
            "bond_value", "preferred_share_value", "conversion_option_value", "exchange_option_value",
            "warrant_value", "redemption_option_value", "issuer_call_value", "sale_claim_value",
            "stock_option_value", "conditional_option_value", "dilution_effect", "total_fair_value",
        )
    }
}
