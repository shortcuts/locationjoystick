package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.common.constants.AppConstants.ReconnectTapConstants
import kotlin.math.abs

enum class ReconnectState { ON, OFF }

/** [centerX]/[centerY] are screen pixels at the ball's center; [state] is what its colors say. */
data class ReconnectControl(
    val state: ReconnectState,
    val centerX: Int,
    val centerY: Int,
)

/**
 * Finds the companion-device control in a screenshot and reads whether it is connected.
 *
 * Pure over ARGB pixels (no Bitmap) so it tests on the JVM. No fixed colors and no text, so it holds
 * in every UI language, theme and brightness. It finds the ball by shape (an upper half and a lower
 * half stacked into a circle, with a dot across the split line) among the window's flat-color
 * regions, then classifies from the colors it measures there: ON is a green dot under a red top, OFF
 * is a dark dot with a rose top over a blue bottom. Returns null for anything it cannot positively
 * read; null never means "tap".
 */
object ReconnectControlDetector {
    // A pixel is flat when every in-window neighbor differs by at most this per channel.
    private const val FLAT_STEP = 4
    private const val MIN_PIXELS = 12

    // Ball diameter ~7.2% of screen width; a half is a semicircle.
    private const val MIN_HALF_WIDTH = 0.03f
    private const val MAX_HALF_WIDTH = 0.10f
    private const val MIN_HALF_ASPECT = 0.2f
    private const val MAX_HALF_ASPECT = 0.6f

    /** First screen column and one past the last row of the search window; the caller crops to these. */
    fun searchX0(width: Int): Int = (width * ReconnectTapConstants.SEARCH_X_MIN_PCT).toInt()

    fun searchY1(height: Int): Int = (height * ReconnectTapConstants.SEARCH_Y_MAX_PCT).toInt()

    /**
     * [cropX] is the screen column of `pixels[0]`: a caller may pass only the columns from [searchX0] on
     * (row stride `width - cropX`, row 0 is screen row 0). Results stay in screen coordinates.
     */
    fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
        cropX: Int = 0,
    ): ReconnectControl? {
        val x0 = searchX0(width)
        val y1 = searchY1(height)
        require(cropX <= x0) { "cropX $cropX is right of the search window at $x0" }
        val flats = flatRegions(pixels, width, x0, y1, cropX)
        val halves =
            flats.filter {
                it.width >= width * MIN_HALF_WIDTH &&
                    it.width <= width * MAX_HALF_WIDTH &&
                    it.height >= it.width * MIN_HALF_ASPECT &&
                    it.height <= it.width * MAX_HALF_ASPECT
            }
        var off: ReconnectControl? = null
        for (u in halves) {
            for (l in halves) {
                if (!stacked(u, l)) continue
                val split = (u.bottom + l.top) / 2
                val dot = flats.firstOrNull { isDot(it, l, split) } ?: continue
                when (stateOf(u, l, dot)) {
                    ReconnectState.ON -> return ReconnectControl(ReconnectState.ON, dot.centerX, dot.centerY)
                    ReconnectState.OFF -> if (off == null) off = ReconnectControl(ReconnectState.OFF, dot.centerX, split)
                    null -> Unit
                }
            }
        }
        return off
    }

    /** [u] sits directly above [l], both about as wide and as centered as one ball's halves, forming a circle. */
    private fun stacked(
        u: Flat,
        l: Flat,
    ): Boolean {
        if (u === l) return false
        val w = l.width
        val gap = l.top - u.bottom
        val unionW = maxOf(u.right, l.right) - minOf(u.left, l.left) + 1
        val unionH = l.bottom - u.top + 1
        return abs(u.width - w) <= 0.2f * w &&
            abs(u.centerX - l.centerX) <= 0.1f * w &&
            gap >= 0 &&
            gap <= 0.15f * w &&
            unionH >= 0.85f * unionW &&
            unionH <= 1.15f * unionW
    }

    /** The center dot straddles the split line of the ball whose lower half is [l]. */
    private fun isDot(
        f: Flat,
        l: Flat,
        split: Int,
    ): Boolean {
        val w = l.width
        return f.width >= 0.12f * w &&
            f.width <= 0.5f * w &&
            f.height >= 0.12f * w &&
            f.height <= 0.5f * w &&
            abs(f.centerX - l.centerX) <= 0.1f * w &&
            abs(f.centerY - split) <= 0.2f * w
    }

    /**
     * Relative channel dominance, so brightness and tint do not matter. ON: green dot under a red top.
     * OFF: dark dot, rose top (red over green and blue), blue bottom (blue over red and green).
     */
    private fun stateOf(
        u: Flat,
        l: Flat,
        d: Flat,
    ): ReconnectState? {
        val dotGreen = d.g - maxOf(d.r, d.b)
        if (dotGreen >= 0.4f * d.g && u.r - maxOf(u.g, u.b) >= 0.4f * u.r) return ReconnectState.ON
        val roseTop = u.r - u.g >= 0.15f * u.r && u.r > u.b
        val blueBottom = l.b - l.r >= 0.3f * l.b && l.b >= l.g
        val darkDot = dotGreen < 0.1f * d.g + 5 && d.r + d.g + d.b <= 0.7f * (l.r + l.g + l.b)
        return if (roseTop && blueBottom && darkDot) ReconnectState.OFF else null
    }

    /** A flat region: bounding box and mean color. */
    private class Flat(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val r: Int,
        val g: Int,
        val b: Int,
    ) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
    }

    /** 4-connected components of flat pixels (columns [x0] until [width], rows 0 until [y1]), at least [MIN_PIXELS] big. */
    private fun flatRegions(
        pixels: IntArray,
        width: Int,
        x0: Int,
        y1: Int,
        cropX: Int,
    ): List<Flat> {
        val w = width - x0
        val stride = width - cropX
        if (w <= 0 || y1 <= 0) return emptyList()

        fun at(
            x: Int,
            y: Int,
        ) = pixels[y * stride + (x0 - cropX) + x]

        fun close(
            a: Int,
            b: Int,
        ) = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= FLAT_STEP &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= FLAT_STEP &&
            abs((a and 0xFF) - (b and 0xFF)) <= FLAT_STEP

        val flat = BooleanArray(w * y1)
        for (y in 0 until y1) {
            for (x in 0 until w) {
                val p = at(x, y)
                flat[y * w + x] =
                    (x == 0 || close(p, at(x - 1, y))) &&
                    (x == w - 1 || close(p, at(x + 1, y))) &&
                    (y == 0 || close(p, at(x, y - 1))) &&
                    (y == y1 - 1 || close(p, at(x, y + 1)))
            }
        }
        val out = ArrayList<Flat>()
        val stack = ArrayDeque<Int>()

        fun push(j: Int) {
            if (!flat[j]) return
            flat[j] = false
            stack.add(j)
        }
        for (start in flat.indices) {
            if (!flat[start]) continue
            flat[start] = false
            stack.add(start)
            var l = w
            var t = y1
            var rt = 0
            var b = 0
            var n = 0
            var sr = 0L
            var sg = 0L
            var sb = 0L
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val x = i % w
                val y = i / w
                l = minOf(l, x)
                rt = maxOf(rt, x)
                t = minOf(t, y)
                b = maxOf(b, y)
                val p = at(x, y)
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
                n++
                if (x > 0) push(i - 1)
                if (x < w - 1) push(i + 1)
                if (y > 0) push(i - w)
                if (y < y1 - 1) push(i + w)
            }
            if (n >= MIN_PIXELS) {
                out.add(Flat(x0 + l, t, x0 + rt, b, (sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt()))
            }
        }
        return out
    }
}
