package com.locationjoystick.core.model

/** Whether the stored ground map still matches the game camera. `Suspect`: screen motion stopped matching it. */
sealed interface FitHealth {
    /** No stored fit for this screen. */
    data object None : FitHealth

    data object Valid : FitHealth

    data object Suspect : FitHealth
}

/**
 * Detects a camera twist or zoom from the flow residual of a walked leg (research "Recommendations for
 * entry A", docs/features/tap-to-walk.md "Automatic fit"). Pure CUSUM; the residuals come from
 * [GroundMapFit.moveResidualsPx]. The constants were tuned on `registration_bench.py monitor` runs
 * (docs/features/tap-to-walk-investigation.md, "Automatic fit bench results"); the module is pure JVM, so they live here.
 * It only detects: a heading-only re-fit was benched and killed (heading off by more than 3 degrees), so any alarm
 * means a full re-fit.
 */
object FitMonitor {
    /** CUSUM drift allowance, in multiples of the stored fit's median residual. */
    const val DRIFT = 0.25

    /** CUSUM alarm level, same unit. */
    const val ALARM = 8.0

    /**
     * Index of the first move whose accumulated excess residual passes [alarm], or null. A move's excess is
     * its residual over [baseMedianPx] minus one (zero when nothing changed) minus [drift].
     */
    fun alarmIndex(
        residualsPx: List<Double?>,
        baseMedianPx: Double,
        drift: Double = DRIFT,
        alarm: Double = ALARM,
    ): Int? {
        require(baseMedianPx > 0.0) { "stored median residual must be positive" }
        var s = 0.0
        for ((i, r) in residualsPx.withIndex()) {
            if (r == null) continue
            s = maxOf(0.0, s + r / baseMedianPx - 1.0 - drift)
            if (s > alarm) return i
        }
        return null
    }
}
