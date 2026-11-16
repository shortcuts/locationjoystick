package com.locationjoystick.core.location

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState

internal sealed interface ExternalTeleportDecision {
    data class Teleport(
        val point: LatLng,
    ) : ExternalTeleportDecision

    data class StartAt(
        val point: LatLng,
    ) : ExternalTeleportDecision

    data class Ignore(
        val reason: String,
    ) : ExternalTeleportDecision
}

internal fun externalTeleportDecision(
    state: MockLocationState,
    hideTeleport: Boolean,
    lat: Double?,
    lng: Double?,
): ExternalTeleportDecision {
    // The range test also rejects NaN, because every comparison with NaN is false.
    if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
        return ExternalTeleportDecision.Ignore("invalid coordinates lat=$lat lng=$lng")
    }
    if (hideTeleport) return ExternalTeleportDecision.Ignore("teleport features are hidden")
    val point = LatLng(lat, lng)
    return when (state) {
        MockLocationState.IDLE -> ExternalTeleportDecision.StartAt(point)
        MockLocationState.RUNNING -> ExternalTeleportDecision.Teleport(point)
        MockLocationState.PAUSED -> ExternalTeleportDecision.Ignore("spoofing is paused")
        MockLocationState.ERROR -> ExternalTeleportDecision.Ignore("spoofing is in error")
    }
}
