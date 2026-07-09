package com.fairvalue.service

import com.fasterxml.jackson.databind.JsonNode
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.ColumnText
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfPageEventHelper
import com.lowagie.text.pdf.PdfTemplate
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream

/**
 * 평가보고서 PDF (OpenPDF). 실보고서 표준 구성. 브랜드=FairValue Engine·네이비(#090946).
 *   5-9 v2 레이아웃 정비: 섹션(제목+표) keepTogether(P1·P2), 라벨 noWrap+폭(P3),
 *   자릿수 규칙으로 셀 한 줄(P4·P5), 네이비 톤 통일, 페이지 푸터 p/N.
 *   ★값·정밀도·구성 불변 — 표시(반올림/서식)만. 전체 정밀값은 엑셀.
 *   한글: /fonts/NotoSansKR-Regular.ttf 임베딩(있으면) → 없으면 OpenPDF 내장 CJK(HYGoThic).
 */
@Component
class ReportPdfBuilder {

    private val NAVY = Color(9, 9, 70)
    private val WHITE = Color.WHITE
    private val LABEL_BG = Color(241, 245, 249)     // 연회색(라벨/헤더 보조)
    private val BASE_BG = Color(219, 227, 240)      // 연네이비(민감도 base)
    private val LINE = Color(203, 213, 225)         // 괘선 0.5pt
    private val GRAY = Color(120, 120, 130)
    private val N = 11   // 축약 노드 수

