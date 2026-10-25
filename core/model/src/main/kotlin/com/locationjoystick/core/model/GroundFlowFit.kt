package com.locationjoystick.core.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/** Row-major 8-bit gray image (luma 0.299R + 0.587G + 0.114B), device pixels. */
class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixels must be width * height" }
    }
}

/** Content shift of one crop between two frames, device pixels; (cx, cy) is the crop centre. */
data class BandFlow(
    val fx: Double,
    val fy: Double,
    val response: Double,
    val cx: Double,
    val cy: Double,
)

/** Known ground move [d] of the player between two frames and the flow of every crop it caused. */
data class LegMove(
    val d: GroundOffset,
    val bands: List<BandFlow>,
)

/** Result of [GroundFlowFit.fit]: the calibration and how well it explains the flows. */
data class GroundFit(
    val calibration: GroundCalibration,
    /** Median absolute residual of the kept flows, device pixels. */
    val medianAbsPx: Double,
    val flowCount: Int,
)

/**
 * Ground map from the walks the app already makes: heading and lateral scale are measured from the flow
 * of a few small crops between two screenshots with a known mock move. The game camera's pitch is fixed,
 * so the forward scale and the perspective are constant ratios of the lateral scale (bench fits, see
 * docs/features/tap-to-walk.md, "Ground map: flow-measured"). Two free terms, so one straight walk pins them.
 */
object GroundFlowFit {
    // Bench fits with a detected feet anchor: forward / lateral 0.39 to 0.44, perspective / lateral -1.3e-4 to -2.5e-4.
    const val FORWARD_RATIO = 0.40
    const val PERSPECTIVE_RATIO = -2.2e-4 // 1/reference px

    // A fresh install walks under these until the first flow arrives; the bench headings ran -2 to -17 degrees.
    const val PROVISIONAL_HEADING_DEG = -10.0
    const val PROVISIONAL_LATERAL_PX_PER_M = 7.0

    // Feet as fractions of the screen: the game keeps the player centred; wide-zoom bench runs 0.635 to 0.648.
    const val FEET_X = 0.5
    const val FEET_Y = 0.6415

    // Crop centres as fractions of the screen: left and right of the player, and two rows further up. The player sprite is not in any.
    private val CROPS = listOf(0.25 to FEET_Y, 0.75 to FEET_Y, 0.25 to 0.47, 0.75 to 0.47, 0.5 to 0.40)
    private const val CROP_HALF = 0.12 // of the width
    private const val DOWNSAMPLE = 2
    private const val MIN_RESPONSE = 0.15 // weaker peaks are noise, not map content
    const val MIN_MOVE_M = 1.5 // shorter moves give flows too small to fit
    private const val MIN_FLOWS = 4
    private const val MIN_SCALE = 1.5 // px/m (reference): smaller is a static loading screen
    private const val MAX_SCALE = 30.0
    private const val MAX_MEDIAN_PX = 8.0
    private const val MIN_FLOW_PX = 1.0 // a crop that moved less is the same picture: a loading screen or a popup
    private const val STORE_MIN_FLOWS = 6
    private const val STORE_MAX_MEDIAN_PX = 4.0
    private const val AGREE_HEADING_DEG = 20.0
    private const val AGREE_SCALE_RATIO = 1.4
    private const val CHANGE_MIN_MOVES = 3 // a camera change must outlast one bad pair of screenshots
    private const val CHANGE_MIN_FLOWS = 10
    private const val CHANGE_MAX_MEDIAN_PX = 2.0
    private const val MAX_ITERS = 30
    private const val OUTLIER_SCALE = 3 * 1.4826
    private const val OUTLIER_FLOOR_PX = 2.0
    private const val REWEIGHT_FROM = 5
    private const val SCORE_CAP = 100.0
    private const val HORIZON_PX = 1e3 // residual for a crop the trial map puts past the horizon

    /** Ground calibration from the two measured terms, in reference pixels. */
    fun calibration(
        headingDeg: Double,
        lateralPxPerM: Double,
    ) = GroundCalibration(
        headingDeg,
        lateralPxPerM,
        FORWARD_RATIO * lateralPxPerM,
        PERSPECTIVE_RATIO * lateralPxPerM,
        FEET_X * GROUND_MAP_REFERENCE_WIDTH,
        FEET_Y * GROUND_MAP_REFERENCE_HEIGHT,
    )

