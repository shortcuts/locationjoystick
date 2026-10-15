package com.locationjoystick.core.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
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

/**
 * One walked leg: gray frames in capture order, whether the player was moving at each frame, and each
 * frame's mock position as metres east/north of any fixed origin (only differences are used).
 */
class WalkLeg(
    val frames: List<GrayImage>,
    val moving: List<Boolean>,
    val offsets: List<GroundOffset>,
) {
    init {
        require(frames.isNotEmpty() && frames.size == moving.size && frames.size == offsets.size) { "one entry per frame" }
        require(frames.all { it.width == frames[0].width && it.height == frames[0].height }) { "frames must share a size" }
    }
}

/** Content shift of one band between two frames, device pixels; (cx, cy) is the band centre. */
data class BandFlow(
    val fx: Double,
    val fy: Double,
    val response: Double,
    val cx: Double,
    val cy: Double,
)

/** Known ground move [d] of the player between two frames and the flow of every band it caused. */
data class LegMove(
    val d: GroundOffset,
    val bands: List<BandFlow>,
)

/**
 * Per-band content flow between frames of a walked leg. Port of `registration_bench.clean_leg` and
 * `band_moves` (see docs/features/tap-to-walk.md, "Frame flow and ground fit"). Pixel sizes are scaled by
 * `width / 1080` and rows are fractions of the height, so any screen size works; at 1080x2340 the numbers
 * equal the bench.
 */
object FrameFlow {
    private const val CLEAN_ROW_LO = 700.0 // reference rows (of 2340) cleaned: below the HUD, above the bottom bar
    private const val CLEAN_ROW_HI = 1950.0
    private const val STATIC_DEV_MAX = 3.0 // temporal deviation under this = unchanged over the leg
    private const val STATIC_EDGE_MIN = 60.0 // and on an edge of the temporal median
    private const val DILATE = 9.0 // px, dilation of the static mask
    private const val SPRITE_RADIUS = 110.0 // px, player disc
    private const val SPRITE_UP = 60.0 // px, disc centre above the feet pixel
    private const val SIGMA_LOCAL = 6.0 // px, removes the low-frequency ground colour
    private const val SIGMA_ENERGY = 10.0 // px, local contrast normalisation
    private const val ENERGY_FLOOR = 4.0 // gray levels, keeps flat areas from being amplified
    private const val SIGMA_OUT = 1.5 // px, final smoothing
    private const val FILL_SWEEPS = 40 // smoothing sweeps of the fill; 10 left a static streak pattern that biased flow (measured)
    private const val MIN_MOVE_M = 2.0 // shorter moves give flows too small to fit
    private const val MIN_UNMASKED = 0.5 // bands with less unmasked area than this get response 0
    private const val ROW_START = 0.21 // band grid, fractions of the frame: 7 rows over 0.21h..0.70h
    private const val ROW_SPAN = 0.49
    private const val ROW_COUNT = 7
    private val COLS = listOf(0.0 to 1.0 / 3, 1.0 / 3 to 2.0 / 3, 2.0 / 3 to 1.0)
    private val DEFAULT_KS = listOf(2, 3, 4, 6)