    private fun koreanBase(): BaseFont =
        runCatching {
            javaClass.getResourceAsStream("/fonts/NotoSansKR-Regular.ttf")?.use {
                BaseFont.createFont("NotoSansKR-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, it.readBytes(), null)
            }
        }.getOrNull()
            ?: BaseFont.createFont("HYGoThic-Medium", "UniKS-UCS2-H", BaseFont.NOT_EMBEDDED)

    // ── 자릿수 규칙(셀 한 줄 보장, →P4·P5) ──
    private fun fAmount(v: Double) = String.format("%,.2f", v)   // 결과·민감도·약정 금액
    private fun fInt(v: Double) = String.format("%,.0f", v)      // 트리 값·액면·parity·steps
    private fun fProb(v: Double) = String.format("%.4f", v)      // 위험중립 p·전환확률 q
    private fun fRate(v: Double) = String.format("%.4f", v)      // YTM/SPOT/FORWARD·%
    private fun fCoef(v: Double) = String.format("%.6f", v)      // u·d

    fun build(reportNo: String, instrumentName: String, instrumentType: String, issuer: String?,
              result: JsonNode, context: JsonNode?, issuedAt: String): ByteArray {
        val bf = koreanBase()
        val fTitle = Font(bf, 22f, Font.BOLD, NAVY)
        val fBrand = Font(bf, 11f, Font.BOLD, NAVY)
        val fName = Font(bf, 14f, Font.BOLD, Color.BLACK)
        val fBody = Font(bf, 9.5f, Font.NORMAL, Color.BLACK)
        val fSmall = Font(bf, 8f, Font.NORMAL, Color(71, 85, 105))

        val doc = Document(PageSize.A4, 48f, 48f, 54f, 54f)
        val baos = ByteArrayOutputStream()
        val writer = PdfWriter.getInstance(doc, baos)
        writer.pageEvent = FooterEvent(bf, reportNo)
        doc.open()

        val valDate = result.path("valuation_date").asText("—")
        val model = result.path("key_parameters").path("model_name").asText("—")

        // ── 표지(1페이지 상단) ──
        doc.add(Paragraph("FairValue Engine", fBrand))
        doc.add(space(20f))
        doc.add(Paragraph("평가보고서", fTitle))
        doc.add(space(6f))
        doc.add(Paragraph(instrumentName, fName))
        doc.add(space(14f))
        section(doc, bf, "기본 정보", kv(bf, listOf(
            "상품유형" to instrumentType, "발행사" to (issuer ?: "—"),
            "평가기준일" to valDate, "적용모형" to modelLabel(model), "발급번호" to reportNo,
        )))

        // ── 유의사항 ──
        section(doc, bf, "평가 개요 및 유의사항", Paragraph(DISCLAIMER, fSmall))

        // ── 평가결과 요약(1페이지 끝) ──
        val summary = kv(bf, listOf(
            "공정가치(총액)" to fAmount(result.path("total_fair_value").asDouble()),
            "단위당(1좌)" to fAmount(result.path("per_unit_value").asDouble()),
        ))
        section(doc, bf, "평가결과 요약", summary, Paragraph(" ", fSmall), componentsTable(bf, result.path("components")))

        // ★P1: 1페이지는 결과요약까지 — 이후 강제 개행. 2페이지가 '주요 약정사항'으로 시작.
        doc.newPage()

        // ── 주요 약정사항 ──
        val terms = context?.path("terms")
        section(doc, bf, "주요 약정사항", kv(bf, listOf(
            "발행일" to jsText(terms, "issue_date"), "만기일" to jsText(terms, "maturity_date"),
            "액면금액" to jsNum(terms, "face_value") { fInt(it) }, "발행금액" to jsNum(terms, "issue_amount") { fInt(it) },
            "표면이자율(%)" to jsNum(terms, "coupon_rate") { fRate(it) }, "보장수익률(%)" to jsNum(terms, "guaranteed_yield") { fRate(it) },
        )))

        // ── 평가방법론 ──
        section(doc, bf, "평가방법론", Paragraph(if (model == "GS") METHOD_GS else METHOD_TF, fBody))

        // ── 주요 가정·파라미터 ──
        section(doc, bf, "주요 가정 및 파라미터", paramsTable(bf, result.path("key_parameters")))

        // ── Appendix 1: 이자율(rf·rd 각각 keepTogether, →P2) ──
        val cb = result.path("curve_bootstrap")
        if (cb.path("rf").isObject) section(doc, bf, "Appendix 1. 이자율 산정 · 무위험(rf)", rateTable(bf, cb.path("rf")))
        if (cb.path("rd").isObject) section(doc, bf, "Appendix 1. 이자율 산정 · 위험(rd)", rateTable(bf, cb.path("rd")))

        // ── Appendix 2: 위험중립확률 ──
        val trees = result.path("trees")
        if (trees.path("risk_neutral_prob").isArray) {
            section(doc, bf, "Appendix 2. 위험중립확률 (스텝별 p)", probTable(bf, trees.path("risk_neutral_prob")))
        }

        // ── Appendix 3: 가격트리(각 트리 개별 keepTogether) ──
        if (trees.isObject) {
            val isGs = trees.path("tree_meta").path("model").asText("") == "GS"
            section(doc, bf, "Appendix 3. 가격 트리 · 기초자산 (${N}노드 축약 · 전체는 엑셀)", treeTable(bf, trees.path("underlying_tree"), false))
            section(doc, bf, "Appendix 3. 가격 트리 · 상품가치(composite)", treeTable(bf, trees.path("composite_tree"), false))
            section(doc, bf, "Appendix 3. 가격 트리 · ${if (isGs) "지분분(q·V)" else "지분요소"}", treeTable(bf, trees.path("equity_tree"), false))
            section(doc, bf, "Appendix 3. 가격 트리 · ${if (isGs) "부채분((1-q)·V)" else "부채요소"}", treeTable(bf, trees.path("debt_tree"), false))
            if (isGs && trees.path("conversion_prob_tree").isArray) {
                section(doc, bf, "Appendix 3. 가격 트리 · 전환확률(q)", treeTable(bf, trees.path("conversion_prob_tree"), true))
            }
        }

        // ── Appendix 4: 민감도 ──
        val sens = result.path("sensitivity")
        if (sens.isObject) section(doc, bf, "Appendix 4. 민감도 분석 (변동성 × 기초자산)", sensitivityTable(bf, sens))

        // ── 평가 식별 정보(감사추적) ──
        val repro = result.path("reproducibility")
        section(doc, bf, "평가 식별 정보 (감사추적)", kv(bf, listOf(
            "발급번호" to reportNo, "Job ID" to result.path("job_id").asText("—"),
            "input_hash" to repro.path("input_hash").asText("—"),
            "model_version" to repro.path("model_version").asText("—"),
            "rate_mode" to trees.path("tree_meta").path("rate_mode").asText("—"),
            "발급일시" to issuedAt,
        ), hashRow = "input_hash"))

        doc.close()
        return baos.toByteArray()
    }

    private fun modelLabel(m: String) = when (m) {
        "GS" -> "Goldman-Sachs (GS)"; "TF_LATTICE", "LATTICE" -> "Tsiveriotis-Fernandes (T&F)"; else -> m
    }

    private fun space(h: Float) = Paragraph(" ").also { it.spacingAfter = h; it.leading = 1f }

    // ── 섹션: 제목바(네이비/흰) + 내용들을 단일 keepTogether 외곽 셀에 담는다(P1·P2) ──
    private fun section(doc: Document, bf: BaseFont, title: String, vararg content: Element) {
        val outer = PdfPTable(1)
        outer.setWidthPercentage(100f)
        outer.setKeepTogether(true)
        outer.setSpacingBefore(14f)
        outer.setSpacingAfter(8f)
        val tc = PdfPCell(Phrase(title, Font(bf, 11f, Font.BOLD, WHITE)))
        tc.backgroundColor = NAVY; tc.setPadding(5f); tc.border = Rectangle.NO_BORDER
        outer.addCell(tc)
        val cc = PdfPCell()
        cc.border = Rectangle.NO_BORDER; cc.paddingTop = 5f; cc.setPaddingLeft(0f); cc.setPaddingRight(0f)
        content.forEach { cc.addElement(it) }
        outer.addCell(cc)
        doc.add(outer)
    }

    private fun bodyFont(bf: BaseFont, bold: Boolean, size: Float, color: Color = Color.BLACK) =
        Font(bf, size, if (bold) Font.BOLD else Font.NORMAL, color)

    /** 헤더 셀(네이비 배경·흰 글자·noWrap). */
    private fun hdr(bf: BaseFont, text: String, align: Int = Element.ALIGN_CENTER, size: Float = 8f): PdfPCell =
        PdfPCell(Phrase(text, bodyFont(bf, true, size, WHITE))).apply {
            backgroundColor = NAVY; horizontalAlignment = align; setNoWrap(true)
            paddingTop = 3f; paddingBottom = 3f; paddingLeft = 4f; paddingRight = 4f; borderColor = LINE
        }

    /** 라벨 셀(연회색·볼드·noWrap — 단어 중간 줄바꿈 금지, →P3). */
    private fun lbl(bf: BaseFont, text: String, size: Float = 8.5f): PdfPCell =
        PdfPCell(Phrase(text, bodyFont(bf, true, size))).apply {
            backgroundColor = LABEL_BG; setNoWrap(true)
            paddingTop = 3f; paddingBottom = 3f; paddingLeft = 5f; paddingRight = 5f; borderColor = LINE
        }

    /** 데이터 셀. wrap=false(기본, 숫자 한 줄) / wrap=true(해시·문단 예외). */
    private fun dat(bf: BaseFont, text: String, align: Int = Element.ALIGN_RIGHT, size: Float = 8.5f,
                    bg: Color? = null, bold: Boolean = false, wrap: Boolean = false): PdfPCell =
        PdfPCell(Phrase(text, bodyFont(bf, bold, if (wrap) 7f else size))).apply {
            horizontalAlignment = align; if (!wrap) setNoWrap(true)
            bg?.let { backgroundColor = it }
            paddingTop = 3f; paddingBottom = 3f; paddingLeft = 5f; paddingRight = 5f; borderColor = LINE
        }

    // 2열 라벨/값 표. hashRow 지정 시 그 라벨 행 값은 wrap 허용(64자 해시).
    private fun kv(bf: BaseFont, rows: List<Pair<String, String>>, hashRow: String? = null): PdfPTable {
        val t = PdfPTable(floatArrayOf(1.2f, 2f)); t.widthPercentage = 100f
        rows.forEach { (k, v) ->
            t.addCell(lbl(bf, k))
            t.addCell(dat(bf, v, align = Element.ALIGN_LEFT, wrap = (k == hashRow)))
        }
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
        t.addCell(hdr(bf, "구성요소", Element.ALIGN_LEFT)); t.addCell(hdr(bf, "가치", Element.ALIGN_RIGHT))
        var sum = 0.0
        COMP_LABELS.forEach { (k, lbl) ->
            val v = comp.path(k)
            if (!v.isNull && !v.isMissingNode) {
                sum += v.asDouble()
                t.addCell(dat(bf, lbl, align = Element.ALIGN_LEFT)); t.addCell(dat(bf, fAmount(v.asDouble())))
            }
        }
        t.addCell(dat(bf, "합계 (Σ)", align = Element.ALIGN_LEFT, bold = true, bg = LABEL_BG))
        t.addCell(dat(bf, fAmount(sum), bold = true, bg = LABEL_BG))
        return t
    }

    private fun paramsTable(bf: BaseFont, kp: JsonNode): PdfPTable {
        data class P(val key: String, val label: String, val fmt: (Double) -> String)
        val rows = listOf(
            P("volatility", "변동성(%)", ::fRate), P("risk_free_rate", "무위험율(%)", ::fRate),
            P("discount_rate", "위험할인율 Rd(%)", ::fRate), P("credit_spread", "신용스프레드(%)", ::fRate),
            P("dividend_yield", "배당(%)", ::fRate), P("u", "상승계수 u", ::fCoef), P("d", "하락계수 d", ::fCoef),
            P("parity", "패리티", ::fInt), P("lattice_steps", "격자 스텝", ::fInt),
        )
        val t = PdfPTable(floatArrayOf(1.3f, 1f)); t.widthPercentage = 100f
        t.addCell(hdr(bf, "항목", Element.ALIGN_LEFT)); t.addCell(hdr(bf, "값", Element.ALIGN_RIGHT))
        val mn = kp.path("model_name")
        if (!mn.isMissingNode && !mn.isNull) { t.addCell(lbl(bf, "모형")); t.addCell(dat(bf, modelLabel(mn.asText()), align = Element.ALIGN_LEFT)) }
        rows.forEach { p ->
            val v = kp.path(p.key)
            if (!v.isNull && !v.isMissingNode && v.isNumber) { t.addCell(lbl(bf, p.label)); t.addCell(dat(bf, p.fmt(v.asDouble()))) }
        }
        return t
    }

    // 이자율: 라벨열 확대(≈1.6×), 라벨 noWrap(→P3). 값 fRate(→P4).
    private fun rateTable(bf: BaseFont, tbl: JsonNode): PdfPTable {
        val spot = tbl.path("spot"); val ytm = tbl.path("ytm"); val fwd = tbl.path("forward")
        val cols = minOf(N, spot.size())
        val widths = FloatArray(1 + cols); widths[0] = 1.6f; for (i in 1..cols) widths[i] = 1f
        val t = PdfPTable(widths.size); t.widthPercentage = 100f; t.setWidths(widths)
        t.addCell(hdr(bf, "만기(y)", Element.ALIGN_LEFT, 7.5f))
        for (i in 0 until cols) t.addCell(hdr(bf, fRate(spot.get(i).get(0).asDouble()), Element.ALIGN_RIGHT, 7.5f))
        listOf("YTM" to ytm, "SPOT" to spot, "FORWARD" to fwd).forEach { (lb, arr) ->
            t.addCell(lbl(bf, lb, 7.5f))
            for (i in 0 until cols) t.addCell(dat(bf, fRate(arr.get(i).get(1).asDouble()), size = 7.5f))
        }
        return t
    }

    private fun probTable(bf: BaseFont, prob: JsonNode): PdfPTable {
        val cols = minOf(N, prob.size())
        val widths = FloatArray(2 + cols); widths[0] = 1.4f; for (i in 1..cols) widths[i] = 1f; widths[cols + 1] = 0.5f
        val t = PdfPTable(widths.size); t.widthPercentage = 100f; t.setWidths(widths)
        t.addCell(hdr(bf, "스텝", Element.ALIGN_LEFT, 7.5f))
        for (i in 0 until cols) t.addCell(hdr(bf, prob.get(i).path("step").asText(), Element.ALIGN_RIGHT, 7.5f))
        t.addCell(hdr(bf, "…", Element.ALIGN_RIGHT, 7.5f))
        t.addCell(lbl(bf, "p(상승)", 7.5f))
        for (i in 0 until cols) t.addCell(dat(bf, fProb(prob.get(i).path("p").asDouble()), size = 7.5f))
        t.addCell(dat(bf, if (prob.size() > cols) "…" else "", size = 7.5f))
        return t
    }

    // 트리: 코너 't\j', 값 트리 fInt / 확률 트리 fProb, 셀 noWrap 7pt(→P3·P4).
    private fun treeTable(bf: BaseFont, tree: JsonNode, prob: Boolean): PdfPTable {
        if (!tree.isArray || tree.size() == 0) return PdfPTable(1)
        val steps = minOf(N, tree.size())
        val nodes = minOf(N, tree.get(steps - 1).size())
        val widths = FloatArray(2 + nodes); widths[0] = 0.7f; for (i in 1..nodes) widths[i] = 1f; widths[nodes + 1] = 0.45f
        val t = PdfPTable(widths.size); t.widthPercentage = 100f; t.setWidths(widths)
        t.addCell(hdr(bf, "t\\j", Element.ALIGN_LEFT, 7f))
        for (j in 0 until nodes) t.addCell(hdr(bf, j.toString(), Element.ALIGN_RIGHT, 7f))
        t.addCell(hdr(bf, "…", Element.ALIGN_RIGHT, 7f))
        for (st in 0 until steps) {
            t.addCell(lbl(bf, st.toString(), 7f))
            val row = tree.get(st)
            for (j in 0 until nodes) {
                val v = row.get(j)
                val txt = if (v == null || v.isNull) "–" else if (prob) fProb(v.asDouble()) else fInt(v.asDouble())
                t.addCell(dat(bf, txt, size = 7f))
            }
            t.addCell(dat(bf, "", size = 7f))
        }
        return t
    }

    private fun sensitivityTable(bf: BaseFont, sens: JsonNode): PdfPTable {
        val vol = sens.path("vol_axis"); val spot = sens.path("spot_axis"); val grid = sens.path("total_grid")
        val widths = FloatArray(1 + spot.size()); widths[0] = 1.4f; for (i in 1..spot.size()) widths[i] = 1f
        val t = PdfPTable(widths.size); t.widthPercentage = 100f; t.setWidths(widths)
        t.addCell(hdr(bf, "σ＼기초자산", Element.ALIGN_LEFT))
        for (i in 0 until spot.size()) t.addCell(hdr(bf, fInt(spot.get(i).asDouble()), Element.ALIGN_RIGHT))
        for (r in 0 until grid.size()) {
            t.addCell(lbl(bf, String.format("%.2f%%", vol.get(r).asDouble() * 100)))
            for (c in 0 until grid.get(r).size()) {
                val base = r == 1 && c == 1
                t.addCell(dat(bf, fAmount(grid.get(r).get(c).asDouble()), bold = base, bg = if (base) BASE_BG else null))
            }
        }
        return t
    }

    private fun jsText(node: JsonNode?, key: String): String {
        val v = node?.path(key) ?: return "—"
        return if (v.isMissingNode || v.isNull) "—" else v.asText()
    }
    private fun jsNum(node: JsonNode?, key: String, fmt: (Double) -> String): String {
        val v = node?.path(key) ?: return "—"
        return if (v.isMissingNode || v.isNull || !v.isNumber) "—" else fmt(v.asDouble())
    }

    /** 페이지 푸터: "FairValue Engine · {발급번호} · p. n / N". 총 페이지는 템플릿으로 마감 시 기입. */
    private inner class FooterEvent(val bf: BaseFont, val reportNo: String) : PdfPageEventHelper() {
        private var total: PdfTemplate? = null
        override fun onOpenDocument(writer: PdfWriter, document: Document) {
            total = writer.directContent.createTemplate(24f, 12f)
        }
        override fun onEndPage(writer: PdfWriter, document: Document) {
            val cb = writer.directContent
            val f = Font(bf, 7.5f, Font.NORMAL, GRAY)
            val head = "FairValue Engine · $reportNo · p. ${writer.pageNumber} / "
            val phrase = Phrase(head, f)
            val x = document.leftMargin()
            val y = document.bottom() - 20f
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, phrase, x, y, 0f)
            val w = bf.getWidthPoint(head, 7.5f)
            total?.let { cb.addTemplate(it, x + w, y) }
        }
        override fun onCloseDocument(writer: PdfWriter, document: Document) {
            total?.apply {
                beginText(); setFontAndSize(bf, 7.5f); setColorFill(GRAY)
                showText((writer.pageNumber - 1).toString()); endText()
            }
        }
    }