    fun provisional() = calibration(PROVISIONAL_HEADING_DEG, PROVISIONAL_LATERAL_PX_PER_M)

    /** The crops of one screenshot, kept as spectra so each frame is transformed once. */
    class Crops internal constructor(
        val width: Int,
        val height: Int,
        internal val spectra: List<PhaseCorrelation.Spectrum>,
        internal val centres: List<Pair<Double, Double>>,
    )

    fun crops(frame: GrayImage): Crops {
        val w = frame.width
        val h = frame.height
        val half = (CROP_HALF * w).toInt()
        val side = (2 * half) / DOWNSAMPLE
        val centres = CROPS.map { (fx, fy) -> fx * w to fy * h }
        val spectra =
            centres.map { (cx, cy) ->
                val x0 = (cx - half).toInt().coerceIn(0, w - 2 * half)
                val y0 = (cy - half).toInt().coerceIn(0, h - 2 * half)
                val out = FloatArray(side * side)
                for (y in 0 until side) {
                    for (x in 0 until side) {
                        var sum = 0
                        for (dy in 0 until DOWNSAMPLE) {
                            val row = (y0 + y * DOWNSAMPLE + dy) * w + x0 + x * DOWNSAMPLE
                            for (dx in 0 until DOWNSAMPLE) sum += frame.pixels[row + dx].toInt() and 0xFF
                        }
                        out[y * side + x] = sum.toFloat() / (DOWNSAMPLE * DOWNSAMPLE)
                    }
                }
                PhaseCorrelation.spectrum(out, side, side)
            }
        return Crops(w, h, spectra, centres)
    }

    /** Crop flows from [a] to [b] for the ground move [d], or null when the frames differ in size or the move is too short. */
    fun move(
        a: Crops,
        b: Crops,
        d: GroundOffset,
    ): LegMove? {
        if (a.width != b.width || a.height != b.height || hypot(d.eastMeters, d.northMeters) < MIN_MOVE_M) return null
        val bands =
            a.spectra.indices.mapNotNull { i ->
                val f = PhaseCorrelation.correlate(a.spectra[i], b.spectra[i])
                if (f.response < MIN_RESPONSE) {
                    null
                } else {
                    BandFlow(f.fx * DOWNSAMPLE, f.fy * DOWNSAMPLE, f.response, a.centres[i].first, a.centres[i].second)
                }
            }
        return if (bands.any { hypot(it.fx, it.fy) >= MIN_FLOW_PX }) LegMove(d, bands) else null
    }

