package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.designsystem.component.FavoritesList
import com.locationjoystick.core.model.FavoriteLocation

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
            Text("Set Location")
        }
        Button(
            onClick = onGoToLocation,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text("Walk To Location")
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
