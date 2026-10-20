package com.locationjoystick.core.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Leg geometry of the learning walk and the automatic fit. Port of `registration_bench.commanded_target`, `along_track_move`
 * and the moving rule of `_commanded_legs`. Flat earth at 111320 m per degree on purpose: the bench goldens
 * use it, and the haversine radius elsewhere (111195 m/deg) would drift 0.1 percent from them.
 */
object CalibrationWalkMath {
    const val METERS_PER_DEGREE = 111320.0
    const val MOVING_MIN_MPS = 0.3 // neighbour-frame speed that counts as walking (/state speed reads 0 mid-walk-to)

    /**
     * Learning walk when no fit exists: two perpendicular legs of [LEARNING_LEG_METERS]. Two legs pin the four terms; held out
     * on the other two `varycap` legs the fit's landing p90 was 6.2 m, inside the 10 m bar
     * (docs/features/tap-to-walk-investigation.md, "Automatic fit bench results").
     */
    val LEARNING_BEARINGS_DEG = doubleArrayOf(0.0, 90.0)
    const val LEARNING_LEG_METERS = 30.0

    fun commandedTarget(
        from: LatLng,
        bearingDeg: Double,
        distanceM: Double,
    ): LatLng {
        val b = Math.toRadians(bearingDeg)
        return LatLng(
            from.latitude + distanceM * cos(b) / METERS_PER_DEGREE,
            from.longitude + distanceM * sin(b) / (METERS_PER_DEGREE * cos(Math.toRadians(from.latitude))),
        )
    }

    /**
     * The move [p0] to [p] projected onto the commanded bearing: only the length comes from the positions,
     * so perpendicular jitter drops out. [lat0] scales east metres.
     */
    fun alongTrackOffset(
        p0: LatLng,
        p: LatLng,
        bearingDeg: Double,
        lat0: Double,
    ): GroundOffset {
        val east = (p.longitude - p0.longitude) * METERS_PER_DEGREE * cos(Math.toRadians(lat0))
        val north = (p.latitude - p0.latitude) * METERS_PER_DEGREE
        val b = Math.toRadians(bearingDeg)
        val along = east * sin(b) + north * cos(b)
        return GroundOffset(along * sin(b), along * cos(b))
    }

    /** The raw move [p0] to [p]. Fixes carry no jitter (it is added when the fix is built), so cross-track motion is real. */
    fun offset(
        p0: LatLng,
        p: LatLng,
        lat0: Double,
    ) = GroundOffset(
        (p.longitude - p0.longitude) * METERS_PER_DEGREE * cos(Math.toRadians(lat0)),
        (p.latitude - p0.latitude) * METERS_PER_DEGREE,
    )

    /** Frame i moves when the speed between frames `i-1` and `i+1` (clamped to the leg) is at least 0.3 m/s. */
    fun movingFlags(
        timesS: DoubleArray,
        positions: List<LatLng>,
    ): List<Boolean> {
        require(timesS.size == positions.size) { "one time per position" }
        val n = positions.size
        if (n == 0) return emptyList()
        val cosLat = cos(Math.toRadians(positions[0].latitude))
        return List(n) { i ->
            val a = maxOf(i - 1, 0)
            val b = minOf(i + 1, n - 1)
            val dn = (positions[b].latitude - positions[a].latitude) * METERS_PER_DEGREE
            val de = (positions[b].longitude - positions[a].longitude) * METERS_PER_DEGREE * cosLat
            hypot(dn, de) / maxOf(timesS[b] - timesS[a], 1e-6) >= MOVING_MIN_MPS
        }
    }
}