    companion object {
        const val DISCLAIMER =
            "본 보고서는 FairValue Engine이 이용자가 입력·선택한 계약조건·시장데이터·평가모형에 기초하여 평가기준일 현재 기준으로 산출한 결과입니다. " +
                "시장상황 변화·입력값 오류·모형의 내재적 한계에 따라 실제 가치와 다를 수 있으며, 회계·세무·투자 판단의 참고자료로서 최종 판단과 책임은 이용자에게 있습니다. " +
                "본 보고서는 평가기준일 현재 기준으로 유효하며, 평가기준일 이후의 시장상황·계약조건 변화를 반영하지 않습니다. " +
                "표시 수치는 가독성을 위해 반올림되었으며, 전체 정밀값은 계산근거 엑셀을 따릅니다. 무단 복제·배포를 금합니다."
        const val METHOD_TF =
            "Tsiveriotis-Fernandes(1998) 모형: 복합금융상품을 지분요소(전환 시 주식가치, 무위험이자율 rf 로 할인)와 부채요소(현금 원리금, 위험조정이자율 rd 로 할인)로 분리하여 " +
                "CRR 이항격자에서 backward induction으로 평가한다. 각 노드에서 보유자의 전환·상환청구권, 발행자의 콜을 최적행사로 반영한다. " +
                "u=exp(σ√Δt), d=1/u, p=(exp((rf−q)Δt)−d)/(u−d)."
        const val METHOD_GS =
            "Goldman-Sachs(1994) 전환확률 가중 할인: 지분·부채요소를 분리하지 않고, 각 노드의 전환확률 q를 직후 노드 q의 위험중립 기대로 전파하되 전환 시 1, 상환청구(현금) 시 0으로 갱신하며, " +
                "위험조정할인율 y=q·rf+(1−q)·rd로 단일 이항트리를 backward induction하여 평가한다. 발행자 신용위험이 전환확률에 연동되어 할인율에 반영됨."
    }
}
