package com.locationjoystick.core.map.maplibre

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.model.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng as MapLatLng

/**
 * Teleport-scale moves should [MapLibreMap.moveCamera] instead of animating. A 500ms pan
 * across the world makes MapLibre request tiles along the interpolated path, so the
 * destination stays on the empty style until those finish.
 */
fun shouldSnapMapCamera(
    from: LatLng,
    to: LatLng,
    snapDistanceMeters: Double = AppConstants.MapConstants.SNAP_CAMERA_DISTANCE_METERS,
): Boolean = haversineDistance(from, to) >= snapDistanceMeters

fun MapLibreMap.followOrSnapTo(
    previous: LatLng?,
    target: LatLng,
    zoom: Double? = null,
    animateDurationMs: Int = 500,
) {
    if (previous == target) return
    val dest = MapLatLng(target.latitude, target.longitude)
    val update =
        if (zoom != null) {
            CameraUpdateFactory.newLatLngZoom(dest, zoom)
        } else {
            CameraUpdateFactory.newLatLng(dest)
        }
    if (previous == null || shouldSnapMapCamera(previous, target)) {
        moveCamera(update)
    } else {
        animateCamera(update, animateDurationMs)
    }
}
