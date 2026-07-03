package com.fairvalue.service

import com.fasterxml.jackson.databind.JsonNode
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream

/**
 * 평가보고서 PDF (OpenPDF). 실보고서 표준 구성. 브랜드=FairValue Engine·네이비(#090946).
 *   한글: /fonts/NotoSansKR-Regular.ttf 임베딩(있으면) → 없으면 OpenPDF 내장 한글 CJK(HYGoThic).
 *   가격트리는 ★11노드 축약(+"…")로 표기(실보고서 방식). 전체 트리는 엑셀에서.
 */
@Component
class ReportPdfBuilder {

    private val NAVY = Color(9, 9, 70)
    private val SLATE = Color(71, 85, 105)
    private val LIGHT = Color(241, 245, 249)
    private val N = 11   // 축약 노드 수

    private fun koreanBase(): BaseFont =
        runCatching {
            javaClass.getResourceAsStream("/fonts/NotoSansKR-Regular.ttf")?.use {
                BaseFont.createFont("NotoSansKR-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, it.readBytes(), null)
            }
        }.getOrNull()
            ?: BaseFont.createFont("HYGoThic-Medium", "UniKS-UCS2-H", BaseFont.NOT_EMBEDDED)

    fun build(reportNo: String, instrumentName: String, instrumentType: String, issuer: String?,
              result: JsonNode, context: JsonNode?, issuedAt: String): ByteArray {
        val bf = koreanBase()
        val fTitle = Font(bf, 22f, Font.BOLD, NAVY)
        val fH = Font(bf, 13f, Font.BOLD, NAVY)
        val fBody = Font(bf, 9.5f, Font.NORMAL, Color.BLACK)
        val fSmall = Font(bf, 8f, Font.NORMAL, SLATE)

        val doc = Document(PageSize.A4, 48f, 48f, 54f, 54f)
        val baos = ByteArrayOutputStream()
        PdfWriter.getInstance(doc, baos)
        doc.open()

        val valDate = result.path("valuation_date").asText("—")
        val model = result.path("key_parameters").path("model_name").asText("—")

        // ── 표지 ──
        doc.add(Paragraph("FairValue Engine", Font(bf, 11f, Font.BOLD, NAVY)))
        doc.add(space(24f))
        doc.add(Paragraph("평가보고서", fTitle))
        doc.add(space(8f))
        doc.add(Paragraph(instrumentName, Font(bf, 14f, Font.BOLD, Color.BLACK)))
        doc.add(space(16f))
        doc.add(kv(bf, listOf(
            "상품유형" to instrumentType, "발행사" to (issuer ?: "—"),
            "평가기준일" to valDate, "적용모형" to modelLabel(model), "발급번호" to reportNo,
        )))
        doc.add(space(18f))

        // ── 유의사항(면책) ──
        h(doc, fH, "평가 개요 및 유의사항")
        doc.add(Paragraph(DISCLAIMER, fSmall))
        doc.add(space(12f))

        // ── 평가결과 요약 ──
        h(doc, fH, "평가결과 요약")
        val total = result.path("total_fair_value").asDouble()
        val perUnit = result.path("per_unit_value").asDouble()
        doc.add(kv(bf, listOf("공정가치(총액)" to n(total), "단위당(1좌)" to n(perUnit))))
        doc.add(space(4f))
        doc.add(componentsTable(bf, result.path("components")))
        doc.add(space(12f))

        // ── 주요 약정사항 ──
        h(doc, fH, "주요 약정사항")
        val terms = context?.path("terms")
        doc.add(kv(bf, listOf(
            "발행일" to js(terms, "issue_date"), "만기일" to js(terms, "maturity_date"),
            "액면금액" to js(terms, "face_value"), "발행금액" to js(terms, "issue_amount"),
            "표면이자율(%)" to js(terms, "coupon_rate"), "보장수익률(%)" to js(terms, "guaranteed_yield"),
        )))
        doc.add(space(12f))

        // ── 평가방법론 ──
        h(doc, fH, "평가방법론")
        doc.add(Paragraph(if (model == "GS") METHOD_GS else METHOD_TF, fBody))
        doc.add(space(12f))

        // ── 주요 가정·파라미터 ──
        h(doc, fH, "주요 가정 및 파라미터")
        doc.add(paramsTable(bf, result.path("key_parameters")))
        doc.add(space(12f))

        // ── Appendix 1: 이자율 산정 ──
        val cb = result.path("curve_bootstrap")
        if (cb.isObject) {
            h(doc, fH, "Appendix 1. 이자율 산정 (YTM · SPOT · FORWARD)")
            rateTable(doc, bf, "무위험(rf)", cb.path("rf"))
            rateTable(doc, bf, "위험(rd)", cb.path("rd"))
            doc.add(space(10f))
        }

        val trees = result.path("trees")
        // ── Appendix 2: 위험중립확률 ──
        if (trees.path("risk_neutral_prob").isArray) {
            h(doc, fH, "Appendix 2. 위험중립확률 (스텝별 p)")
            doc.add(probTable(bf, trees.path("risk_neutral_prob")))
            doc.add(space(10f))
        }
        // ── Appendix 3: 가격트리(11 축약) ──
        if (trees.isObject) {
            h(doc, fH, "Appendix 3. 가격 트리 (${N}노드 축약 · 전체는 엑셀)")
            val isGs = trees.path("tree_meta").path("model").asText("") == "GS"
            treeTable(doc, bf, "기초자산", trees.path("underlying_tree"))
            treeTable(doc, bf, "상품가치(composite)", trees.path("composite_tree"))
            treeTable(doc, bf, if (isGs) "지분분(q·V)" else "지분요소", trees.path("equity_tree"))
            treeTable(doc, bf, if (isGs) "부채분((1-q)·V)" else "부채요소", trees.path("debt_tree"))
            if (isGs && trees.path("conversion_prob_tree").isArray) treeTable(doc, bf, "전환확률(q)", trees.path("conversion_prob_tree"))
            doc.add(space(10f))
        }
        // ── Appendix 4: 민감도 ──
        val sens = result.path("sensitivity")
        if (sens.isObject) {
            h(doc, fH, "Appendix 4. 민감도 분석 (변동성 × 기초자산)")
            doc.add(sensitivityTable(bf, sens))
            doc.add(space(10f))
        }

        // ── 평가 식별 정보(감사추적) ──
        h(doc, fH, "평가 식별 정보 (감사추적)")
        val repro = result.path("reproducibility")
        doc.add(kv(bf, listOf(
            "발급번호" to reportNo, "Job ID" to result.path("job_id").asText("—"),
            "input_hash" to repro.path("input_hash").asText("—"),
            "model_version" to repro.path("model_version").asText("—"),
            "rate_mode" to trees.path("tree_meta").path("rate_mode").asText("—"),
            "발급일시" to issuedAt,
        )))

        doc.close()
        return baos.toByteArray()
    }

    private fun modelLabel(m: String) = when (m) {
        "GS" -> "Goldman-Sachs (GS)"; "TF_LATTICE", "LATTICE" -> "Tsiveriotis-Fernandes (T&F)"; else -> m
    }

    private fun h(doc: Document, f: Font, t: String) { doc.add(space(6f)); doc.add(Paragraph(t, f)); doc.add(space(4f)) }
    private fun space(h: Float) = Paragraph(" ").also { it.spacingAfter = h }

    private fun n(v: Double?): String = if (v == null) "—" else String.format("%,.4f", v)
    private fun js(node: JsonNode?, key: String): String {
        val v = node?.path(key) ?: return "—"
        return if (v.isMissingNode || v.isNull) "—" else v.asText()
    }

    private fun cell(bf: BaseFont, text: String, bold: Boolean = false, bg: Color? = null, align: Int = Element.ALIGN_LEFT): PdfPCell {
        val f = Font(bf, 8.5f, if (bold) Font.BOLD else Font.NORMAL, Color.BLACK)
        return PdfPCell(Phrase(text, f)).apply {
            horizontalAlignment = align; paddingTop = 2f; paddingBottom = 3f; paddingLeft = 4f; paddingRight = 4f
            borderColor = Color(226, 232, 240); bg?.let { backgroundColor = it }
        }
    }

    private fun kv(bf: BaseFont, rows: List<Pair<String, String>>): PdfPTable {
        val t = PdfPTable(floatArrayOf(1f, 2f)); t.widthPercentage = 100f
        rows.forEach { (k, v) -> t.addCell(cell(bf, k, bg = LIGHT)); t.addCell(cell(bf, v, align = Element.ALIGN_RIGHT)) }
        return t
    }

    private val COMP_LABELS = linkedMapOf(
        "bond_value" to "채권가치", "preferred_share_value" to "우선주가치", "conversion_option_value" to "전환옵션",
        "exchange_option_value" to "교환옵션", "warrant_value" to "신주인수권", "redemption_option_value" to "상환권",
        "issuer_call_value" to "발행자콜", "sale_claim_value" to "매도청구권", "stock_option_value" to "주식매수선택권",
        "conditional_option_value" to "조건부SO", "dilution_effect" to "희석효과",
    )
    private fun componentsTable(bf: BaseFont, comp: JsonNode): PdfPTable {
        val t = PdfPTable(floatArrayOf(2f, 1f)); t.widthPercentage = 100f
        t.addCell(cell(bf, "구성요소", bold = true, bg = LIGHT)); t.addCell(cell(bf, "가치", bold = true, bg = LIGHT, align = Element.ALIGN_RIGHT))
        var sum = 0.0
        COMP_LABELS.forEach { (k, lbl) ->
            val v = comp.path(k)
            if (!v.isNull && !v.isMissingNode) {
                sum += v.asDouble()
                t.addCell(cell(bf, lbl)); t.addCell(cell(bf, n(v.asDouble()), align = Element.ALIGN_RIGHT))
            }
        }
        t.addCell(cell(bf, "합계(Σ)", bold = true)); t.addCell(cell(bf, n(sum), bold = true, align = Element.ALIGN_RIGHT))
        return t
    }

    private fun paramsTable(bf: BaseFont, kp: JsonNode): PdfPTable {
        val labels = linkedMapOf(
            "model_name" to "모형", "volatility" to "변동성(%)", "risk_free_rate" to "무위험율(%)",
            "discount_rate" to "위험할인율 Rd(%)", "credit_spread" to "신용스프레드(%)", "dividend_yield" to "배당(%)",
            "u" to "상승계수 u", "d" to "하락계수 d", "lattice_steps" to "격자 스텝", "parity" to "패리티",
        )
        val t = PdfPTable(floatArrayOf(1f, 1f)); t.widthPercentage = 100f
        labels.forEach { (k, lbl) ->
            val v = kp.path(k)
            if (!v.isNull && !v.isMissingNode) { t.addCell(cell(bf, lbl, bg = LIGHT)); t.addCell(cell(bf, v.asText(), align = Element.ALIGN_RIGHT)) }
        }
        return t
    }

    private fun rateTable(doc: Document, bf: BaseFont, title: String, tbl: JsonNode) {
        if (!tbl.isObject) return
        val spot = tbl.path("spot"); val ytm = tbl.path("ytm"); val fwd = tbl.path("forward")
        val cols = minOf(N, spot.size())
        doc.add(Paragraph(title, Font(bf, 9f, Font.BOLD, SLATE)))
        val t = PdfPTable(1 + cols); t.widthPercentage = 100f
        t.addCell(cell(bf, "만기(y)", bold = true, bg = LIGHT))
        for (i in 0 until cols) t.addCell(cell(bf, spot.get(i).get(0).asText(), bold = true, bg = LIGHT, align = Element.ALIGN_RIGHT))
        listOf("YTM" to ytm, "SPOT" to spot, "FORWARD" to fwd).forEach { (lbl, arr) ->
            t.addCell(cell(bf, lbl, bold = true))
            for (i in 0 until cols) t.addCell(cell(bf, arr.get(i).get(1).asText(), align = Element.ALIGN_RIGHT))
        }
        doc.add(t); doc.add(space(4f))
    }

    private fun probTable(bf: BaseFont, prob: JsonNode): PdfPTable {
        val cols = minOf(N, prob.size())
        val t = PdfPTable(1 + cols + 1); t.widthPercentage = 100f
        t.addCell(cell(bf, "스텝", bold = true, bg = LIGHT))
        for (i in 0 until cols) t.addCell(cell(bf, prob.get(i).path("step").asText(), bold = true, bg = LIGHT, align = Element.ALIGN_RIGHT))
        t.addCell(cell(bf, "…", bold = true, bg = LIGHT, align = Element.ALIGN_RIGHT))
        t.addCell(cell(bf, "p(상승)", bold = true))
        for (i in 0 until cols) t.addCell(cell(bf, prob.get(i).path("p").asText(), align = Element.ALIGN_RIGHT))
        t.addCell(cell(bf, if (prob.size() > cols) "…" else "", align = Element.ALIGN_RIGHT))
        return t
    }

    private fun treeTable(doc: Document, bf: BaseFont, title: String, tree: JsonNode) {
        if (!tree.isArray || tree.size() == 0) return
        val steps = minOf(N, tree.size())
        val nodes = minOf(N, tree.get(steps - 1).size())
        doc.add(Paragraph(title, Font(bf, 9f, Font.BOLD, SLATE)))
        val t = PdfPTable(1 + nodes + 1); t.widthPercentage = 100f
        t.addCell(cell(bf, "step\\node", bold = true, bg = LIGHT))
        for (j in 0 until nodes) t.addCell(cell(bf, j.toString(), bold = true, bg = LIGHT, align = Element.ALIGN_RIGHT))
        t.addCell(cell(bf, "…", bold = true, bg = LIGHT, align = Element.ALIGN_RIGHT))
        for (st in 0 until steps) {
            t.addCell(cell(bf, st.toString(), bold = true))
            val row = tree.get(st)
            for (j in 0 until nodes) {
                val v = row.get(j)
                t.addCell(cell(bf, if (v == null || v.isNull) "–" else v.asText(), align = Element.ALIGN_RIGHT))
            }
            t.addCell(cell(bf, "", align = Element.ALIGN_RIGHT))
        }
        doc.add(t); doc.add(space(6f))
    }

    private fun sensitivityTable(bf: BaseFont, sens: JsonNode): PdfPTable {
        val vol = sens.path("vol_axis"); val spot = sens.path("spot_axis"); val grid = sens.path("total_grid")
        val t = PdfPTable(1 + spot.size()); t.widthPercentage = 100f
        t.addCell(cell(bf, "σ＼기초자산", bold = true, bg = LIGHT))
        for (i in 0 until spot.size()) t.addCell(cell(bf, spot.get(i).asText(), bold = true, bg = LIGHT, align = Element.ALIGN_RIGHT))
        for (r in 0 until grid.size()) {
            t.addCell(cell(bf, String.format("%.2f%%", vol.get(r).asDouble() * 100), bold = true))
            for (c in 0 until grid.get(r).size()) {
                val base = r == 1 && c == 1
                t.addCell(cell(bf, n(grid.get(r).get(c).asDouble()), bold = base, bg = if (base) LIGHT else null, align = Element.ALIGN_RIGHT))
            }
        }
        return t
    }

    companion object {
        const val DISCLAIMER =
            "본 보고서는 FairValue Engine이 이용자가 입력·선택한 계약조건·시장데이터·평가모형에 기초하여 평가기준일 현재 기준으로 산출한 결과입니다. " +
                "시장상황 변화·입력값 오류·모형의 내재적 한계에 따라 실제 가치와 다를 수 있으며, 회계·세무·투자 판단의 참고자료로서 최종 판단과 책임은 이용자에게 있습니다. " +
                "본 보고서는 평가기준일 현재 기준으로 유효하며, 평가기준일 이후의 시장상황·계약조건 변화를 반영하지 않습니다. 무단 복제·배포를 금합니다."
        const val METHOD_TF =
            "Tsiveriotis-Fernandes(1998) 모형: 복합금융상품을 지분요소(전환 시 주식가치, 무위험이자율 rf 로 할인)와 부채요소(현금 원리금, 위험조정이자율 rd 로 할인)로 분리하여 " +
                "CRR 이항격자에서 backward induction으로 평가한다. 각 노드에서 보유자의 전환·상환청구권, 발행자의 콜을 최적행사로 반영한다. " +
                "u=exp(σ√Δt), d=1/u, p=(exp((rf−q)Δt)−d)/(u−d)."
        const val METHOD_GS =
            "Goldman-Sachs(1994) 전환확률 가중 할인: 지분·부채요소를 분리하지 않고, 각 노드의 전환확률 q를 직후 노드 q의 위험중립 기대로 전파하되 전환 시 1, 상환청구(현금) 시 0으로 갱신하며, " +
                "위험조정할인율 y=q·rf+(1−q)·rd로 단일 이항트리를 backward induction하여 평가한다. 발행자 신용위험이 전환확률에 연동되어 할인율에 반영됨."
    }
}
