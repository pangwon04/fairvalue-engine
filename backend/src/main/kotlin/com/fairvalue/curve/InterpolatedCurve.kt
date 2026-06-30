package com.fairvalue.curve

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * 저장된 (tenor_years, rate_percent) 포인트로부터 zero/df/forward 를 계산한다.
 *
 * 복리 규약(고정): 이산 연율. zero rate z(decimal) 에 대해
 *     df(t) = 1 / (1 + z)^t          (t = 만기, 연 단위)
 * 입력 rate_percent 는 % 이며 내부에서 decimal(z = rate/100)로 환산한다.
 *
 * 보간(interpolation):
 *   - LINEAR     : zero rate 를 만기 구간에서 선형 보간.
 *   - LOG_LINEAR : ln(df) 를 선형 보간(= discount factor 로그선형).
 *   기본값은 LINEAR(method 가 비거나 미지원이면 LINEAR).
 *
 * 경계(외삽): t 가 최소 tenor 미만 / 최대 tenor 초과면 끝점 zero rate 를 고정하는
 *   평탄외삽(flat extrapolation). df 는 그 고정 zero 로 1/(1+z)^t 로 계산한다.
 *
 * forward: f(t1,t2) = (df(t1)/df(t2))^(1/(t2-t1)) - 1. 음수면 경고(warnings)에 기록(차단 아님).
 *
 * 계산 자료형: double. 노드점에서 두 보간법의 df 는 동일(df_i = 1/(1+z_i)^t_i).
 */
class InterpolatedCurve(
    points: List<Pair<Double, Double>>, // (tenor_years, rate_percent)
    method: String?,
) {
    private val ts: DoubleArray
    private val zs: DoubleArray        // decimal zero
    private val lndf: DoubleArray      // ln(df_i) = -t_i * ln(1+z_i)
    private val logLinear: Boolean
    private val warningsList = mutableListOf<String>()

    init {
        require(points.isNotEmpty()) { "커브 포인트가 비어 있습니다." }
        val sorted = points.sortedBy { it.first }
        ts = DoubleArray(sorted.size) { sorted[it].first }
        zs = DoubleArray(sorted.size) { sorted[it].second / 100.0 }
        lndf = DoubleArray(sorted.size) { -ts[it] * ln(1.0 + zs[it]) }
        logLinear = method?.trim()?.uppercase() == "LOG_LINEAR"
    }

    fun warnings(): List<String> = warningsList.toList()

    /** 구간 인덱스 i: ts[i] <= t < ts[i+1]. 범위 밖이면 -1(아래) / size-1(위). */
    private fun bracket(t: Double): Int {
        if (t <= ts.first()) return -1
        if (t >= ts.last()) return ts.size - 1
        var i = 0
        while (i < ts.size - 1 && !(t >= ts[i] && t < ts[i + 1])) i++
        return i
    }

    /** zero rate(decimal) — 평탄외삽 적용. */
    fun zeroRate(t: Double): Double {
        if (t <= ts.first()) return zs.first()
        if (t >= ts.last()) return zs.last()
        return if (logLinear) {
            val df = discountFactor(t)
            (1.0 / df).pow(1.0 / t) - 1.0
        } else {
            val i = bracket(t)
            linear(t, ts[i], zs[i], ts[i + 1], zs[i + 1])
        }
    }

    /** discount factor — 평탄외삽 적용. */
    fun discountFactor(t: Double): Double {
        if (t <= ts.first() || t >= ts.last()) {
            val z = if (t <= ts.first()) zs.first() else zs.last()
            return 1.0 / (1.0 + z).pow(t)
        }
        return if (logLinear) {
            val i = bracket(t)
            exp(linear(t, ts[i], lndf[i], ts[i + 1], lndf[i + 1]))
        } else {
            1.0 / (1.0 + zeroRate(t)).pow(t)
        }
    }

    /** forward rate(decimal) t1→t2 (t2>t1). 음수면 경고 기록. */
    fun forwardRate(t1: Double, t2: Double): Double {
        require(t2 > t1) { "t2 는 t1 보다 커야 합니다." }
        val f = (discountFactor(t1) / discountFactor(t2)).pow(1.0 / (t2 - t1)) - 1.0
        if (f < 0.0) warningsList.add("음의 forward rate 발생: f($t1,$t2)=${"%.6f".format(f)}")
        return f
    }

    private fun linear(t: Double, x0: Double, y0: Double, x1: Double, y1: Double): Double =
        y0 + (y1 - y0) * (t - x0) / (x1 - x0)
}
