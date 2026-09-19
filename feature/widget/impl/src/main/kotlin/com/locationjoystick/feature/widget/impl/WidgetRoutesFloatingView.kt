package com.locationjoystick.feature.widget.impl

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.formatCapturedPointsForClipboard
import com.locationjoystick.core.common.util.parseTeleportBetweenDelaySeconds
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjRouteStartOptions
import com.locationjoystick.core.designsystem.component.RoutesPickerList
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.core.model.startWaypoint
import com.locationjoystick.feature.widget.impl.R

@Composable
internal fun RoutesFloatingView(
    routes: List<com.locationjoystick.core.model.Route>,
    onDismiss: () -> Unit,
    onStartRoute: (
        routeId: String,
        isLooping: Boolean,
        isReverse: Boolean,
        isReturnToLocation: Boolean,
        followRoadsToStart: Boolean,
        isPlanting: Boolean,
        teleportBetweenWaypoints: Boolean,
        teleportBetweenDelaySeconds: Int,
    ) -> Unit,
    onTeleport: (LatLng) -> Unit,
    onRename: (com.locationjoystick.core.model.Route, String) -> Unit,
    onDelete: (com.locationjoystick.core.model.Route) -> Unit,
    onShareOpened: () -> Unit = onDismiss,
    hideTeleport: Boolean = false,
    sortMode: SavedItemSortMode = SavedItemSortMode.NEWEST_FIRST,
    onSortModeSelected: (SavedItemSortMode) -> Unit = {},
) {
    var selectedRouteId by remember { mutableStateOf<String?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var renamingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }
    var deletingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }
    var renameText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val shareRouteTitle = stringResource(R.string.widget_share_route)

    FloatingPickerShell(
        title =
            selectedRouteId?.let { id -> routes.find { it.id == id }?.name }
                ?: stringResource(R.string.widget_panel_routes_title),
        onDismiss = onDismiss,
        hasBack = selectedRouteId != null,
        onBack = { selectedRouteId = null },
        searchEnabled = selectedRouteId == null && routes.isNotEmpty(),
        searchVisible = searchVisible,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onToggleSearch = {
            if (searchVisible) {
                searchVisible = false
                searchQuery = ""
            } else {
                searchVisible = true
            }
        },
        searchLabel = stringResource(R.string.widget_search_routes),
        sortMode = if (selectedRouteId == null) sortMode else null,
        onSortModeSelected = onSortModeSelected,
    ) {
        if (selectedRouteId != null) {
            val routeId = selectedRouteId!!
            val route = routes.find { it.id == routeId }
            val isTeleportRoute = route?.routeType == com.locationjoystick.core.model.RouteType.TELEPORT
            var loop by remember(routeId) { mutableStateOf(true) }
            var reverse by remember(routeId) { mutableStateOf(false) }
            var returnToLocation by remember(routeId) { mutableStateOf(false) }
            var followRoads by remember(routeId) { mutableStateOf(false) }
            var planting by remember(routeId) { mutableStateOf(false) }
            var teleportBetweenWaypoints by remember(routeId) { mutableStateOf(false) }
            var teleportBetweenDelaySecondsText by remember(routeId) {
                mutableStateOf(AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS.toString())
            }

            if (route != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = {
                            renameText = route.name
                            renamingRoute = route
                        },
                    ) { Icon(LjIcons.Edit, contentDescription = stringResource(R.string.widget_panel_content_rename_route)) }
                    IconButton(
                        onClick = {
                            val coordinateText = formatCapturedPointsForClipboard(route.waypoints.map { it.position })
                            onShareOpened()
                            context.startActivity(
                                Intent
                                    .createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, coordinateText)
                                        },
                                        shareRouteTitle,
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    ) { Icon(LjIcons.Share, contentDescription = stringResource(R.string.widget_panel_content_share_route)) }
                    IconButton(onClick = { deletingRoute = route }) {
                        Icon(LjIcons.Delete, contentDescription = stringResource(R.string.widget_panel_content_delete_route))
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                LjRouteStartOptions(
                    loop = loop,
                    onLoopChange = { loop = it },
                    reverse = reverse,
                    onReverseChange = { reverse = it },
                    returnToLocation = returnToLocation,
                    onReturnToLocationChange = { returnToLocation = it },
                    followRoads = followRoads,
                    onFollowRoadsChange = { followRoads = it },
                    planting = planting && !isTeleportRoute,
                    onPlantingChange = {
                        planting = it
                        if (it) returnToLocation = false
                    },
                    teleportBetweenWaypoints = teleportBetweenWaypoints && !hideTeleport && !isTeleportRoute,
                    onTeleportBetweenWaypointsChange = { teleportBetweenWaypoints = it },
                    teleportBetweenDelaySecondsText = teleportBetweenDelaySecondsText,
                    onTeleportBetweenDelaySecondsTextChange = { teleportBetweenDelaySecondsText = it },
                    onTeleport = {
                        route?.startWaypoint(reverse)?.let { onTeleport(it.position) }
                    },
                    onCancel = {
                        selectedRouteId = null
                        onDismiss()
                    },
                    onStart = {
                        onStartRoute(
                            routeId,
                            loop || (planting && !isTeleportRoute),
                            reverse,
                            returnToLocation && !loop && !planting,
                            followRoads && !isTeleportRoute,
                            planting && !isTeleportRoute,
                            teleportBetweenWaypoints && !hideTeleport && !isTeleportRoute,
                            parseTeleportBetweenDelaySeconds(teleportBetweenDelaySecondsText),
                        )
                        selectedRouteId = null
                        onDismiss()
                    },
                    hideTeleport = hideTeleport,
                    isTeleportRoute = isTeleportRoute,
                )
            }
        } else {
            RoutesPickerList(
                routes = routes,
                onSelect = { selectedRouteId = it.id },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
                enableSearch = false,
                filterQuery = searchQuery,
            )
        }
    }
    renamingRoute?.let { route ->
        AlertDialog(
            onDismissRequest = { renamingRoute = null },
            title = { Text(stringResource(R.string.widget_panel_content_rename_route)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.widget_panel_content_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        onRename(route, renameText.trim())
                        renamingRoute = null
                    },
                ) { Text(stringResource(R.string.widget_panel_content_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { renamingRoute = null },
                ) { Text(stringResource(R.string.widget_panel_content_cancel)) }
            },
        )
    }
    deletingRoute?.let { route ->
        AlertDialog(
            onDismissRequest = { deletingRoute = null },
            title = { Text(stringResource(R.string.widget_delete_route_title)) },
            text = { Text(route.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(route)
                        selectedRouteId = null
                        deletingRoute = null
                    },
                ) { Text(stringResource(R.string.widget_panel_content_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingRoute = null },
                ) { Text(stringResource(R.string.widget_panel_content_cancel)) }
            },
        )
    }
}
