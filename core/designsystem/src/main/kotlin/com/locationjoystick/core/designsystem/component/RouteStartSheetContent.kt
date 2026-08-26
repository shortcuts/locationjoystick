package com.locationjoystick.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Bundles the loop/reverse/returnToLocation/followRoads state a "start route" sheet needs plus its
 * wiring to [LjRouteStartOptions] — duplicated identically across every surface that offers this
 * sheet before this extraction (map long-press sheet, Routes screen).
 */
@Composable
fun RouteStartSheetContent(
    key: Any?,
    onTeleport: (reverse: Boolean) -> Unit,
    onStart: (loop: Boolean, reverse: Boolean, returnToLocation: Boolean, followRoads: Boolean) -> Unit,
    onCancel: () -> Unit,
    hideTeleport: Boolean = false,
) {
    var loop by remember(key) { mutableStateOf(false) }
    var reverse by remember(key) { mutableStateOf(false) }
    var returnToLocation by remember(key) { mutableStateOf(false) }
    var followRoads by remember(key) { mutableStateOf(false) }

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
        onStart = { onStart(loop, reverse, returnToLocation && !loop, followRoads) },
        hideTeleport = hideTeleport,
    )
}
