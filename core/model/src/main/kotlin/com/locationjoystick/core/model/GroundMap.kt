package com.locationjoystick.core.model

import kotlin.math.cos
import kotlin.math.sin

/** Screen size (px) the calibration values are expressed in. Every screen is scaled linearly to it. */
const val GROUND_MAP_REFERENCE_WIDTH = 1080.0
const val GROUND_MAP_REFERENCE_HEIGHT = 2340.0

/**
 * Fitted pitched-camera terms, all in reference-frame pixels (1080x2340), not device pixels.
 *
 * @property headingDeg how far north is rotated in the frame, degrees.
 * @property lateralPxPerM px per metre across the screen at the feet.
 * @property forwardPxPerM px per metre up the screen at the feet.
 * @property perspective perspective term (1/m); px per metre falls with distance.
 * @property feetX player feet pixel x, reference pixels.
 * @property feetY player feet pixel y, reference pixels.
 */
data class GroundCalibration(
    val headingDeg: Double,
    val lateralPxPerM: Double,
    val forwardPxPerM: Double,
    val perspective: Double,
    val feetX: Double,
    val feetY: Double,
)

/** Ground offset in metres. East is +x, north is +y (north up). */
data class GroundOffset(
    val eastMeters: Double,
    val northMeters: Double,
)

/**
 * Pixel <-> ground mapping. Screen frame: x right, y down, pixels of the real screen
 * ([screenWidth] x [screenHeight]). Ground frame: metres east/north of the player's feet.
 * Port of `registration_bench.ground_to_game` / `tap_to_ground_persp`; no skew term.
 */
class GroundMap(
    private val cal: GroundCalibration,
    private val screenWidth: Double,
    private val screenHeight: Double,
) {
    // Row-major H = camH * rot(theta), and its adjugate (inverse up to scale; the homogeneous divide cancels it).
    private val h: DoubleArray
    private val hInv: DoubleArray
    private val feetW: Double

    init {
        require(screenWidth > 0 && screenHeight > 0) { "screen size must be positive" }
        val t = Math.toRadians(cal.headingDeg)
        val c = cos(t)
        val s = sin(t)
        val ls = cal.lateralPxPerM
        val fs = cal.forwardPxPerM
        val a = cal.perspective
        h = doubleArrayOf(ls * c, -ls * s, cal.feetX, -fs * s, -fs * c, cal.feetY, -a * s, -a * c, 1.0)
        hInv = adjugate(h)
        feetW = hInv[6] * cal.feetX + hInv[7] * cal.feetY + hInv[8]
    }

    /** Ground offset of screen pixel ([x], [y]) from the feet, or null past the horizon line (divisor <= 0); that line sits far above the visible ground band. */
    fun pixelToGround(
        x: Double,
        y: Double,
    ): GroundOffset? {
        val rx = x * GROUND_MAP_REFERENCE_WIDTH / screenWidth
        val ry = y * GROUND_MAP_REFERENCE_HEIGHT / screenHeight
        val w = hInv[6] * rx + hInv[7] * ry + hInv[8]
        // Same sign as the feet's divisor and not near zero; the adjugate's overall sign is arbitrary.
        if (w * feetW <= HORIZON_EPS) return null
        val f = apply(hInv, cal.feetX, cal.feetY)
        val p = apply(hInv, rx, ry)
        return GroundOffset(p.first - f.first, p.second - f.second)
    }

    /** Screen pixel of the ground point [east], [north] metres from the feet. */
    fun groundToPixel(
        east: Double,
        north: Double,
    ): Pair<Double, Double> {
        val f = apply(hInv, cal.feetX, cal.feetY)
        val p = apply(h, f.first + east, f.second + north)
        return Pair(p.first * screenWidth / GROUND_MAP_REFERENCE_WIDTH, p.second * screenHeight / GROUND_MAP_REFERENCE_HEIGHT)
    }

    private fun apply(
        m: DoubleArray,
        x: Double,
        y: Double,
    ): Pair<Double, Double> {
        val w = m[6] * x + m[7] * y + m[8]
        return Pair((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    private companion object {
        const val HORIZON_EPS = 1e-9

        fun adjugate(m: DoubleArray): DoubleArray =
            doubleArrayOf(
                m[4] * m[8] - m[5] * m[7],
                m[2] * m[7] - m[1] * m[8],
                m[1] * m[5] - m[2] * m[4],
                m[5] * m[6] - m[3] * m[8],
                m[0] * m[8] - m[2] * m[6],
                m[2] * m[3] - m[0] * m[5],
                m[3] * m[7] - m[4] * m[6],
                m[1] * m[6] - m[0] * m[7],
                m[0] * m[4] - m[1] * m[3],
            )
    }
}
