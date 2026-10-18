package com.locationjoystick.core.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Result of [GroundMapFit.fit]: the calibration and how well it explains the flows. */
data class GroundFit(
    val calibration: GroundCalibration,
    /** Median absolute residual of the kept flows, reference pixels. */
    val medianAbsPx: Double,
    /** Band flows (response >= floor, below the horizon) the fit used. */
    val flowCount: Int,
)

/**
 * Least-squares fit of the pitched-camera ground map to band flows. Port of
 * `registration_bench.fit_perspective` / `fit_perspective_horizon`. Works in 1080x2340 reference pixels.
 */
object GroundMapFit {
    private const val MIN_RESPONSE = 0.2 // flows below this are noise, not content
    private const val MIN_FLOWS = 40 // fewer flows cannot pin four parameters robustly
    private const val MIN_MOVE_FLOWS = 3 // a single move's median needs this many bands
    private const val OUTLIER_SCALE = 3 * 1.4826 // 3 MAD, MAD scaled to a standard deviation
    private const val OUTLIER_FLOOR_PX = 2.0 // cut never tighter than this (sub-pixel flow noise)
    private val REWEIGHT_AT = setOf(8, 20, 30) // Gauss-Newton iterations that recompute outlier weights
    private const val MAX_ITERS = 40
    private const val STEP_TOL = 1e-7
    private const val JAC_STEP = 1e-4 // relative forward-difference step
    private const val SCORE_CAP = 100.0 // squared px, caps an outlier's pull on the start ranking
    private const val HORIZON_MARGIN = 0.03 // of the height: bands this close under the horizon row are dropped too
    private val STARTS_DEG = doubleArrayOf(-20.0, 5.0, 30.0, -60.0, 60.0, 120.0, 180.0)
    private val START_REST = doubleArrayOf(7.0, 3.0, -0.0024) // lateral, forward px/m, perspective 1/m

    /**
     * @param moves from [FrameFlow.bandMoves], device pixels; [feetX]/[feetY] device pixels of the screen
     * [width] x [height]. Returns null under 40 usable flows or when no start converges.
     */
    fun fit(
        moves: List<LegMove>,
        feetX: Double,
        feetY: Double,
        width: Int,
        height: Int,
    ): GroundFit? {
        require(width > 0 && height > 0) { "screen size must be positive" }
        val flows = moves.flatMap { flowsOf(it, width, height) }
        val px = feetX * GROUND_MAP_REFERENCE_WIDTH / width
        val py = feetY * GROUND_MAP_REFERENCE_HEIGHT / height
        val first = solve(flows, px, py) ?: return null
        val hz = if (first.a != 0.0) first.sy / first.a else Double.NaN
        val refit =
            if (hz >= 0.0 && hz < GROUND_MAP_REFERENCE_HEIGHT) {
                val cut = hz + HORIZON_MARGIN * GROUND_MAP_REFERENCE_HEIGHT
                solve(flows.filter { it.cy > cut }, px, py)
            } else {
                null
            }
        val best = refit ?: first
        val cal = GroundCalibration(best.theta, best.s, best.sy, best.a, px, py)
        return GroundFit(cal, best.medianAbs, best.n)
    }

    /**
     * Median absolute residual (reference px, both components, the measure of [GroundFit.medianAbsPx]) of each move's
     * kept flows under [cal], null for a move with fewer than 3 usable bands; order follows [moves].
     */
    fun moveResidualsPx(
        cal: GroundCalibration,
        moves: List<LegMove>,
        width: Int,
        height: Int,
    ): List<Double?> =
        moves.map { m ->
            val rows = flowsOf(m, width, height).filter { it.response >= MIN_RESPONSE }
            if (rows.size < MIN_MOVE_FLOWS) {
                null
            } else {
                medianAbs(residuals(params(cal), rows, cal.feetX, cal.feetY))
            }
        }

    private fun params(cal: GroundCalibration) = doubleArrayOf(cal.headingDeg, cal.lateralPxPerM, cal.forwardPxPerM, cal.perspective)

    private fun flowsOf(
        m: LegMove,
        width: Int,
        height: Int,
    ): List<Flow> {
        val kx = GROUND_MAP_REFERENCE_WIDTH / width
        val ky = GROUND_MAP_REFERENCE_HEIGHT / height
        return m.bands.map { b -> Flow(m.d.eastMeters, m.d.northMeters, b.fx * kx, b.fy * ky, b.cx * kx, b.cy * ky, b.response) }
    }

    private class Flow(
        val dE: Double,
        val dN: Double,
        val fx: Double,
        val fy: Double,
        val cx: Double,
        val cy: Double,
        val response: Double,
    )

    private class Solution(
        val theta: Double,
        val s: Double,
        val sy: Double,
        val a: Double,
        val medianAbs: Double,
        val n: Int,
    )

