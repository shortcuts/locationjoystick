package com.locationjoystick.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.parseTeleportBetweenDelaySeconds

/**
 * Shared route options, including planting and optional hops between stops.
 * Road-following starts keep their loading state until route planning finishes.
 */
@Composable
fun RouteStartSheetContent(
    key: Any?,
    onTeleport: (reverse: Boolean) -> Unit,
    onStart: (
        loop: Boolean,
        reverse: Boolean,
        returnToLocation: Boolean,
        followRoads: Boolean,
        planting: Boolean,
        teleportBetweenWaypoints: Boolean,
        teleportBetweenDelaySeconds: Int,
    ) -> Unit,
    onCancel: () -> Unit,
    hideTeleport: Boolean = false,
    isTeleportRoute: Boolean = false,
    isRoadRouteFetchInFlight: Boolean = false,
) {
    var loop by remember(key) { mutableStateOf(true) }
    var reverse by remember(key) { mutableStateOf(false) }
    var returnToLocation by remember(key) { mutableStateOf(false) }
    var followRoads by remember(key) { mutableStateOf(false) }
    var planting by remember(key) { mutableStateOf(false) }
    var teleportBetweenWaypoints by remember(key) { mutableStateOf(false) }
    var teleportBetweenDelaySecondsText by remember(key) {
        mutableStateOf(AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS.toString())
    }
    var isAwaitingRoadStart by remember(key) { mutableStateOf(false) }

    LaunchedEffect(isRoadRouteFetchInFlight) {
        if (isAwaitingRoadStart && !isRoadRouteFetchInFlight) {
            onCancel()
        }
    }

    LjRouteStartOptions(
        loop = loop,
        onLoopChange = { loop = it },
        reverse = reverse,
        onReverseChange = { reverse = it },
        returnToLocation = returnToLocation,
        onReturnToLocationChange = { returnToLocation = it },
        followRoads = followRoads,
        onFollowRoadsChange = { followRoads = it },
        planting = planting,
        onPlantingChange = {
            planting = it
            if (it) returnToLocation = false
        },
        teleportBetweenWaypoints = teleportBetweenWaypoints && !hideTeleport,
        onTeleportBetweenWaypointsChange = { teleportBetweenWaypoints = it },
        teleportBetweenDelaySecondsText = teleportBetweenDelaySecondsText,
        onTeleportBetweenDelaySecondsTextChange = { teleportBetweenDelaySecondsText = it },
        onTeleport = { onTeleport(reverse) },
        onCancel = onCancel,
        onStart = {
            val effectiveFollowRoads = followRoads && !isTeleportRoute
            if (effectiveFollowRoads) isAwaitingRoadStart = true
            onStart(
                loop || planting,
                reverse,
                returnToLocation && !loop && !planting,
                effectiveFollowRoads,
                planting && !isTeleportRoute,
                teleportBetweenWaypoints && !hideTeleport && !isTeleportRoute,
                parseTeleportBetweenDelaySeconds(teleportBetweenDelaySecondsText),
            )
        },
        hideTeleport = hideTeleport,
        isTeleportRoute = isTeleportRoute,
        isStarting = isAwaitingRoadStart,
    )
}
