package com.locationjoystick.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shift of image `b` relative to image `a` in pixels (positive fx: content moved right, positive fy: down),
 * and the correlation peak strength (about 1 for a clean pure shift, near 0 for unrelated images).
 */
data class FlowResult(
    val fx: Double,
    val fy: Double,
    val response: Double,
)

/**
 * Windowed phase correlation, the pure-Kotlin equivalent of `cv2.phaseCorrelate(a, b, hanning)`.
 * Pads to the same 2^a 3^b 5^c size OpenCV uses, so results agree to float rounding.
 */
object PhaseCorrelation {
    private const val MIN_SIZE = 8

    // Keeps the cross-power division finite. Must stay tiny: features are ~unit scale, so 1e-7 already
    // flattens the high-frequency bins and cuts the response by about a third versus cv2 (measured).
    private const val EPS = 2.220446049250313e-16
    private const val CENTROID_HALF = 2 // 5x5 weighted centroid around the integer peak

    /** Windowed, zero-padded spectrum of one image; reuse it across every pair the image takes part in. */
    class Spectrum internal constructor(
        internal val re: DoubleArray,
        internal val im: DoubleArray,
        internal val pw: Int,
        internal val ph: Int,
    )

    fun correlate(
        a: FloatArray,
        b: FloatArray,
        width: Int,
        height: Int,
    ): FlowResult = correlate(spectrum(a, width, height), spectrum(b, width, height))

    fun spectrum(
        a: FloatArray,
        width: Int,
        height: Int,
    ): Spectrum {
        require(width >= MIN_SIZE && height >= MIN_SIZE) { "frame must be at least ${MIN_SIZE}x$MIN_SIZE" }
        require(a.size == width * height) { "buffer size must be width * height" }
        val pw = optimalSize(width)
        val ph = optimalSize(height)
        val wx = hann(width)
        val wy = hann(height)
        val re = DoubleArray(pw * ph)
        val im = DoubleArray(pw * ph)
        for (y in 0 until height) {
            for (x in 0 until width) re[y * pw + x] = a[y * width + x] * sqrt(wx[x] * wy[y])
        }
        fft2(re, im, pw, ph, false, height)
        return Spectrum(re, im, pw, ph)
    }

    /** Shift of [b] relative to [a]; both spectra must come from images of the same size. */
    fun correlate(
        a: Spectrum,
        b: Spectrum,
    ): FlowResult {
        require(a.pw == b.pw && a.ph == b.ph) { "spectra must come from same-size images" }
        val pw = a.pw
        val ph = a.ph
        val ar = DoubleArray(pw * ph)
        val ai = DoubleArray(pw * ph)
        val br = b.re
        val bi = b.im
        for (i in ar.indices) {
            val re = a.re[i] * br[i] + a.im[i] * bi[i]
            val im = a.im[i] * br[i] - a.re[i] * bi[i]
            val m = hypot(re, im) + EPS
            ar[i] = re / m
            ai[i] = im / m
        }
        fft2(ar, ai, pw, ph, true, ph)
        val scale = 1.0 / (pw.toDouble() * ph)

        // fftshift by index: shift 0 sits at (ph/2, pw/2); shifted (sy, sx) reads unshifted (sy - ph/2, sx - pw/2) mod size
        fun at(
            sy: Int,
            sx: Int,
        ) = ar[((sy - ph / 2 + ph) % ph) * pw + (sx - pw / 2 + pw) % pw] * scale

        var peak = Double.NEGATIVE_INFINITY
        var py = 0
        var px = 0
        for (sy in 0 until ph) {
            for (sx in 0 until pw) {
                val v = at(sy, sx)
                if (v > peak) {
                    peak = v
                    py = sy
                    px = sx
                }
            }
        }
        var sum = 0.0
        var sxw = 0.0
        var syw = 0.0
        for (sy in maxOf(0, py - CENTROID_HALF)..minOf(ph - 1, py + CENTROID_HALF)) {
            for (sx in maxOf(0, px - CENTROID_HALF)..minOf(pw - 1, px + CENTROID_HALF)) {
                val v = at(sy, sx)
                sum += v
                sxw += v * (sx - px)
                syw += v * (sy - py)
            }
        }
        val cx = if (sum != 0.0) px + sxw / sum else px.toDouble()
        val cy = if (sum != 0.0) py + syw / sum else py.toDouble()
        return FlowResult(pw / 2 - cx, ph / 2 - cy, sum)
    }