    /** Frames with leg-static content (HUD, ring, companion) and the player disc filled in, same size. */
    fun cleanLeg(
        frames: List<GrayImage>,
        feetX: Double,
        feetY: Double,
    ): List<GrayImage> {
        require(frames.isNotEmpty()) { "no frames" }
        val w = frames[0].width
        val h = frames[0].height
        val s = w / GROUND_MAP_REFERENCE_WIDTH
        val y0 = rowOf(CLEAN_ROW_LO, h)
        val y1 = rowOf(CLEAN_ROW_HI, h)
        if (y1 <= y0) return frames
        val r = (odd(DILATE * s) / 2)
        val ys0 = maxOf(0, y0 - r - 1)
        val ys1 = minOf(h, y1 + r + 1)
        val rh = ys1 - ys0

        val med = FloatArray(rh * w)
        val dev = FloatArray(rh * w)
        val col = FloatArray(frames.size)
        for (i in med.indices) {
            val idx = ys0 * w + i
            for (f in frames.indices) col[f] = (frames[f].pixels[idx].toInt() and 0xFF).toFloat()
            val m = median(col)
            for (f in col.indices) col[f] = abs(col[f] - m)
            med[i] = m
            dev[i] = median(col)
        }
        val stat = BooleanArray(rh * w)
        val gx = sobel(med, w, rh, true)
        val gy = sobel(med, w, rh, false)
        for (i in stat.indices) stat[i] = dev[i] < STATIC_DEV_MAX && hypot(gx[i], gy[i]) > STATIC_EDGE_MIN
        val dil = dilate(stat, w, rh, r)

        val sh = y1 - y0
        val mask = BooleanArray(sh * w)
        val cx = feetX.toInt()
        val cy = feetY.toInt() - Math.rint(SPRITE_UP * s).toInt()
        val rad = Math.rint(SPRITE_RADIUS * s).toLong()
        for (y in 0 until sh) {
            for (x in 0 until w) {
                val dx = (x - cx).toLong()
                val dy = (y + y0 - cy).toLong()
                mask[y * w + x] = dil[(y + y0 - ys0) * w + x] || dx * dx + dy * dy <= rad * rad
            }
        }
        val (order, wave) = fillOrder(mask, w, sh)
        return frames.map { f ->
            val out = f.pixels.copyOf()
            val base = y0 * w
            val v = FloatArray(sh * w) { (out[base + it].toInt() and 0xFF).toFloat() }
            val nb = IntArray(4)
            // pass 1: ring by ring from the mask boundary, mean of the already-valid 4-neighbours
            for (p in order) {
                var sum = 0f
                var n = 0
                for (i in 0 until neighbours(p, w, sh, nb)) {
                    if (wave[nb[i]] < wave[p]) {
                        sum += v[nb[i]]
                        n++
                    }
                }
                v[p] = sum / n
            }
            // pass 2: Gauss-Seidel sweeps over all 4 neighbours smooth the streaks pass 1 copies inward
            repeat(FILL_SWEEPS) {
                for (p in order) {
                    var sum = 0f
                    val n = neighbours(p, w, sh, nb)
                    for (i in 0 until n) sum += v[nb[i]]
                    v[p] = sum / n
                }
            }
            for (p in order) out[base + p] = Math.round(v[p]).coerceIn(0, 255).toByte()
            GrayImage(w, h, out)
        }
    }

