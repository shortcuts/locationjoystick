package com.locationjoystick.feature.map.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.locationjoystick.core.designsystem.LjAccent
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.LjMapIconButton
import com.locationjoystick.core.model.AppFeature

@Composable
internal fun MapFabColumn(
    uiState: MapUiState,
    isFollowingCamera: Boolean,
    onAction: (MapAction) -> Unit,
    onToggleSearch: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
        horizontalAlignment = Alignment.End,
    ) {
        if (!isFollowingCamera) {
            LjMapIconButton(
                icon = LjIcons.MyLocation,
                contentDescription = "Re-center on location",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { onAction(MapAction.RecenterCamera) },
            )
        }

        if (uiState.walkTarget != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
            ) {
                AnimatedVisibility(visible = uiState.isWalkControlsExpanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4)) {
                        LjMapIconButton(
                            icon = if (uiState.isWalkPaused) LjIcons.PlayArrow else LjIcons.Pause,
                            contentDescription = if (uiState.isWalkPaused) "Resume walk" else "Pause walk",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                if (uiState.isWalkPaused) {
                                    onAction(MapAction.ResumeWalk)
                                } else {
                                    onAction(MapAction.PauseWalk)
                                }
                            },
                        )
                        LjMapIconButton(
                            icon = LjIcons.Stop,
                            contentDescription = "Stop walk",
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            onClick = { onAction(MapAction.StopWalk) },
                        )
                    }
                }
                LjMapIconButton(
                    icon = LjIcons.DirectionsWalk,
                    contentDescription = "Walk active",
                    containerColor = LjAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { onAction(MapAction.ToggleWalkControls) },
                )
            }
        }

        uiState.mapFeatureOrder.forEach { feature ->
            val enabled = feature in uiState.enabledMapFeatures
            when (feature) {
                AppFeature.FAVORITES -> {
                    if (enabled) {
                        LjMapIconButton(
                            icon = LjIcons.Favorite,
                            contentDescription = "Open favorites",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { onAction(MapAction.OpenFavoritesPicker) },
                        )
                    }
                }

                // Routes — expandable: icon always, pause/stop expand when replay active
                AppFeature.ROUTES -> {
                    if (enabled || uiState.isRouteReplay) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
                        ) {
                            LjMapIconButton(
                                icon = LjIcons.Route,
                                contentDescription =
                                    if (uiState.isRouteReplay) "Route active" else "Open routes",
                                containerColor =
                                    if (uiState.isRouteReplay) LjSuccess else MaterialTheme.colorScheme.primaryContainer,
                                contentColor =
                                    if (uiState.isRouteReplay) LjBg else MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    if (uiState.isRouteReplay) {
                                        onAction(MapAction.ToggleRouteControls)
                                    } else {
                                        onAction(MapAction.OpenRoutesSheet)
                                    }
                                },
                            )
                            AnimatedVisibility(visible = uiState.isRouteReplay && uiState.isRouteControlsExpanded) {
                                Row(horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4)) {
                                    LjMapIconButton(
                                        icon = if (uiState.isRoutePaused) LjIcons.PlayArrow else LjIcons.Pause,
                                        contentDescription = if (uiState.isRoutePaused) "Resume route" else "Pause route",
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = {
                                            if (uiState.isRoutePaused) {
                                                onAction(MapAction.ResumeRouteReplay)
                                            } else {
                                                onAction(MapAction.PauseRouteReplay)
                                            }
                                        },
                                    )
                                    LjMapIconButton(
                                        icon = LjIcons.Stop,
                                        contentDescription = "Stop route",
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                        onClick = { onAction(MapAction.StopRouteReplay) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Roaming — collapsible: compass icon always, pause/resume+stop expand to the left when roaming active
                AppFeature.ROAMING -> {
                    if (enabled || uiState.isRoaming || uiState.isRoamingSheetMinimized) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
                        ) {
                            AnimatedVisibility(visible = uiState.isRoaming && uiState.isRoamingControlsExpanded) {
                                Row(horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4)) {
                                    LjMapIconButton(
                                        icon = LjIcons.Stop,
                                        contentDescription = "Stop roaming",
                                        containerColor = Color.Black,
                                        contentColor = MaterialTheme.colorScheme.error,
                                        onClick = { onAction(MapAction.StopRoaming) },
                                    )
                                    LjMapIconButton(
                                        icon = if (uiState.isRoamingPaused) LjIcons.PlayArrow else LjIcons.Pause,
                                        contentDescription = if (uiState.isRoamingPaused) "Resume roaming" else "Pause roaming",
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = {
                                            if (uiState.isRoamingPaused) {
                                                onAction(MapAction.ResumeRoaming)
                                            } else {
                                                onAction(MapAction.PauseRoaming)
                                            }
                                        },
                                    )
                                }
                            }
                            LjMapIconButton(
                                icon = LjIcons.Explore,
                                contentDescription =
                                    when {
                                        uiState.isRoaming -> "Roaming active"
                                        uiState.isRoamingSheetMinimized -> "Expand roaming sheet"
                                        else -> "Start roaming"
                                    },
                                containerColor =
                                    when {
                                        uiState.isRoaming -> Color.Black
                                        uiState.isRoamingSheetMinimized -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                contentColor =
                                    when {
                                        uiState.isRoaming -> LjSuccess
                                        uiState.isRoamingSheetMinimized -> MaterialTheme.colorScheme.onTertiary
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                onClick = {
                                    when {
                                        uiState.isRoaming -> onAction(MapAction.ToggleRoamingControls)
                                        uiState.isRoamingSheetMinimized -> onAction(MapAction.ExpandRoamingSheet)
                                        else -> onAction(MapAction.OpenRoamingSheet)
                                    }
                                },
                            )
                        }
                    }
                }

                AppFeature.SEARCH -> {
                    if (enabled) {
                        LjMapIconButton(
                            icon = LjIcons.Search,
                            contentDescription = "Search location",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = onToggleSearch,
                        )
                    }
                }

                else -> {
                    Unit
                }
            }
        }

        // ClearMap — visible when there's clearable content
        val hasClearableContent =
            !uiState.isRoaming &&
                (
                    uiState.roamingPreviewWaypoints != null ||
                        uiState.ephemeralWaypoints.isNotEmpty() ||
                        uiState.walkTarget != null ||
                        uiState.pendingTapPosition != null
                )
        if (hasClearableContent) {
            LjMapIconButton(
                icon = LjIcons.Delete,
                contentDescription = "Clear map",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = { onAction(MapAction.ClearMap) },
            )
        }
    }
}
