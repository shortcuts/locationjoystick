package com.locationjoystick.core.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * Ground offsets of the tap-to-walk map. Flat earth at 111320 m per degree on purpose: the flow fit and the tap
 * share it, and the haversine radius elsewhere (111195 m/deg) would drift 0.1 percent from them.
 */
object CalibrationWalkMath {
    const val METERS_PER_DEGREE = 111320.0

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

    /** The raw move [p0] to [p]. Fixes carry no jitter (it is added when the fix is built), so cross-track motion is real. */
    fun offset(
        p0: LatLng,
        p: LatLng,
        lat0: Double,
    ) = GroundOffset(
        (p.longitude - p0.longitude) * METERS_PER_DEGREE * cos(Math.toRadians(lat0)),
        (p.latitude - p0.latitude) * METERS_PER_DEGREE,
    )
}