    private fun solve(
        all: List<Flow>,
        px: Double,
        py: Double,
    ): Solution? {
        val rows = all.filter { it.response >= MIN_RESPONSE }
        if (rows.size < MIN_FLOWS) return null
        val m = rows.size * 2
        var best: DoubleArray? = null
        var bestScore = Double.POSITIVE_INFINITY
        for (th in STARTS_DEG) {
            val x = doubleArrayOf(th, START_REST[0], START_REST[1], START_REST[2])
            var w: DoubleArray? = null
            for (iter in 0 until MAX_ITERS) {
                val r = residuals(x, rows, px, py)
                val jac =
                    Array(4) { j ->
                        val e = JAC_STEP * maxOf(1.0, abs(x[j]))
                        val xe = x.copyOf().also { v -> v[j] += e }
                        val re = residuals(xe, rows, px, py)
                        DoubleArray(m) { i -> (re[i] - r[i]) / e }
                    }
                if (iter in REWEIGHT_AT) {
                    val c = maxOf(OUTLIER_SCALE * median(DoubleArray(m) { i -> abs(r[i]) }), OUTLIER_FLOOR_PX)
                    w = DoubleArray(m) { i -> if (abs(r[i]) < c) 1.0 else c / abs(r[i]) }
                }
                val dx = normalStep(jac, r, w) ?: break
                for (j in 0 until 4) x[j] += dx[j]
                if (dx.maxOf { abs(it) } < STEP_TOL) break
            }
            val r = residuals(x, rows, px, py)
            val score = sqrt(r.sumOf { min(it * it, SCORE_CAP) } / m)
            if (score < bestScore) {
                bestScore = score
                best = x
            }
        }
        val x = best ?: return null
        val r = residuals(x, rows, px, py)
        // (s, sy, a, theta) and (-s, -sy, -a, theta + 180) are the same homography: report the positive-scale one
        val flip = x[1] < 0.0
        val sign = if (flip) -1.0 else 1.0
        val theta = ((x[0] + if (flip) 360.0 else 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return Solution(theta, sign * x[1], sign * x[2], sign * x[3], median(DoubleArray(m) { abs(r[it]) }), rows.size)
    }

    /** Flow minus predicted flow, interleaved (x, y) per row. Non-finite predictions (at the horizon) count as 1e3 px. */
    private fun residuals(
        x: DoubleArray,
        rows: List<Flow>,
        px: Double,
        py: Double,
    ): DoubleArray {
        val t = Math.toRadians(x[0])
        val c = cos(t)
        val sn = sin(t)
        val (ls, fs, a) = Triple(x[1], x[2], x[3])
        // bench ground_to_game: camH(s, sy, a, p) * rot(theta)
        val h = doubleArrayOf(ls * c, -ls * sn, px, -fs * sn, -fs * c, py, -a * sn, -a * c, 1.0)
        val hi =
            doubleArrayOf(
                h[4] * h[8] - h[5] * h[7],
                h[2] * h[7] - h[1] * h[8],
                h[1] * h[5] - h[2] * h[4],
                h[5] * h[6] - h[3] * h[8],
                h[0] * h[8] - h[2] * h[6],
                h[2] * h[3] - h[0] * h[5],
                h[3] * h[7] - h[4] * h[6],
                h[1] * h[6] - h[0] * h[7],
                h[0] * h[4] - h[1] * h[3],
            )
        val out = DoubleArray(rows.size * 2)
        for ((i, f) in rows.withIndex()) {
            val wi = hi[6] * f.cx + hi[7] * f.cy + hi[8]
            val gx = (hi[0] * f.cx + hi[1] * f.cy + hi[2]) / wi - f.dE
            val gy = (hi[3] * f.cx + hi[4] * f.cy + hi[5]) / wi - f.dN
            val wh = h[6] * gx + h[7] * gy + h[8]
            val qx = (h[0] * gx + h[1] * gy + h[2]) / wh
            val qy = (h[3] * gx + h[4] * gy + h[5]) / wh
            val rx = f.fx - (qx - f.cx)
            val ry = f.fy - (qy - f.cy)
            out[2 * i] = if (rx.isFinite()) rx else 1e3
            out[2 * i + 1] = if (ry.isFinite()) ry else 1e3
        }
        return out
    }

    /** Weighted least-squares step -r ~ J dx via the 4x4 normal equations; null when singular. */
    private fun normalStep(
        jac: Array<DoubleArray>,
        r: DoubleArray,
        w: DoubleArray?,
    ): DoubleArray? {
        val a = Array(4) { DoubleArray(5) }
        for (i in r.indices) {
            val ww = w?.get(i)?.let { it * it } ?: 1.0
            for (p in 0 until 4) {
                for (q in 0 until 4) a[p][q] += ww * jac[p][i] * jac[q][i]
                a[p][4] -= ww * jac[p][i] * r[i]
            }
        }
        for (col in 0 until 4) {
            val piv = (col until 4).maxBy { abs(a[it][col]) }
            if (abs(a[piv][col]) < 1e-12) return null
            val tmp = a[col]
            a[col] = a[piv]
            a[piv] = tmp
            for (row in col + 1 until 4) {
                val f = a[row][col] / a[col][col]
                for (k in col until 5) a[row][k] -= f * a[col][k]
            }
        }
        val x = DoubleArray(4)
        for (row in 3 downTo 0) {
            var s = a[row][4]
            for (k in row + 1 until 4) s -= a[row][k] * x[k]
            x[row] = s / a[row][row]
        }
        return x
    }

    private fun medianAbs(v: DoubleArray): Double = median(DoubleArray(v.size) { abs(v[it]) })

    private fun median(v: DoubleArray): Double {
        v.sort()
        val n = v.size
        return if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2
    }
}
