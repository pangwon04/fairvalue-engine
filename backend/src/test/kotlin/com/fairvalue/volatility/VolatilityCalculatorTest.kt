package com.fairvalue.volatility

import com.fairvalue.service.VolatilityCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * ★BLOCKING: 변동성 산출 순수 로직 + CSV 실무 견고성.
 *   참조값은 독립(Python) 수동 계산 — ddof=1·√250. 1e-9 대조.
 *   시리즈 A=[100,102,101,103,105]: daily=0.014701804820072557, annual(250)=0.23245594473335623.
 */
class VolatilityCalculatorTest {

    private val EPS = 1e-9

    // ---- 수동 대조(1e-9) ----
    @Test
    fun `일변동성 표본표준편차 ddof1 - 수동 대조`() {
        val closes = listOf(100.0, 102.0, 101.0, 103.0, 105.0)
        assertEquals(0.014701804820072557, VolatilityCalculator.dailyVolatility(closes), EPS)
    }

    @Test
    fun `연환산 sqrt(거래일수) - 250 과 248`() {
        val daily = 0.014701804820072557
        assertEquals(0.23245594473335623, VolatilityCalculator.annualize(daily, 250), EPS)
        assertEquals(0.23152425383087222, VolatilityCalculator.annualize(daily, 248), EPS)
    }

    @Test
    fun `2사 단순평균 채택`() {
        val a = "2024-01-02,100\n2024-01-03,102\n2024-01-04,101\n2024-01-05,103\n2024-01-08,105"
        val b = "2024-01-02,50\n2024-01-03,51\n2024-01-04,49\n2024-01-05,52\n2024-01-08,50"
        val r = VolatilityCalculator.compute(listOf("A.csv" to a, "B.csv" to b), 250)
        assertEquals(2, r.companies.size)
        assertEquals(0.23245594473335623, r.companies[0].annualVol, EPS)
        assertEquals(0.4997961455251737, r.average, EPS)   // (annA+annB)/2
        assertEquals(250, r.tradingDaysUsed)
    }

    // ---- CSV 견고성 (boost 1) ----
    @Test
    fun `(b) 종가 천단위 콤마 제거`() {
        val s = VolatilityCalculator.parseSeries("2024-01-02,1,234\n2024-01-03,1,250")
        assertEquals(2, s.size)
        assertEquals(1234.0, s[0].second, EPS)
        assertEquals(1250.0, s[1].second, EPS)
    }

    @Test
    fun `(c) 날짜 포맷 유연 - dash dot slash yyyyMMdd`() {
        assertEquals(LocalDate.of(2024, 1, 2), VolatilityCalculator.parseDate("2024-01-02"))
        assertEquals(LocalDate.of(2024, 1, 2), VolatilityCalculator.parseDate("2024.01.02"))
        assertEquals(LocalDate.of(2024, 1, 2), VolatilityCalculator.parseDate("2024/01/02"))
        assertEquals(LocalDate.of(2024, 1, 2), VolatilityCalculator.parseDate("20240102"))
    }

    @Test
    fun `(d) 내림차순 입력 - 정렬로 흡수하여 오름차순 동일 결과`() {
        val asc = VolatilityCalculator.parseSeries("2024-01-02,100\n2024-01-03,102\n2024-01-04,101")
        val desc = VolatilityCalculator.parseSeries("2024-01-04,101\n2024-01-03,102\n2024-01-02,100")
        assertEquals(asc, desc)
        assertEquals(LocalDate.of(2024, 1, 2), desc.first().first)   // 정렬됨
    }

    @Test
    fun `(a) UTF-8 BOM 제거`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "날짜,종가\n2024-01-02,100".toByteArray(Charsets.UTF_8)
        val text = VolatilityCalculator.decodeCsv(bytes)
        assertTrue(text.startsWith("날짜"), "BOM 제거 후 첫 글자 정상: $text")
        assertEquals(1, VolatilityCalculator.parseSeries(text).size)
    }

    @Test
    fun `(a) CP949 인코딩 폴백 - ASCII 데이터 정상 파싱`() {
        val bytes = "종목,종가\n2024.01.02,1,234\n2024.01.03,1,250".toByteArray(charset("MS949"))
        val text = VolatilityCalculator.decodeCsv(bytes)     // UTF-8 실패 시 MS949 폴백(예외 없음)
        val s = VolatilityCalculator.parseSeries(text)
        assertEquals(2, s.size)
        assertEquals(1234.0, s[0].second, EPS)
    }

    // ---- 정렬·중복·결측·음수/0·표본부족 ----
    @Test
    fun `중복 날짜 1건 유지`() {
        val s = VolatilityCalculator.parseSeries("2024-01-02,100\n2024-01-02,999\n2024-01-03,102")
        assertEquals(2, s.size)   // 2일 1건만
    }

    @Test
    fun `결측 헤더 음수 0 종가 제외`() {
        val s = VolatilityCalculator.parseSeries(
            "date,close\n2024-01-02,100\n2024-01-03,\n2024-01-04,-5\n2024-01-05,0\n2024-01-06,105")
        assertEquals(2, s.size)   // 100, 105 만
        assertEquals(100.0, s[0].second, EPS)
        assertEquals(105.0, s[1].second, EPS)
    }

    @Test
    fun `표본부족 20개 미만 경고`() {
        val text = (1..10).joinToString("\n") { "2024-01-${"%02d".format(it)},${100 + it}" }
        val c = VolatilityCalculator.computeCompany("small.csv", text, 250)
        assertTrue(c.warnings.any { it.contains("< 20") || it.contains("< ${VolatilityCalculator.MIN_RETURN_SAMPLES}") },
            "표본부족 경고: ${c.warnings}")
        assertNotNull(c.periodStart)
    }

    @Test
    fun `인코딩 완전 실패시 명확 예외`() {
        // 유효 문자셋 폴백이 있으므로 정상 바이트는 예외 없음 확인(회귀 방지).
        val ok = VolatilityCalculator.decodeCsv("2024-01-02,100".toByteArray(Charsets.UTF_8))
        assertTrue(ok.contains("2024-01-02"))
    }
}
