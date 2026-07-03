package com.fairvalue.service

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 변동성 산출(순수 로직, Spring 무의존 — 단위테스트 대상). E3b.
 *
 * 방법론(실보고서 공통):
 *   1) 유사회사 주가 CSV(날짜, 종가) → 날짜 오름차순 정렬·중복(동일날짜) 1건 유지·결측/0/음수 제외.
 *   2) 일별 로그수익률 r_i = ln(P_i / P_{i-1}).
 *   3) 일변동성 = 표본표준편차(ddof=1, n-1).
 *   4) 연변동성 = 일변동성 × √(연간 거래일수)  [기본 250, 호출측 편집값 사용].
 *   5) 유사회사 여럿 → 회사별 연변동성 단순평균 = 채택(호출측 편집 가능).
 *   6) 수익률 표본 < 20 → 경고(저장 허용).
 *
 * ★ CSV 실무 견고성:
 *   (a) 인코딩: UTF-8(BOM 제거)·엄격 판별 실패 시 CP949(MS949, EUC-KR 포함) 폴백, 둘 다 실패 시 명확 오류.
 *   (b) 종가 천단위 콤마("1,234") 제거(숫자 내부 콤마만).
 *   (c) 날짜 포맷 유연: yyyy-MM-dd / yyyy.MM.dd / yyyy/MM/dd / yyyyMMdd.
 *   (d) 내림차순 입력 허용(정렬로 흡수).
 */
object VolatilityCalculator {

    const val ANNUAL_TRADING_DAYS_DEFAULT = 250
    const val MIN_RETURN_SAMPLES = 20

    data class Company(
        val name: String,
        val observations: Int,     // 유효 종가 관측치 수
        val periodStart: LocalDate?,
        val periodEnd: LocalDate?,
        val dailyVol: Double,      // 소수(0.021 = 2.1%)
        val annualVol: Double,     // 소수
        val warnings: List<String>,
    )

    data class ComputeResult(
        val companies: List<Company>,
        val average: Double,        // 채택 후보(회사별 annualVol 단순평균), 소수
        val tradingDaysUsed: Int,
        val warnings: List<String>,
    )

    /** ★(a) 바이트 → 문자열. UTF-8(BOM 제거) 엄격 → 실패 시 CP949 폴백 → 둘 다 실패 시 예외. */
    fun decodeCsv(bytes: ByteArray): String {
        var b = bytes
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) {
            b = b.copyOfRange(3, b.size)   // UTF-8 BOM 제거
        }
        val dec = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            return dec.decode(ByteBuffer.wrap(b)).toString()
        } catch (_: Exception) {
            val cs = runCatching { Charset.forName("MS949") }.getOrNull()
                ?: runCatching { Charset.forName("EUC-KR") }.getOrNull()
            if (cs != null) {
                return String(b, cs)
            }
            throw IllegalArgumentException("CSV 인코딩을 해석할 수 없습니다(UTF-8 또는 CP949/EUC-KR 지원).")
        }
    }

    /** ★(c) 날짜 유연 파싱. 실패 시 null. */
    fun parseDate(raw: String): LocalDate? {
        val s = raw.trim().trim('"')
        if (s.isEmpty()) return null
        // yyyyMMdd (8자리 숫자)
        if (s.length == 8 && s.all { it.isDigit() }) {
            return runCatching {
                LocalDate.of(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt())
            }.getOrNull()
        }
        val norm = s.replace('.', '-').replace('/', '-').replace(" ", "")
        return runCatching {
            val p = norm.split('-')
            LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }.getOrNull()
    }

    /**
     * (date, close) 2열로 분해. 구분자 우선순위: 탭 → 세미콜론 → 콤마.
     * 콤마의 경우 ★(b) 종가 천단위 콤마("1,234")를 위해 '첫 토큰=날짜, 나머지=종가(콤마 이어붙임)'로 처리한다.
     *   "2024-01-02,105"     → ["2024-01-02","105"]      (3자리 종가도 안전 — 구분자 콤마 보존)
     *   "2024-01-02,1,234"   → ["2024-01-02","1234"]     (천단위 콤마 흡수)
     */
    private fun splitDateClose(line: String): List<String> {
        val cols = when {
            line.contains('\t') -> line.split('\t')
            line.contains(';') -> line.split(';')
            else -> {
                val c = line.split(',')
                if (c.size < 2) c else listOf(c[0], c.drop(1).joinToString(""))
            }
        }
        return cols.map { it.trim().trim('"') }
    }

    /** CSV 텍스트 → (날짜, 종가) 시계열. 정렬·중복제거·결측/0/음수 제외. */
    fun parseSeries(text: String): List<Pair<LocalDate, Double>> {
        val byDate = LinkedHashMap<LocalDate, Double>()   // 동일날짜 → 마지막값 유지
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val cols = splitDateClose(line)
            if (cols.size < 2) continue
            val date = parseDate(cols[0]) ?: continue      // 헤더/비날짜 줄 자동 skip
            val close = cols[1].replace(",", "").replace(" ", "").toDoubleOrNull() ?: continue
            if (close <= 0.0 || close.isNaN() || close.isInfinite()) continue   // 결측/0/음수 제외
            byDate[date] = close
        }
        // ★(d) 오름차순 정렬(내림차순 입력 흡수)
        return byDate.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    /** 로그수익률 표본표준편차(ddof=1). 표본 부족(<2) → 0. */
    fun dailyVolatility(closes: List<Double>): Double {
        if (closes.size < 2) return 0.0
        val rs = (1 until closes.size).map { ln(closes[it] / closes[it - 1]) }
        val n = rs.size
        if (n < 2) return 0.0
        val mean = rs.sum() / n
        val varr = rs.sumOf { (it - mean) * (it - mean) } / (n - 1)   // ddof=1
        return sqrt(varr)
    }

    fun annualize(dailyVol: Double, tradingDays: Int): Double = dailyVol * sqrt(tradingDays.toDouble())

    /** 회사 1개 산출. name·CSV 텍스트·거래일수 → Company. */
    fun computeCompany(name: String, csvText: String, tradingDays: Int): Company {
        val series = parseSeries(csvText)
        val closes = series.map { it.second }
        val returnSamples = (closes.size - 1).coerceAtLeast(0)
        val warnings = mutableListOf<String>()
        if (returnSamples < MIN_RETURN_SAMPLES) {
            warnings += "수익률 표본 ${returnSamples}개 < ${MIN_RETURN_SAMPLES}개 — 변동성 신뢰도 낮음(저장은 허용)."
        }
        val daily = dailyVolatility(closes)
        return Company(
            name = name,
            observations = closes.size,
            periodStart = series.firstOrNull()?.first,
            periodEnd = series.lastOrNull()?.first,
            dailyVol = daily,
            annualVol = annualize(daily, tradingDays),
            warnings = warnings,
        )
    }

    /** 여러 회사(name→CSV) 산출 + 단순평균(채택 후보). */
    fun compute(files: List<Pair<String, String>>, tradingDays: Int): ComputeResult {
        val td = if (tradingDays > 0) tradingDays else ANNUAL_TRADING_DAYS_DEFAULT
        val companies = files.map { (name, text) -> computeCompany(name, text, td) }
        val vols = companies.map { it.annualVol }.filter { it > 0.0 }
        val average = if (vols.isNotEmpty()) vols.sum() / vols.size else 0.0
        val warnings = mutableListOf<String>()
        companies.forEach { c -> c.warnings.forEach { warnings += "[${c.name}] $it" } }
        if (companies.isEmpty()) warnings += "산출 대상 파일이 없습니다."
        return ComputeResult(companies, average, td, warnings)
    }
}
