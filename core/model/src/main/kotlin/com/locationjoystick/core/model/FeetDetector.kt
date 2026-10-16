package com.locationjoystick.core.model

import kotlin.math.abs
import kotlin.math.hypot

/** Row-major gray frame, values 0..255, `width * height` floats (size is passed to [FeetDetector.detect]). */
class GrayFrame(
    val pixels: FloatArray,
)

/** Player feet pixel in device pixels (x right, y down). */
data class FeetPixel(
    val x: Double,
    val y: Double,
) {
    /** Same pixel in the 1080x2340 reference frame that [GroundCalibration.feetX]/`feetY` use. */
    fun toReference(
        width: Int,
        height: Int,
    ): Pair<Double, Double> = x * GROUND_MAP_REFERENCE_WIDTH / width to y * GROUND_MAP_REFERENCE_HEIGHT / height
}

/**
 * Finds the player's feet pixel from walked-leg frames alone. Port of `registration_bench.detect_feet`:
 * the world scrolls under a player that stays put, so only the player (and HUD) stay sharp in a leg's
 * temporal median. Constants match the bench; goldens pin them (`scripts/export_feet_golden.py`).
 * Every size is a fraction of the frame, so any screen size or density works.
 */
object FeetDetector {
    private const val EDGE_THRESHOLD = 30.0 // gradient magnitude of the min-over-legs median that counts as sharp
    private const val GAP_FRACTION = 0.05 // row gap (of height) that still joins a run: dark legs break the figure
    private const val COL_LO = 0.42 // centre column band, fraction of width
    private const val COL_HI = 0.58
    private const val ROW_LO = 0.4 // search rows, fraction of height
    private const val ROW_HI = 0.9
    private const val MIN_SUPPORT = 0.0005 // min summed run support per (height * kernel)
    private const val TAIL_FRACTION = 0.1 // drop run rows below this share of the strongest row
    private const val LIFT = 0.07 // ground contact sits 7% of figure height above the last sharp row (tuned)
    private const val MIN_FRAMES = 3 // a leg needs this many moving frames

    // Feet row as a fraction of height (wide-zoom runs: 0.635-0.648). A row further than PRIOR_TOLERANCE is the
    // screen-fixed range ring merged into the figure's run: use the prior instead. Mirrors registration_bench.py.
    private const val PRIOR_FEET_FRAC = 0.6415
    private const val PRIOR_TOLERANCE = 0.03

    /**
     * @param legs one list per walked leg, only frames recorded while moving, each `width * height`.
     * @return the feet pixel, or null when no figure is found (caller: calibration failed, store nothing).
     */
    fun detect(
        legs: List<List<GrayFrame>>,
        width: Int,
        height: Int,
    ): FeetPixel? {
        require(width >= 2 && height >= 2) { "frame size must be at least 2x2" }
        val grads =
            legs.filter { it.size >= MIN_FRAMES }.map { fr ->
                gradient(fr.size, width, height) { f, i -> fr[f].pixels[i] }
            }
        return detectFromGradients(grads, width, height)
    }

    /**
     * Incremental half of [detect]: the gradient of one leg's temporal median from 8-bit frames, so a caller
     * can keep one 10 MB gradient per leg instead of every frame. Null under 3 frames (leg unusable).
     */
    fun legGradient(frames: List<GrayImage>): FloatArray? {
        if (frames.size < MIN_FRAMES) return null
        val w = frames[0].width
        val h = frames[0].height
        require(w >= 2 && h >= 2) { "frame size must be at least 2x2" }
        return gradient(frames.size, w, h) { f, i -> (frames[f].pixels[i].toInt() and 0xFF).toFloat() }
    }

