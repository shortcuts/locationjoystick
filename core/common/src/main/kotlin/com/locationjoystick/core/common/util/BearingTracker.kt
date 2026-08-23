package com.locationjoystick.core.common.util

import com.locationjoystick.core.model.LatLng

/**
 * Tracks consecutive tick positions to derive travel-direction bearing for engines
 * (route replay, roaming) that only emit position per tick, not bearing.
 */
class BearingTracker {
    private var lastPosition: LatLng? = null

    /** Returns the bearing from the last-seen position to [position], or null if unknown/unchanged. */
    fun advance(position: LatLng): Float? {
        val previous = lastPosition
        lastPosition = position
        if (previous == null || previous == position) return null
        return calculateBearing(previous.latitude, previous.longitude, position.latitude, position.longitude).toFloat()
    }
}
