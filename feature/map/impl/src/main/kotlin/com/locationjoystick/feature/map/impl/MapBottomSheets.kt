package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.toBadgeText
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.CooldownAdvisoryBadge
import com.locationjoystick.core.designsystem.component.FavoriteTargetDetail
import com.locationjoystick.core.designsystem.component.FavoritesList
import com.locationjoystick.core.designsystem.component.LjRouteStartOptions
import com.locationjoystick.core.designsystem.component.RoutesPickerList
import com.locationjoystick.core.model.startWaypoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutesPickerSheet(
    uiState: MapUiState,
    onAction: (MapAction) -> Unit,
) {
    var selectedRouteId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = {
            selectedRouteId = null
            onAction(MapAction.CloseRoutesSheet)
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val routeId = selectedRouteId
        if (routeId != null) {
            val route = uiState.routes.find { it.id == routeId }
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedRouteId = null }) {
                        Icon(LjIcons.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = route?.name ?: "Start route",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                var loop by remember(routeId) { mutableStateOf(false) }
                var reverse by remember(routeId) { mutableStateOf(false) }
                var returnToLocation by remember(routeId) { mutableStateOf(false) }
                var followRoads by remember(routeId) { mutableStateOf(false) }
                LjRouteStartOptions(
                    loop = loop,
                    onLoopChange = { loop = it },
                    reverse = reverse,
                    onReverseChange = { reverse = it },
                    returnToLocation = returnToLocation,
                    onReturnToLocationChange = { returnToLocation = it },
                    followRoads = followRoads,
                    onFollowRoadsChange = { followRoads = it },
                    onTeleport = {
                        route?.startWaypoint(reverse)?.let { onAction(MapAction.Teleport(it.position)) }
                    },
                    onCancel = {
                        selectedRouteId = null
                        onAction(MapAction.CloseRoutesSheet)
                    },
                    onStart = {
                        onAction(
                            MapAction.StartRouteReplay(routeId, loop, reverse, returnToLocation && !loop, followRoads),
                        )
                        selectedRouteId = null
                    },
                    hideTeleport = uiState.hideTeleportFeatures,
                )
            }
        } else {
            RoutesPickerList(
                routes = uiState.routes,
                onSelect = { selectedRouteId = it.id },
                title = "Routes",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesPickerSheet(
    uiState: MapUiState,
    onAction: (MapAction) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onAction(MapAction.CloseFavoritesPicker) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val target = uiState.favoriteTarget
        if (target == null) {
            FavoritesList(
                title = "Favorites",
                favorites = uiState.favorites,
                onSelect = { onAction(MapAction.SelectFavorite(it)) },
                onSaveCurrentLocation =
                    if (uiState.currentPosition != null) {
                        { showSaveDialog = true }
                    } else {
                        null
                    },
                cooldownBadgeText = { fav ->
                    (uiState.favoriteCooldownStates[fav.id] ?: CooldownState.Ready)
                        .toBadgeText(uiState.currentPosition, fav.position)
                },
            )
        } else {
            FavoriteTargetDetail(
                favorite = target,
                onSetLocation = { onAction(MapAction.SetLocationTo(target.position)) },
                onGoToLocation = { onAction(MapAction.WalkStraightTo(target.position)) },
                onGoToLocationViaRoads = { onAction(MapAction.WalkViaRoadsTo(target.position)) },
                onDismiss = { onAction(MapAction.CloseFavoritesPicker) },
                hideTeleportFeatures = uiState.hideTeleportFeatures,
            )
        }
    }

    if (showSaveDialog) {
        SaveCurrentLocationDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onAction(MapAction.SaveCurrentLocation(name))
                showSaveDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PendingTapSheet(
    position: com.locationjoystick.core.model.LatLng,
    isRouteReplay: Boolean,
    isWalkActive: Boolean,
    cooldownState: CooldownState,
    onAction: (MapAction) -> Unit,
    isEphemeralReplay: Boolean = false,
    onShare: (() -> Unit)? = null,
    hideTeleportFeatures: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(MapAction.ClearPendingTap) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (isRouteReplay && !isEphemeralReplay) {
                Text("Route in progress", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                if (!hideTeleportFeatures) {
                    Button(
                        onClick = { onAction(MapAction.StopRouteAndTeleport(position)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Stop route and teleport")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { onAction(MapAction.StopRouteAndWalkTo(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop route and walk here")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onAction(MapAction.FinishRouteAndWalkTo(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Finish route and walk here")
                }
            } else {
                Text("Move to this location?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                CooldownAdvisoryBadge(
                    (cooldownState as? CooldownState.Cooling)?.toAdvisoryLabel() ?: "No wait needed",
                )
                Spacer(Modifier.height(16.dp))
                if (!hideTeleportFeatures) {
                    Button(
                        onClick = { onAction(MapAction.ConfirmTeleport(position)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Teleport here")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = {
                        onAction(MapAction.LongPressTapToWalk(position))
                        onAction(MapAction.ClearPendingTap)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Walk here")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onAction(MapAction.WalkViaRoadsTo(position))
                        onAction(MapAction.ClearPendingTap)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Walk here via roads")
                }
                if (isWalkActive) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onAction(MapAction.AddEphemeralWaypoint(position)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add next point")
                    }
                }
            }
            if (onShare != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Share this location")
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { onAction(MapAction.ClearPendingTap) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
internal fun SaveCurrentLocationDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save current location") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onSave(name.trim())
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
