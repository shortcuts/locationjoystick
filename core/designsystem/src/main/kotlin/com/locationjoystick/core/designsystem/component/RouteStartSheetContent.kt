package com.locationjoystick.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Bundles the loop/reverse/returnToLocation/followRoads state a "start route" sheet needs plus its
 * wiring to [LjRouteStartOptions] — duplicated identically across every surface that offers this
 * sheet before this extraction (map long-press sheet, Routes screen).
 *
 * While `followRoads` is checked, tapping Start fires [onStart] but keeps the sheet open with a
 * loading Start button until [isRoadRouteFetchInFlight] flips back to false — the OSRM fetch for
 * the road-following start happens in `ReplayOrchestrator` (service-owned), so nothing else tells
 * the sheet when it's done. [onCancel] is reused to dismiss once the fetch resolves, since both
 * call sites' `onCancel` already just closes the sheet with no other side effect.
 */
@Composable
fun RouteStartSheetContent(
    key: Any?,
    onTeleport: (reverse: Boolean) -> Unit,
    onStart: (loop: Boolean, reverse: Boolean, returnToLocation: Boolean, followRoads: Boolean) -> Unit,
    onCancel: () -> Unit,
    hideTeleport: Boolean = false,
    isRoadRouteFetchInFlight: Boolean = false,
) {
    var loop by remember(key) { mutableStateOf(false) }
    var reverse by remember(key) { mutableStateOf(false) }
    var returnToLocation by remember(key) { mutableStateOf(false) }
    var followRoads by remember(key) { mutableStateOf(false) }
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
        onTeleport = { onTeleport(reverse) },
        onCancel = onCancel,
        onStart = {
            if (followRoads) isAwaitingRoadStart = true
            onStart(loop, reverse, returnToLocation && !loop, followRoads)
        },
        hideTeleport = hideTeleport,
        isStarting = isAwaitingRoadStart,
    )
}
