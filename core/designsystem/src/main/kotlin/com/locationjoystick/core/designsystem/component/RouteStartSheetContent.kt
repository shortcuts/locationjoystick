package com.locationjoystick.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.parseTeleportBetweenDelaySeconds
import com.locationjoystick.core.designsystem.LocalGuidedPath
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.RouteStartConfig

/**
 * Shared route options, including planting and optional hops between stops.
 * Road-following starts keep their loading state until route planning finishes.
 * The sheet opens collapsed: a summary line seeded from [remembered], with the checkboxes behind Options.
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
    /** Route carries its own Planting setting: the Planting option is shown on and locked. */
    isPlantedRoute: Boolean = false,
    /** Route style is Walkable (roads): with [isPlantedRoute], Follow roads is shown on and locked. */
    isRoadRoute: Boolean = false,
    /** Options of the last Start; null keeps the stock defaults. */
    remembered: RouteStartConfig? = null,
) {
    var loop by remember(key) { mutableStateOf(remembered?.isLooping ?: true) }
    var reverse by remember(key) { mutableStateOf(remembered?.isReverse ?: false) }
    var returnToLocation by remember(key) { mutableStateOf(remembered?.isReturnToLocation == true && !isPlantedRoute) }
    var followRoads by remember(key) {
        mutableStateOf((isPlantedRoute && isRoadRoute) || (remembered?.followRoadsToStart == true && !isTeleportRoute))
    }
    var planting by remember(key) { mutableStateOf(isPlantedRoute) }
    var teleportBetweenWaypoints by remember(key) {
        mutableStateOf(remembered?.teleportBetweenWaypoints == true && !isTeleportRoute)
    }
    var teleportBetweenDelaySecondsText by remember(key) {
        mutableStateOf(
            (remembered?.teleportBetweenDelaySeconds ?: AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS).toString(),
        )
    }
    var isAwaitingRoadStart by remember(key) { mutableStateOf(false) }
    var optionsExpanded by remember(key) { mutableStateOf(false) }
    val showsPlanting = LocalGuidedPath.current.showsPlanting
    val summary =
        listOfNotNull(
            stringResource(R.string.route_start_options_loop).takeIf { loop || planting },
            stringResource(R.string.lj_route_start_options_planting).takeIf { showsPlanting && planting },
            stringResource(R.string.route_start_options_reverse).takeIf { reverse },
            stringResource(R.string.lj_route_start_options_return_to_location).takeIf { returnToLocation && !loop && !planting },
            stringResource(R.string.lj_route_start_options_follow_roads).takeIf { followRoads && !isTeleportRoute },
            stringResource(R.string.lj_route_start_options_teleport_between_waypoints)
                .takeIf { teleportBetweenWaypoints && !hideTeleport && !isTeleportRoute },
        ).joinToString(", ").ifEmpty { stringResource(R.string.route_start_options_summary_none) }

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
        plantingLocked = isPlantedRoute,
        followRoadsLocked = isPlantedRoute && isRoadRoute,
        isStarting = isAwaitingRoadStart,
        optionsSummary = summary,
        optionsExpanded = optionsExpanded,
        onOptionsExpandedChange = { optionsExpanded = it },
    )
}