    /**
     * Flow of every band for every frame pair `(i, i + k)`, `k` in [ks], with both frames moving and a known
     * move of at least 2 m. Frames are cleaned first ([cleanLeg]); [feetX]/[feetY] are device pixels.
     * A band overlapping the player disc has its response scaled by its unmasked fraction and zeroed below 0.5.
     */
    fun bandMoves(
        legs: List<WalkLeg>,
        feetX: Double,
        feetY: Double,
        ks: List<Int> = DEFAULT_KS,
    ): List<LegMove> {
        val out = mutableListOf<LegMove>()
        for (leg in legs) {
            val w = leg.frames[0].width
            val h = leg.frames[0].height
            val s = w / GROUND_MAP_REFERENCE_WIDTH
            val pairs = mutableListOf<Triple<Int, Int, GroundOffset>>()
            for (k in ks) {
                for (i in 0 until leg.frames.size - k) {
                    if (!(leg.moving[i] && leg.moving[i + k])) continue
                    val d =
                        GroundOffset(
                            leg.offsets[i + k].eastMeters - leg.offsets[i].eastMeters,
                            leg.offsets[i + k].northMeters - leg.offsets[i].northMeters,
                        )
                    if (hypot(d.eastMeters, d.northMeters) < MIN_MOVE_M) continue
                    pairs += Triple(i, i + k, d)
                }
            }
            if (pairs.isEmpty()) continue
            val bands = bandRects(w, h)
            val clean = cleanLeg(leg.frames, feetX, feetY)
            val cx = feetX.toInt()
            val cy = feetY.toInt() - Math.rint(SPRITE_UP * s).toInt()
            val rad = Math.rint(SPRITE_RADIUS * s).toLong()
            val flows = Array(pairs.size) { ArrayList<BandFlow>(bands.size) }
            for (b in bands) {
                val (x0, y0, x1, y1) = b
                val bw = x1 - x0
                val bh = y1 - y0
                var inside = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val dx = (x - cx).toLong()
                        val dy = (y - cy).toLong()
                        if (dx * dx + dy * dy <= rad * rad) inside++
                    }
                }
                val frac = 1.0 - inside.toDouble() / (bw * bh)
                val weight = if (frac < MIN_UNMASKED) 0.0 else frac
                val spectra = arrayOfNulls<PhaseCorrelation.Spectrum>(clean.size)

                fun spec(f: Int) =
                    spectra[f] ?: PhaseCorrelation.spectrum(feature(clean[f], x0, y0, bw, bh, s), bw, bh).also { spectra[f] = it }
                for ((pi, p) in pairs.withIndex()) {
                    val r = PhaseCorrelation.correlate(spec(p.first), spec(p.second))
                    flows[pi] += BandFlow(r.fx, r.fy, r.response * weight, (x0 + x1) / 2.0, (y0 + y1) / 2.0)
                }
            }
            // bench order: bands row-major per pair; `flows` is already per pair in band order
            for ((pi, p) in pairs.withIndex()) out += LegMove(p.third, flows[pi])
        }
        return out
    }

    private data class Rect(
        val x0: Int,
        val y0: Int,
        val x1: Int,
        val y1: Int,
    )

    private fun bandRects(
        w: Int,
        h: Int,
    ): List<Rect> =
        (0 until ROW_COUNT).flatMap { i ->
            val ya = ROW_START + i * ROW_SPAN / ROW_COUNT
            val yb = ROW_START + (i + 1) * ROW_SPAN / ROW_COUNT
            COLS.map { (xa, xb) -> Rect((xa * w).toInt(), (ya * h).toInt(), (xb * w).toInt(), (yb * h).toInt()) }
        }

    /** Bench `_feat` on a crop: local-contrast luminance, blurred for coarse alignment. */
    private fun feature(
        img: GrayImage,
        x0: Int,
        y0: Int,
        w: Int,
        h: Int,
        s: Double,
    ): FloatArray {
        val g = FloatArray(w * h) { (img.pixels[(y0 + it / w) * img.width + x0 + it % w].toInt() and 0xFF).toFloat() }
        val low = blur(g, w, h, SIGMA_LOCAL * s)
        val d = FloatArray(g.size) { g[it] - low[it] }
        val energy = blur(FloatArray(d.size) { d[it] * d[it] }, w, h, SIGMA_ENERGY * s)
        val n = FloatArray(d.size) { d[it] / (sqrt(energy[it].toDouble()).toFloat() + ENERGY_FLOOR.toFloat()) }
        return blur(n, w, h, SIGMA_OUT * s)
    }

    /** Separable Gaussian, `cv2.GaussianBlur(img, (0, 0), sigma)` for float32: size round(8 sigma + 1) | 1, reflect-101. */
    private fun blur(
        src: FloatArray,
        w: Int,
        h: Int,
        sigma: Double,
    ): FloatArray {
        val size = Math.rint(sigma * 8 + 1).toInt() or 1
        val r = size / 2
        val kd = DoubleArray(size) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }
        val total = kd.sum()
        val k = DoubleArray(size) { kd[it] / total } // double accumulation: float rounding noise lowers the response
        // horizontal: reflect-pad each row once, then a branch-free symmetric sum
        val tmp = DoubleArray(src.size)
        val pad = FloatArray(w + 2 * r)
        for (y in 0 until h) {
            System.arraycopy(src, y * w, pad, r, w)
            for (i in 0 until r) {
                pad[r - 1 - i] = src[y * w + reflect(-(i + 1), w)]
                pad[r + w + i] = src[y * w + reflect(w + i, w)]
            }
            for (x in 0 until w) {
                var acc = k[r] * pad[x + r]
                for (j in 0 until r) acc += k[j] * (pad[x + j].toDouble() + pad[x + size - 1 - j])
                tmp[y * w + x] = acc
            }
        }
        // vertical: whole rows at a time (contiguous), symmetric in the kernel
        val out = FloatArray(src.size)
        val acc = DoubleArray(w)
        for (y in 0 until h) {
            for (x in 0 until w) acc[x] = k[r] * tmp[y * w + x]
            for (j in 0 until r) {
                val up = reflect(y + j - r, h) * w
                val dn = reflect(y + r - j, h) * w
                val kj = k[j]
                for (x in 0 until w) acc[x] += kj * (tmp[up + x] + tmp[dn + x])
            }
            for (x in 0 until w) out[y * w + x] = acc[x].toFloat()
        }
        return out
    }

    private fun reflect(
        i: Int,
        n: Int,
    ): Int {
        if (n == 1) return 0
        var j = i
        while (j < 0 || j >= n) j = if (j < 0) -j else 2 * (n - 1) - j
        return j
    }

    private fun sobel(
        src: FloatArray,
        w: Int,
        h: Int,
        horizontal: Boolean,
    ): FloatArray {
        val out = FloatArray(src.size)
        for (y in 0 until h) {
            val ym = reflect(y - 1, h) * w
            val y0 = y * w
            val yp = reflect(y + 1, h) * w
            for (x in 0 until w) {
                val xm = reflect(x - 1, w)
                val xp = reflect(x + 1, w)
                out[y0 + x] =
                    if (horizontal) {
                        (src[ym + xp] - src[ym + xm]) + 2 * (src[y0 + xp] - src[y0 + xm]) + (src[yp + xp] - src[yp + xm])
                    } else {
                        (src[yp + xm] - src[ym + xm]) + 2 * (src[yp + x] - src[ym + x]) + (src[yp + xp] - src[ym + xp])
                    }
            }
        }
        return out
    }

    /** Square dilation of radius [r]; pixels outside the image do not count. */
    private fun dilate(
        m: BooleanArray,
        w: Int,
        h: Int,
        r: Int,
    ): BooleanArray {
        val tmp = BooleanArray(m.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                for (j in maxOf(0, x - r)..minOf(w - 1, x + r)) {
                    if (m[y * w + j]) {
                        tmp[y * w + x] = true
                        break
                    }
                }
            }
        }
        val out = BooleanArray(m.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                for (j in maxOf(0, y - r)..minOf(h - 1, y + r)) {
                    if (tmp[j * w + x]) {
                        out[y * w + x] = true
                        break
                    }
                }
            }
        }
        return out
    }

    /**
     * Masked pixels in fill order (distance from the mask boundary) and each pixel's wave number
     * (0 unmasked, 1.. masked by distance, [Int.MAX_VALUE] masked but unreachable: no valid pixel in the strip).
     */
    private fun fillOrder(
        mask: BooleanArray,
        w: Int,
        h: Int,
    ): Pair<IntArray, IntArray> {
        val wave = IntArray(mask.size) { if (mask[it]) Int.MAX_VALUE else 0 }
        val order = ArrayList<Int>()

        val nb = IntArray(4)
        for (p in mask.indices) {
            if (!mask[p]) continue
            var edge = false
            for (i in 0 until neighbours(p, w, h, nb)) edge = edge || !mask[nb[i]]
            if (edge) {
                wave[p] = 1
                order += p
            }
        }
        var head = 0
        while (head < order.size) {
            val p = order[head++]
            for (i in 0 until neighbours(p, w, h, nb)) {
                val q = nb[i]
                if (wave[q] == Int.MAX_VALUE) {
                    wave[q] = wave[p] + 1
                    order += q
                }
            }
        }
        return order.toIntArray() to wave
    }

    /** In-bounds 4-neighbours of pixel [p] in a [w] x [h] grid, written to [out]; returns how many. */
    private fun neighbours(
        p: Int,
        w: Int,
        h: Int,
        out: IntArray,
    ): Int {
        var n = 0
        val x = p % w
        val y = p / w
        if (x > 0) out[n++] = p - 1
        if (x < w - 1) out[n++] = p + 1
        if (y > 0) out[n++] = p - w
        if (y < h - 1) out[n++] = p + w
        return n
    }

    private fun median(v: FloatArray): Float {
        v.sort() // in place: the caller reuses the same multiset of values
        val n = v.size
        return if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2f
    }

    private fun odd(v: Double): Int = maxOf(1, Math.rint(v).toInt()) or 1

    private fun rowOf(
        ref: Double,
        h: Int,
    ): Int = Math.rint(ref * h / GROUND_MAP_REFERENCE_HEIGHT).toInt().coerceIn(0, h)
}