    /** Second half of [detect]: the feet pixel from per-leg gradients ([legGradient]), or null. */
    fun detectFromGradients(
        grads: List<FloatArray>,
        width: Int,
        height: Int,
    ): FeetPixel? {
        require(width >= 2 && height >= 2) { "frame size must be at least 2x2" }
        if (grads.isEmpty()) return null
        val n = width * height
        val edge = BooleanArray(n) { i -> grads.minOf { it[i] } > EDGE_THRESHOLD }

        val x0 = (COL_LO * width).toInt()
        val x1 = (COL_HI * width).toInt()
        if (x1 <= x0) return null
        val k = maxOf(3, (0.005 * height).toInt())
        val colMean = DoubleArray(height) { y -> (x0 until x1).count { edge[y * width + it] }.toDouble() / (x1 - x0) }
        // np.convolve(colMean, ones(k), "same"): out[i] = sum_{j<k} colMean[i + (k-1)/2 - j]
        val s = (k - 1) / 2
        val rows = DoubleArray(height) { i -> (0 until k).sumOf { j -> colMean.getOrElse(i + s - j) { 0.0 } } }

        val ys = (ROW_LO * height).toInt().until((ROW_HI * height).toInt()).filter { rows[it] > 0.0 }
        if (ys.isEmpty()) return null
        val runs = mutableListOf<MutableList<Int>>()
        var cur = mutableListOf(ys[0])
        for (y in ys.drop(1)) {
            if (y - cur.last() > GAP_FRACTION * height) {
                runs += cur
                cur = mutableListOf()
            }
            cur += y
        }
        runs += cur
        val best = runs.maxBy { r -> r.sumOf { rows[it] } }
        if (best.sumOf { rows[it] } < MIN_SUPPORT * height * k) return null
        val peak = best.maxOf { rows[it] }
        val run = best.filter { rows[it] >= TAIL_FRACTION * peak }
        val first = run.first()
        val last = run.last()
        var sum = 0.0
        var count = 0
        for (y in first..last) {
            for (x in x0 until x1) {
                if (edge[y * width + x]) {
                    sum += x - x0
                    count++
                }
            }
        }
        if (count == 0) return null
        val feetY = last - LIFT * (last - first)
        val y = if (abs(feetY / height - PRIOR_FEET_FRAC) > PRIOR_TOLERANCE) PRIOR_FEET_FRAC * height else feetY
        return FeetPixel(x0 + sum / count, y)
    }

    /** Gradient magnitude of the blurred per-pixel temporal median of one leg. */
    private inline fun gradient(
        m: Int,
        w: Int,
        h: Int,
        pixel: (frame: Int, index: Int) -> Float,
    ): FloatArray {
        val med = FloatArray(w * h)
        val col = FloatArray(m)
        for (i in med.indices) {
            for (f in 0 until m) col[f] = pixel(f, i)
            col.sort()
            med[i] = if (m % 2 == 1) col[m / 2] else (col[m / 2 - 1] + col[m / 2]) / 2f
        }
        val g = conv3(med, w, h, 1f, 2f, 1f, 1f, 2f, 1f, 16f)
        val gx = conv3(g, w, h, -1f, 0f, 1f, 1f, 2f, 1f, 1f)
        val gy = conv3(g, w, h, 1f, 2f, 1f, -1f, 0f, 1f, 1f)
        return FloatArray(w * h) { hypot(gx[it], gy[it]) }
    }

    /** Separable 3-tap filter (x taps a0..a2, y taps b0..b2, divided by [div]), reflect-101 borders. */
    private fun conv3(
        src: FloatArray,
        w: Int,
        h: Int,
        a0: Float,
        a1: Float,
        a2: Float,
        b0: Float,
        b1: Float,
        b2: Float,
        div: Float,
    ): FloatArray {
        fun r(
            i: Int,
            n: Int,
        ) = if (i < 0) {
            -i
        } else if (i >= n) {
            2 * (n - 1) - i
        } else {
            i
        }
        val tmp = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                tmp[y * w + x] =
                    a0 * src[y * w + r(x - 1, w)] + a1 * src[y * w + x] + a2 * src[y * w + r(x + 1, w)]
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[y * w + x] =
                    (b0 * tmp[r(y - 1, h) * w + x] + b1 * tmp[y * w + x] + b2 * tmp[r(y + 1, h) * w + x]) / div
            }
        }
        return out
    }
}