    /** Smallest size >= [n] of the form 2^a 3^b 5^c: OpenCV's `getOptimalDFTSize`, so padding matches `cv2.phaseCorrelate`. */
    private fun optimalSize(n: Int): Int {
        var m = n
        while (true) {
            var r = m
            for (p in intArrayOf(2, 3, 5)) while (r % p == 0) r /= p
            if (r == 1) return m
            m++
        }
    }

    /**
     * 1-D Hann factor 0.5 * (1 - cos(2 pi i / (n - 1))). `cv2.createHanningWindow` makes the 2-D window
     * sqrt(wx * wy), not the plain product (measured: max difference 5e-8; the plain product lowers the
     * response by about 20 percent).
     */
    private fun hann(n: Int) = DoubleArray(n) { 0.5 * (1 - cos(2 * PI * it / (n - 1))) }

    private fun fft2(
        re: DoubleArray,
        im: DoubleArray,
        w: Int,
        h: Int,
        inverse: Boolean,
        liveRows: Int,
    ) {
        val rowTw = Dft(w)
        val tr = DoubleArray(maxOf(w, h))
        val ti = DoubleArray(maxOf(w, h))
        // rows at and past liveRows are all zero on a forward pass, and so are their transforms
        for (y in 0 until liveRows) {
            System.arraycopy(re, y * w, tr, 0, w)
            System.arraycopy(im, y * w, ti, 0, w)
            rowTw.transform(tr, ti, inverse)
            System.arraycopy(tr, 0, re, y * w, w)
            System.arraycopy(ti, 0, im, y * w, w)
        }
        val colTw = if (h == w) rowTw else Dft(h)
        for (x in 0 until w) {
            for (y in 0 until h) {
                tr[y] = re[y * w + x]
                ti[y] = im[y * w + x]
            }
            colTw.transform(tr, ti, inverse)
            for (y in 0 until h) {
                re[y * w + x] = tr[y]
                im[y * w + x] = ti[y]
            }
        }
    }

    /** Mixed-radix (any size, fast for 2^a 3^b 5^c) in-place complex DFT of length [n]. The inverse is unscaled. */
    private class Dft(
        val n: Int,
    ) {
        private val cosT = DoubleArray(n) { cos(2 * PI * it / n) }
        private val sinT = DoubleArray(n) { sin(2 * PI * it / n) }
        private val factors: IntArray
        private val outRe = DoubleArray(n)
        private val outIm = DoubleArray(n)
        private val yRe: DoubleArray
        private val yIm: DoubleArray
        private var sign = -1.0

        init {
            val f = ArrayList<Int>()
            var m = n
            var p = 2
            while (m > 1) {
                while (m % p == 0) {
                    f += p
                    m /= p
                }
                p++
            }
            factors = f.toIntArray()
            yRe = DoubleArray(factors.maxOrNull() ?: 1)
            yIm = DoubleArray(factors.maxOrNull() ?: 1)
        }

        fun transform(
            re: DoubleArray,
            im: DoubleArray,
            inverse: Boolean,
        ) {
            sign = if (inverse) 1.0 else -1.0
            rec(re, im, 0, 1, n, 0, 0)
            System.arraycopy(outRe, 0, re, 0, n)
            System.arraycopy(outIm, 0, im, 0, n)
        }

        /** DFT of the [len] samples `in[inOff + i * stride]` into `out[outOff ..< outOff + len]`. */
        private fun rec(
            inRe: DoubleArray,
            inIm: DoubleArray,
            inOff: Int,
            stride: Int,
            len: Int,
            outOff: Int,
            fi: Int,
        ) {
            if (len == 1) {
                outRe[outOff] = inRe[inOff]
                outIm[outOff] = inIm[inOff]
                return
            }
            val p = factors[fi]
            val m = len / p
            for (r in 0 until p) rec(inRe, inIm, inOff + r * stride, stride * p, m, outOff + r * m, fi + 1)
            val tw = n / len // W_len^x = W_n^(x * tw)
            for (k in 0 until m) {
                for (r in 0 until p) {
                    yRe[r] = outRe[outOff + r * m + k]
                    yIm[r] = outIm[outOff + r * m + k]
                }
                for (q in 0 until p) {
                    var sr = 0.0
                    var si = 0.0
                    val j = k + q * m
                    val step = j * tw // < n; twiddle index r * step is accumulated mod n
                    var idx = 0
                    for (r in 0 until p) {
                        val wr = cosT[idx]
                        val wi = sign * sinT[idx]
                        sr += yRe[r] * wr - yIm[r] * wi
                        si += yRe[r] * wi + yIm[r] * wr
                        idx += step
                        if (idx >= n) idx -= n
                    }
                    outRe[outOff + j] = sr
                    outIm[outOff + j] = si
                }
            }
        }
    }
}
