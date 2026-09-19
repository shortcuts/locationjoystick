package com.locationjoystick.core.map.projection

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapCoordinateSystem
import com.locationjoystick.core.model.MapTileSource

/**
 * Presentation-boundary coordinate transform between the app's WGS-84 domain model and the datum
 * the currently selected base map is drawn in.
 *
 * Apply [toMap] to anything *drawn on* or *used to position the camera of* a MapLibre map, and
 * [fromMap] to anything *read back from* it (tap/long-press coordinates). Nothing persisted, sent
 * to `LocationManager`, shared via deep link or synced to a group ever passes through here.
 */
interface MapProjection {
    fun toMap(point: LatLng): LatLng

    fun fromMap(point: LatLng): LatLng

    fun toMap(points: List<LatLng>): List<LatLng> = points.map(::toMap)

    object Identity : MapProjection {
        override fun toMap(point: LatLng): LatLng = point

        override fun fromMap(point: LatLng): LatLng = point

        override fun toMap(points: List<LatLng>): List<LatLng> = points
    }

    object Gcj02Projection : MapProjection {
        override fun toMap(point: LatLng): LatLng = Gcj02.fromWgs84(point)

        override fun fromMap(point: LatLng): LatLng = Gcj02.toWgs84(point)
    }
}

/** Projection matching this tile source's [MapTileSource.coordinateSystem]. */
val MapTileSource.projection: MapProjection
    get() =
        when (coordinateSystem) {
            MapCoordinateSystem.WGS84 -> MapProjection.Identity
            MapCoordinateSystem.GCJ02 -> MapProjection.Gcj02Projection
        }
