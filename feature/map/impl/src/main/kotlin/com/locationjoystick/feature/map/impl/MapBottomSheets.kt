package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.FavoritesList
import com.locationjoystick.core.model.FavoriteLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutesPickerSheet(
    uiState: MapUiState,
    onAction: (MapAction) -> Unit,
) {
    var selectedRouteId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { onAction(MapAction.CloseRoutesSheet) },
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = "Routes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uiState.routes.isEmpty()) {
                Text(
                    text = "No routes saved",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.routes, key = { it.id }) { route ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = route.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "${route.waypoints.size} waypoints",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(onClick = { selectedRouteId = route.id }) {
                                Icon(LjIcons.PlayArrow, contentDescription = "Start route")
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRouteId?.let { routeId ->
        StartRouteDialog(
            onDismiss = { selectedRouteId = null },
            onStart = { isLooping, isReverse, isReturnToLocation, teleportToStart ->
                onAction(MapAction.StartRouteReplay(routeId, isLooping, isReverse, isReturnToLocation, teleportToStart))
                selectedRouteId = null
            },
        )
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
            )
        } else {
            FavoriteTargetDetail(
                favorite = target,
                onSetLocation = { onAction(MapAction.SetLocationTo(target.position)) },
                onGoToLocation = { onAction(MapAction.WalkStraightTo(target.position)) },
                onGoToLocationViaRoads = { onAction(MapAction.WalkViaRoadsTo(target.position)) },
                onDismiss = { onAction(MapAction.CloseFavoritesPicker) },
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
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(MapAction.ClearPendingTap) },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (isRouteReplay) {
                Text("Route in progress", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onAction(MapAction.StopRouteAndTeleport(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop route and teleport")
                }
                Spacer(Modifier.height(8.dp))
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
                if (cooldownState is CooldownState.Cooling) {
                    Spacer(Modifier.height(12.dp))
                    val distKm = cooldownState.distanceMeters / 1000.0
                    val distLabel = if (distKm >= 1.0) "%.1f km".format(distKm) else "%.0f m".format(cooldownState.distanceMeters)
                    val remaining = cooldownState.remainingSeconds
                    val hours = remaining / AppConstants.TimeConstants.SECONDS_PER_HOUR
                    val minutes = (remaining % AppConstants.TimeConstants.SECONDS_PER_HOUR) / AppConstants.TimeConstants.SECONDS_PER_MINUTE
                    val seconds = remaining % AppConstants.TimeConstants.SECONDS_PER_MINUTE
                    val timeLabel =
                        when {
                            hours > 0 -> "%dh %dm".format(hours, minutes)
                            minutes > 0 -> "%dm %ds".format(minutes, seconds)
                            else -> "%ds".format(seconds)
                        }
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Suggested wait: $timeLabel · $distLabel teleport",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onAction(MapAction.ConfirmTeleport(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Teleport here")
                }
                Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { onAction(MapAction.ClearPendingTap) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Do nothing")
            }
        }
    }
}

@Composable
internal fun FavoriteTargetDetail(
    favorite: FavoriteLocation,
    onSetLocation: () -> Unit,
    onGoToLocation: () -> Unit,
    onGoToLocationViaRoads: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Text(favorite.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${String.format("%.4f", favorite.position.latitude)}, " +
                "${String.format("%.4f", favorite.position.longitude)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        Button(
            onClick = onSetLocation,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
        ) {
            Text("Set location")
        }
        OutlinedButton(
            onClick = onGoToLocation,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text("Walk to location")
        }
        OutlinedButton(
            onClick = onGoToLocationViaRoads,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text("Walk via roads")
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Do nothing")
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

@Composable
private fun StartRouteDialog(
    onDismiss: () -> Unit,
    onStart: (isLooping: Boolean, isReverse: Boolean, isReturnToLocation: Boolean, teleportToStart: Boolean) -> Unit,
) {
    var loop by remember { mutableStateOf(false) }
    var reverse by remember { mutableStateOf(false) }
    var returnToLocation by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Start route", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                CheckboxRow(label = "Loop", checked = loop, enabled = !returnToLocation, onCheckedChange = { loop = it })
                CheckboxRow(label = "Reverse", checked = reverse, onCheckedChange = { reverse = it })
                CheckboxRow(
                    label = "Return to location",
                    checked = returnToLocation,
                    enabled = !loop,
                    onCheckedChange = { returnToLocation = it },
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onStart(loop, reverse, returnToLocation && !loop, false) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Walk and start")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onStart(loop, reverse, returnToLocation && !loop, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Teleport and start")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(
            text = label,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
