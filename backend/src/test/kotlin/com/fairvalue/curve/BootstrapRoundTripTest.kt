package com.fairvalue.curve

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

/**
 * Bootstrapping round-trip(핵심) + 실제 보고서 커브 sanity check(느슨).
 * Spring 불필요(순수 계산 단위테스트).
 */
class BootstrapRoundTripTest {

    private fun curvesDir(): File =
        listOf("../golden-values/curves", "golden-values/curves")
            .map { File(it) }.firstOrNull { it.isDirectory }
            ?: error("golden-values/curves 디렉터리를 찾을 수 없습니다.")

    /** CSV(#메타·헤더 스킵) → (tenor, decimal rate). */
    private fun loadCsv(name: String): List<Pair<Double, Double>> {
        val pts = mutableListOf<Pair<Double, Double>>()
        for (raw in File(curvesDir(), name).readLines()) {
            val ln = raw.trim()
            if (ln.isEmpty() || ln.startsWith("#") || ln.lowercase().startsWith("tenor")) continue
            val c = ln.split(',')
            pts += c[0].trim().toDouble() to c[1].trim().toDouble() / 100.0
        }
        return pts
    }

    private fun maxRoundTripError(par: List<Pair<Double, Double>>): Double {
        val df = Bootstrapper.bootstrap(par)
        val par2 = Bootstrapper.parFromDf(df)
        return par.indices.maxOf { abs(par[it].second - par2[it].second) }
    }

    @Test
    fun `합성 par 커브 round-trip 은 1e-9 이내`() {
        val flat = listOf(0.5 to 0.05, 1.0 to 0.05, 2.0 to 0.05, 3.0 to 0.05, 5.0 to 0.05, 10.0 to 0.05)
        val upward = listOf(0.25 to 0.02, 1.0 to 0.025, 3.0 to 0.03, 5.0 to 0.034, 10.0 to 0.038)
        val inverted = listOf(0.5 to 0.05, 1.0 to 0.045, 2.0 to 0.04, 5.0 to 0.035, 10.0 to 0.03)
        for (c in listOf(flat, upward, inverted)) {
            assertTrue(maxRoundTripError(c) < 1e-9, "합성 round-trip 오차 초과: ${maxRoundTripError(c)}")
        }
    }

    @Test
    fun `실제 보고서 커브 6개 round-trip 은 1e-9 이내`() {
        val files = listOf(
            "risk_free_2022-10-13.csv", "risk_free_2023-12-31.csv", "risk_free_2024-12-31.csv",
            "credit_2022-10-13.csv", "credit_2023-12-31.csv", "credit_2024-12-31.csv",
        )
        for (f in files) {
            val err = maxRoundTripError(loadCsv(f))
            assertTrue(err < 1e-9, "$f round-trip 오차 초과: $err")
        }
    }

    @Test
    fun `실제 커브 sanity — df 범위 0 초과 1 이하, zero·forward 부호·범위 합리(정확값 미요구)`() {
        for (f in listOf("risk_free_2024-12-31.csv", "credit_2024-12-31.csv")) {
            val par = loadCsv(f)
            val df = Bootstrapper.bootstrap(par)
            df.forEach { (t, d) ->
                assertTrue(d > 0.0 && d <= 1.0001, "$f df($t)=$d 범위 비정상")
                val z = Bootstrapper.zeroFromDf(t, d)
                assertTrue(abs(z) < 1.0, "$f zero($t)=$z 범위 비정상(|z|<100%)")
                // df ↔ zero 정합: 1/(1+z)^t ≈ df
                assertTrue(abs(1.0 / (1.0 + z).pow(t) - d) < 1e-9, "$f df↔zero 불일치")
            }
        }
    }
}