    /** Heading and lateral scale that best explain [moves] on a [width] x [height] screen, or null when nothing plausible fits. */
    fun fit(
        moves: List<LegMove>,
        width: Int,
        height: Int,
    ): GroundFit? {
        val flows = moves.flatMap { m -> m.bands.map { m.d to it } }
        if (flows.size < MIN_FLOWS) return null
        var best: Triple<Double, DoubleArray, DoubleArray>? = null
        for (start in -180 until 180 step 30) {
            val x = doubleArrayOf(start.toDouble(), PROVISIONAL_LATERAL_PX_PER_M)
            for (iter in 0 until MAX_ITERS) {
                val r = residuals(x, flows, width, height)
                val w =
                    if (iter < REWEIGHT_FROM) {
                        null
                    } else {
                        val c = maxOf(OUTLIER_SCALE * median(DoubleArray(r.size) { abs(r[it]) }), OUTLIER_FLOOR_PX)
                        DoubleArray(r.size) { if (abs(r[it]) < c) 1.0 else c / abs(r[it]) }
                    }
                val step = gaussNewtonStep(x, r, w, flows, width, height) ?: break
                x[0] += step[0]
                x[1] += step[1]
                if (!x[1].isFinite() || x[1] <= 0.0) break
                if (abs(step[0]) < 1e-6 && abs(step[1]) < 1e-6) break
            }
            if (!x[0].isFinite() || !x[1].isFinite() || x[1] <= 0.0) continue
            val r = residuals(x, flows, width, height)
            val score = sqrt(r.sumOf { min(it * it, SCORE_CAP) } / r.size)
            if (best == null || score < best.first) best = Triple(score, x, r)
        }
        val (_, x, r) = best ?: return null
        val med = median(DoubleArray(r.size) { abs(r[it]) })
        if (x[1] !in MIN_SCALE..MAX_SCALE || med > MAX_MEDIAN_PX) return null
        val heading = ((x[0] + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return GroundFit(calibration(heading, x[1]), med, flows.size)
    }

    /** Whether [fit], made from [moves] moves, may replace the [stored] map (null: none for this screen). */
    fun replaces(
        stored: GroundCalibration?,
        fit: GroundFit,
        moves: Int,
    ): Boolean {
        if (fit.flowCount < STORE_MIN_FLOWS || fit.medianAbsPx > STORE_MAX_MEDIAN_PX) return false
        if (stored == null) return true
        val gap = abs(fit.calibration.headingDeg - stored.headingDeg) % 360.0
        val ratio = fit.calibration.lateralPxPerM / stored.lateralPxPerM
        if (min(gap, 360.0 - gap) <= AGREE_HEADING_DEG && ratio in 1 / AGREE_SCALE_RATIO..AGREE_SCALE_RATIO) return true
        return moves >= CHANGE_MIN_MOVES && fit.flowCount >= CHANGE_MIN_FLOWS && fit.medianAbsPx <= CHANGE_MAX_MEDIAN_PX
    }

    /** Measured flow minus the flow the trial map predicts, interleaved (x, y) per crop. */
    private fun residuals(
        x: DoubleArray,
        flows: List<Pair<GroundOffset, BandFlow>>,
        width: Int,
        height: Int,
    ): DoubleArray {
        val map = GroundMap(calibration(x[0], x[1]), width.toDouble(), height.toDouble())
        val out = DoubleArray(flows.size * 2)
        for ((i, f) in flows.withIndex()) {
            val (d, b) = f
            val g = map.pixelToGround(b.cx, b.cy)
            // The world moves opposite to the player: the ground under the crop is d further behind after the move.
            val q = g?.let { map.groundToPixel(it.eastMeters - d.eastMeters, it.northMeters - d.northMeters) }
            val rx = if (q == null) HORIZON_PX else b.fx - (q.first - b.cx)
            val ry = if (q == null) HORIZON_PX else b.fy - (q.second - b.cy)
            out[2 * i] = if (rx.isFinite()) rx else HORIZON_PX
            out[2 * i + 1] = if (ry.isFinite()) ry else HORIZON_PX
        }
        return out
    }

    private fun gaussNewtonStep(
        x: DoubleArray,
        r: DoubleArray,
        w: DoubleArray?,
        flows: List<Pair<GroundOffset, BandFlow>>,
        width: Int,
        height: Int,
    ): DoubleArray? {
        val eps = doubleArrayOf(1e-3, 1e-4 * maxOf(1.0, x[1]))
        val jac =
            Array(2) { j ->
                val xe = x.copyOf().also { it[j] += eps[j] }
                val re = residuals(xe, flows, width, height)
                DoubleArray(r.size) { (re[it] - r[it]) / eps[j] }
            }
        var a00 = 0.0
        var a01 = 0.0
        var a11 = 0.0
        var b0 = 0.0
        var b1 = 0.0
        for (i in r.indices) {
            val ww = w?.get(i)?.let { it * it } ?: 1.0
            a00 += ww * jac[0][i] * jac[0][i]
            a01 += ww * jac[0][i] * jac[1][i]
            a11 += ww * jac[1][i] * jac[1][i]
            b0 -= ww * jac[0][i] * r[i]
            b1 -= ww * jac[1][i] * r[i]
        }
        val det = a00 * a11 - a01 * a01
        if (abs(det) < 1e-12) return null
        return doubleArrayOf((b0 * a11 - b1 * a01) / det, (a00 * b1 - a01 * b0) / det)
    }

    private fun median(v: DoubleArray): Double {
        v.sort()
        val n = v.size
        return if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2
    }
}
