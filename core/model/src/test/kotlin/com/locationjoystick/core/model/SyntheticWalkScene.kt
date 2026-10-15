package com.locationjoystick.core.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Same procedural scene as scripts/export_flow_golden.py: smooth value noise, a static HUD checkerboard and a ring.
private object SceneWorld {
    const val LATTICE = 10

    /** Integer-hash value 0..255 at lattice point (i, j); same hash as scripts/export_flow_golden.py. */
    fun lattice(
        i: Int,
        j: Int,
    ): Double {
        var h = ((i.toLong() * 73856093L) xor (j.toLong() * 19349663L)) and 0xFFFFFFFFL
        h = (h * 2654435761L) and 0xFFFFFFFFL
        return ((h shr 16) and 0xFFL).toDouble()
    }

    fun world(
        x: Int,
        y: Int,
        contrast: Double,
    ): Int {
        val i = x / LATTICE
        val j = y / LATTICE
        var fx = (x - i * LATTICE).toDouble() / LATTICE
        var fy = (y - j * LATTICE).toDouble() / LATTICE
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        val top = lattice(i, j) * (1 - fx) + lattice(i + 1, j) * fx
        val bot = lattice(i, j + 1) * (1 - fx) + lattice(i + 1, j + 1) * fx
        val v = 128.0 + contrast * ((top * (1 - fy) + bot * fy) - 127.5)
        return Math.rint(v).coerceIn(0.0, 255.0).toInt()
    }
}

internal class SyntheticWalkScene(
    val w: Int,
    val h: Int,
    val feetX: Double,
    val feetY: Double,
    /** Replaces the ring with a static sharp figure above the feet (what [FeetDetector] looks for); off for the flow goldens. */
    private val figure: Boolean = false,
    /** World texture amplitude; the feet detector needs a world softer than its edge threshold. */
    contrast: Double = 0.9,
) {
    private val pad = 90
    private val ww = w + pad
    private val ground = ByteArray(ww * (h + pad))

    init {
        for (y in 0 until h + pad) {
            for (x in 0 until ww) ground[y * ww + x] = SceneWorld.world(x, y, contrast).toByte()
        }
    }

    /** world(x + dx t, y + dy t) plus the static checkerboard HUD and the ring. */
    fun frame(
        t: Int,
        dx: Int,
        dy: Int,
    ): GrayImage {
        val s = w / 1080.0
        val px = ByteArray(w * h)
        // negative scrolls start deeper in the padded ground (up to 8 frames of 9 px) so indices stay >= 0
        val bx = if (dx < 0) -dx * 8 else 0
        val by = if (dy < 0) -dy * 8 else 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                var g = ground[(y + dy * t + by) * ww + x + dx * t + bx].toInt() and 0xFF
                if (y >= (900 * s).toInt() && y < (1000 * s).toInt() && x >= (60 * s).toInt() && x < (260 * s).toInt()) {
                    g = if ((x / 4 + y / 4) % 2 == 0) 255 else 0
                }
                if (!figure && kotlin.math.abs(hypot(x - feetX, y - feetY) - 160.0 * s) <= 3.0) g = 255
                if (figure && y < feetY && y >= feetY - 0.05 * h && kotlin.math.abs(x - feetX) <= 0.02 * w) {
                    g = if (kotlin.math.abs(x - feetX) <= 0.01 * w) 0 else 255
                }
                px[y * w + x] = g.toByte()
            }
        }
        return GrayImage(w, h, px)
    }

    fun leg(
        bearing: Double,
        dx: Int,
        dy: Int,
        frames: Int = 9,
    ): WalkLeg {
        val a = Math.toRadians(bearing)
        return WalkLeg(
            (0 until frames).map { frame(it, dx, dy) },
            List(frames) { true },
            (0 until frames).map { GroundOffset(it * 1.3 * sin(a), it * 1.3 * cos(a)) },
        )
    }
}
