package com.fairvalue.curve

import kotlin.math.pow

/**
 * par yield curve → zero/discount factor 부트스트랩 (순수 함수, 메모리 계산).
 *
 * 가정(명시):
 *   - 액면 1, par 채권(가격 = 1).
 *   - 그리드 tenor 에서 쿠폰이 발생하고, 쿠폰액 = par(연율) × Δt_j (Δt_j = t_j - t_{j-1}, t_0 = 0).
 *     불규칙 그리드(0.25, 0.5, 1.5 ...)에 대응하는 accrual(단리 누적) 방식.
 *   - 이산 연율 zero: z_i = (1/df_i)^(1/t_i) - 1.
 *
 * 부트스트랩(par → df, 순차):
 *     df_i = (1 - c_i · Σ_{j<i} Δt_j·df_j) / (1 + c_i · Δt_i)
 * 역산(df → par, round-trip):
 *     par_i = (1 - df_i) / (Σ_{j≤i} Δt_j·df_j)
 * 역산이 부트스트랩의 대수적 역함수이므로 round-trip 은 수치한계(≈1e-15)까지 par 를 복원한다.
 *
 * 입력/출력 rate 는 decimal(예: 0.0355 = 3.55%).
 */
object Bootstrapper {

    /** par(decimal) → df. 입력은 만기 오름차순(아니면 정렬). */
    fun bootstrap(par: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val pts = par.sortedBy { it.first }
        val ts = pts.map { it.first }
        val dfs = DoubleArray(pts.size)
        for (i in pts.indices) {
            val c = pts[i].second
            val dtI = ts[i] - (if (i > 0) ts[i - 1] else 0.0)
            var acc = 0.0
            for (j in 0 until i) {
                val dtJ = ts[j] - (if (j > 0) ts[j - 1] else 0.0)
                acc += dtJ * dfs[j]
            }
            dfs[i] = (1.0 - c * acc) / (1.0 + c * dtI)
        }
        return ts.mapIndexed { i, t -> t to dfs[i] }
    }

    /** df → par(decimal). bootstrap 의 역산(round-trip 검증용). */
    fun parFromDf(dfPoints: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val pts = dfPoints.sortedBy { it.first }
        val ts = pts.map { it.first }
        val out = ArrayList<Pair<Double, Double>>(pts.size)
        for (i in pts.indices) {
            var annuity = 0.0
            for (j in 0..i) {
                val dtJ = ts[j] - (if (j > 0) ts[j - 1] else 0.0)
                annuity += dtJ * pts[j].second
            }
            out.add(ts[i] to (1.0 - pts[i].second) / annuity)
        }
        return out
    }

    /** df → 이산 연율 zero(decimal). */
    fun zeroFromDf(t: Double, df: Double): Double = (1.0 / df).pow(1.0 / t) - 1.0
}
