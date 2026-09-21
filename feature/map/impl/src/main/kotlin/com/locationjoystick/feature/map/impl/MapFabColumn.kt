package com.locationjoystick.feature.map.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.locationjoystick.core.designsystem.LjAccent
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.LjMapIconButton
import com.locationjoystick.core.designsystem.component.RouteProgressBadgeInMapFabSlot
import com.locationjoystick.core.designsystem.component.routeProgressStopContentDescription
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.feature.map.impl.R

@Composable
internal fun MapFabColumn(
    uiState: MapUiState,
    isFollowingCamera: Boolean,
    lastFollowedPosition: LatLng?,
    onAction: (MapAction) -> Unit,
    onToggleSearch: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
        horizontalAlignment = Alignment.End,
    ) {
        LjMapIconButton(
            icon = LjIcons.MyLocation,
            contentDescription =
                if (
                    uiState.mockLocationState == MockLocationState.IDLE ||
                    uiState.mockLocationState == MockLocationState.ERROR
                ) {
                    stringResource(R.string.map_fab_recenter_gps_cd)
                } else {
                    stringResource(R.string.map_fab_recenter_mock_cd)
                },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor =
                if (isFollowingCamera) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onAction(MapAction.RecenterCamera(lastFollowedPosition)) },
        )

        if (uiState.walkTarget != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
            ) {
                AnimatedVisibility(visible = uiState.isWalkControlsExpanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4)) {
                        LjMapIconButton(
                            icon = if (uiState.isWalkPaused) LjIcons.PlayArrow else LjIcons.Pause,
                            contentDescription =
                                stringResource(
                                    if (uiState.isWalkPaused) {
                                        R.string.map_fab_resume_walk_cd
                                    } else {
                                        R.string.map_fab_pause_walk_cd
                                    },
                                ),
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
                            contentDescription = stringResource(R.string.map_fab_stop_walk_cd),
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            onClick = { onAction(MapAction.StopWalk) },
                        )
                    }
                }
                LjMapIconButton(
                    icon = LjIcons.DirectionsWalk,
                    contentDescription = stringResource(R.string.map_fab_walk_in_progress_cd),
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
                            contentDescription = stringResource(R.string.map_fab_open_favorites_cd),
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
                                    stringResource(
                                        if (uiState.isRouteReplay) {
                                            R.string.map_fab_route_active_cd
                                        } else {
                                            R.string.map_fab_open_routes_cd
                                        },
                                    ),
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
                                        contentDescription =
                                            stringResource(
                                                if (uiState.isRoutePaused) {
                                                    R.string.map_fab_resume_route_cd
                                                } else {
                                                    R.string.map_fab_pause_route_cd
                                                },
                                            ),
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
                                        contentDescription = stringResource(R.string.map_fab_stop_route_cd),
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                        onClick = { onAction(MapAction.StopRouteReplay) },
                                    )
                                    if (!uiState.hideTeleportFeatures && uiState.showRouteJumpButtons) {
                                        LjMapIconButton(
                                            icon = LjIcons.SkipPrevious,
                                            contentDescription = stringResource(R.string.map_fab_previous_waypoint_cd),
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = LjSuccess,
                                            onClick = { onAction(MapAction.JumpToPreviousWaypoint) },
                                        )
                                        LjMapIconButton(
                                            icon = LjIcons.SkipNext,
                                            contentDescription = stringResource(R.string.map_fab_next_waypoint_cd),
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = LjSuccess,
                                            onClick = { onAction(MapAction.JumpToNextWaypoint) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Roaming — collapsible: compass icon always, pause/resume+stop expand to the left when roaming active
                AppFeature.ROAMING -> {
                    val routePlaying =
                        uiState.isRouteReplay && uiState.mockLocationState == MockLocationState.RUNNING
                    if (enabled || uiState.isRoaming || uiState.isRoamingSheetMinimized) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
                        ) {
                            AnimatedVisibility(visible = uiState.isRoaming && uiState.isRoamingControlsExpanded) {
                                Row(horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4)) {
                                    LjMapIconButton(
                                        icon = LjIcons.Stop,
                                        contentDescription = stringResource(R.string.map_fab_stop_roaming_cd),
                                        containerColor = LjBg,
                                        contentColor = MaterialTheme.colorScheme.error,
                                        onClick = { onAction(MapAction.StopRoaming) },
                                    )
                                    LjMapIconButton(
                                        icon = if (uiState.isRoamingPaused) LjIcons.PlayArrow else LjIcons.Pause,
                                        contentDescription =
                                            stringResource(
                                                if (uiState.isRoamingPaused) {
                                                    R.string.map_fab_resume_roaming_cd
                                                } else {
                                                    R.string.map_fab_pause_roaming_cd
                                                },
                                            ),
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
                                        uiState.isRoaming -> stringResource(R.string.map_fab_roaming_active_cd)
                                        routePlaying -> stringResource(R.string.map_fab_roaming_ignored_route_playing_cd)
                                        uiState.isRoamingSheetMinimized ->
                                            stringResource(R.string.map_fab_expand_roaming_sheet_cd)
                                        else -> stringResource(R.string.map_fab_start_roaming_cd)
                                    },
                                containerColor =
                                    when {
                                        uiState.isRoaming -> LjBg
                                        uiState.isRoamingSheetMinimized -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                contentColor =
                                    when {
                                        uiState.isRoaming -> LjSuccess
                                        routePlaying -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f)
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
                            contentDescription = stringResource(R.string.map_fab_search_location_cd),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = onToggleSearch,
                        )
                    }
                }

                AppFeature.PASTE_COORDINATES -> {
                    if (enabled) {
                        LjMapIconButton(
                            icon = LjIcons.ContentPaste,
                            contentDescription = stringResource(R.string.map_fab_column_paste_coordinates),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { onAction(MapAction.OpenPasteCoordinates) },
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
                contentDescription = stringResource(R.string.map_fab_clear_map_cd),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = { onAction(MapAction.ClearMap) },
            )
        }

        val progress = uiState.routeProgress
        if (uiState.isRouteReplay && progress != null) {
            RouteProgressBadgeInMapFabSlot(
                label = progress.label,
                contentDescription = routeProgressStopContentDescription(progress.current, progress.total),
            )
        }
    }
}
