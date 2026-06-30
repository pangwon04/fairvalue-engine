package com.fairvalue.curve

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * 보간(LINEAR/LOG_LINEAR)·평탄외삽·df/zero/forward 정합·음의 forward 경고.
 */
class InterpolationTest {

    @Test
    fun `LINEAR zero 선형 보간`() {
        val c = InterpolatedCurve(listOf(1.0 to 3.0, 2.0 to 4.0), "LINEAR")
        // 1.5 → 3.5% (선형)
        assertEquals(0.035, c.zeroRate(1.5), 1e-12)
    }

    @Test
    fun `평탄 5퍼센트 커브는 두 보간법 모두 5퍼센트`() {
        val pts = listOf(1.0 to 5.0, 2.0 to 5.0, 5.0 to 5.0)
        for (m in listOf("LINEAR", "LOG_LINEAR")) {
            val c = InterpolatedCurve(pts, m)
            assertEquals(0.05, c.zeroRate(1.5), 1e-9, "$m zero(1.5)")
            // df(1.5) = 1/1.05^1.5
            assertEquals(1.0 / 1.05.pow(1.5), c.discountFactor(1.5), 1e-9, "$m df(1.5)")
        }
    }

    @Test
    fun `평탄외삽 — 범위 밖은 끝점 rate 고정`() {
        val c = InterpolatedCurve(listOf(1.0 to 3.0, 5.0 to 4.0), "LINEAR")
        assertEquals(0.03, c.zeroRate(0.1), 1e-12, "최소 미만 → 첫 끝점")
        assertEquals(0.04, c.zeroRate(20.0), 1e-12, "최대 초과 → 마지막 끝점")
    }

    @Test
    fun `노드점 df 정합 + forward ↔ df 비율 정합`() {
        val c = InterpolatedCurve(listOf(1.0 to 3.0, 2.0 to 4.0, 3.0 to 4.5), "LINEAR")
        // 노드점: df(2) = 1/1.04^2
        assertEquals(1.0 / 1.04.pow(2.0), c.discountFactor(2.0), 1e-12)
        // forward(1,2) == (df1/df2)^(1/(2-1)) - 1
        val expected = c.discountFactor(1.0) / c.discountFactor(2.0) - 1.0
        assertEquals(expected, c.forwardRate(1.0, 2.0), 1e-12)
        assertTrue(c.warnings().isEmpty(), "정상 커브는 음의 forward 경고 없음")
    }

    @Test
    fun `역전 커브에서 음의 forward 경고`() {
        // 단기 10%, 장기 1% → df(1) < df(2) → forward 음수
        val c = InterpolatedCurve(listOf(1.0 to 10.0, 2.0 to 1.0), "LINEAR")
        val f = c.forwardRate(1.0, 2.0)
        assertTrue(f < 0.0, "역전 커브 forward 음수 기대: $f")
        assertTrue(c.warnings().any { it.contains("음의 forward") }, "음의 forward 경고 기록")
    }
}
