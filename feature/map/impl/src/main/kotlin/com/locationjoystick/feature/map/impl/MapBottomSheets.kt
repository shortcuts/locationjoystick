package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.toBadgeText
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.CooldownAdvisoryBadge
import com.locationjoystick.core.designsystem.component.FavoriteTargetDetail
import com.locationjoystick.core.designsystem.component.FavoritesList
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.designsystem.component.PasteCoordinatesForm
import com.locationjoystick.core.designsystem.component.RouteStartSheetContent
import com.locationjoystick.core.designsystem.component.RoutesPickerList
import com.locationjoystick.core.designsystem.component.rememberLjSheetState
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.startWaypoint
import com.locationjoystick.feature.map.impl.R

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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val routeId = selectedRouteId
        if (routeId != null) {
            val route = uiState.routes.find { it.id == routeId }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedRouteId = null }) {
                        Icon(LjIcons.ArrowBack, contentDescription = stringResource(R.string.map_sheet_back_cd))
                    }
                    Text(
                        text = route?.name ?: stringResource(R.string.map_bottom_sheets_start_route),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.height(8.dp))
                RouteStartSheetContent(
                    key = routeId,
                    onTeleport = { reverse ->
                        route?.startWaypoint(reverse)?.let { onAction(MapAction.ConfirmTeleport(it.position)) }
                    },
                    onStart = {
                        loop,
                        reverse,
                        returnToLocation,
                        followRoads,
                        planting,
                        teleportBetweenWaypoints,
                        delaySeconds,
                        ->
                        onAction(
                            MapAction.StartRouteReplay(
                                routeId,
                                loop,
                                reverse,
                                returnToLocation,
                                followRoads,
                                planting,
                                teleportBetweenWaypoints,
                                delaySeconds,
                            ),
                        )
                        if (!followRoads) selectedRouteId = null
                    },
                    onCancel = {
                        selectedRouteId = null
                        onAction(MapAction.CloseRoutesSheet)
                    },
                    hideTeleport = uiState.hideTeleportFeatures,
                    isTeleportRoute = route?.routeType == RouteType.TELEPORT,
                    isRoadRouteFetchInFlight = uiState.isRoadRouteFetchInFlight,
                )
            }
        } else {
            RoutesPickerList(
                routes = uiState.routes,
                onSelect = { selectedRouteId = it.id },
                title = stringResource(R.string.map_bottom_sheets_routes),
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
        sheetState = rememberLjSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val target = uiState.favoriteTarget
        if (target == null) {
            FavoritesList(
                title = stringResource(R.string.map_bottom_sheets_favorites),
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
                isRoadRouteFetchInFlight = uiState.isRoadRouteFetchInFlight,
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
internal fun PasteCoordinatesSheet(
    onDismiss: () -> Unit,
    onTeleport: (LatLng) -> Unit,
    onWalk: (LatLng) -> Unit,
    onWalkViaRoads: (LatLng) -> Unit,
    onSaveFavorite: (name: String, position: LatLng) -> Unit,
    onSaveRoute: (name: String, points: List<LatLng>) -> Unit,
    onStartRoute: (points: List<LatLng>, config: RouteStartConfig) -> Unit,
    hideTeleportFeatures: Boolean = false,
    title: String? = null,
    initialText: String = "",
    initialRouteName: String = "",
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    ModalBottomSheet(
        onDismissRequest = {
            if (shouldHonorPasteSheetDismiss(lifecycleOwner.lifecycle.currentState)) {
                onDismiss()
            }
        },
        sheetState = rememberLjSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        PasteCoordinatesForm(
            onDismiss = onDismiss,
            onTeleport = onTeleport,
            onWalk = onWalk,
            onWalkViaRoads = onWalkViaRoads,
            onSaveFavorite = onSaveFavorite,
            onSaveRoute = onSaveRoute,
            onStartRoute = onStartRoute,
            hideTeleportFeatures = hideTeleportFeatures,
            title = title,
            initialText = initialText,
            initialRouteName = initialRouteName,
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
    isRoadRouteFetchInFlight: Boolean = false,
) {
    var isAwaitingRoadWalk by remember { mutableStateOf(false) }
    LaunchedEffect(isRoadRouteFetchInFlight) {
        if (isAwaitingRoadWalk && !isRoadRouteFetchInFlight) {
            onAction(MapAction.ClearPendingTap)
            isAwaitingRoadWalk = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onAction(MapAction.ClearPendingTap) },
        sheetState = rememberLjSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (isRouteReplay && !isEphemeralReplay) {
                Text(stringResource(R.string.map_sheet_route_in_progress), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                if (!hideTeleportFeatures) {
                    LjButton(
                        onClick = { onAction(MapAction.StopRouteAndTeleport(position)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.map_sheet_stop_route_and_teleport))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LjOutlinedButton(
                    onClick = { onAction(MapAction.StopRouteAndWalkTo(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.map_sheet_stop_route_and_walk_here))
                }
                Spacer(Modifier.height(8.dp))
                LjOutlinedButton(
                    onClick = { onAction(MapAction.FinishRouteAndWalkTo(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.map_sheet_finish_route_and_walk_here))
                }
            } else {
                Text(stringResource(R.string.map_sheet_move_to_this_location), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                CooldownAdvisoryBadge(
                    (cooldownState as? CooldownState.Cooling)?.toAdvisoryLabel()
                        ?: stringResource(R.string.map_sheet_no_wait_needed),
                )
                Spacer(Modifier.height(8.dp))
                if (!hideTeleportFeatures) {
                    LjButton(
                        onClick = { onAction(MapAction.ConfirmTeleport(position)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.map_sheet_teleport_here))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LjOutlinedButton(
                        onClick = {
                            onAction(MapAction.LongPressTapToWalk(position))
                            onAction(MapAction.ClearPendingTap)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.map_sheet_walk_here))
                    }
                    LjOutlinedButton(
                        onClick = {
                            isAwaitingRoadWalk = true
                            onAction(MapAction.WalkViaRoadsTo(position))
                        },
                        enabled = !isRoadRouteFetchInFlight,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isRoadRouteFetchInFlight) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.map_sheet_walk_via_roads))
                        }
                    }
                }
                if (isWalkActive) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LjOutlinedButton(
                            onClick = { onAction(MapAction.AddEphemeralWaypoint(position, followRoads = false)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.map_sheet_add_next_point))
                        }
                        LjOutlinedButton(
                            onClick = { onAction(MapAction.AddEphemeralWaypoint(position, followRoads = true)) },
                            enabled = !isRoadRouteFetchInFlight,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isRoadRouteFetchInFlight) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.map_sheet_add_next_point_via_roads))
                            }
                        }
                    }
                }
            }
            if (onShare != null) {
                Spacer(Modifier.height(8.dp))
                LjOutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.map_sheet_share_this_location))
                }
            }
            Spacer(Modifier.height(4.dp))
            LjTextButton(
                onClick = { onAction(MapAction.ClearPendingTap) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.map_sheet_close))
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
        title = { Text(stringResource(R.string.map_sheet_save_current_location)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.map_sheet_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            LjTextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.map_sheet_save))
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.map_sheet_cancel))
            }
        },
    )
}
