package com.locationjoystick.feature.map.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.UiConstants

@Composable
internal fun MapFabColumn(
    uiState: MapUiState,
    isFollowingCamera: Boolean,
    onAction: (MapAction) -> Unit,
    onNavigateToRoutes: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4)) {
        if (!isFollowingCamera) {
            MapIconButton(
                icon = LjIcons.MyLocation,
                contentDescription = "Re-center on location",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = { onAction(MapAction.RecenterCamera) },
            )
        }

        if (uiState.walkTarget != null) {
            MapIconButton(
                icon = LjIcons.Stop,
                contentDescription = "Stop walk",
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = { onAction(MapAction.StopWalk) },
            )
            MapIconButton(
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
        }

        // Start/Stop spoof — always visible
        val isSpoofing = uiState.isSpoofing
        MapIconButton(
            icon = if (isSpoofing) LjIcons.Stop else LjIcons.PlayArrow,
            contentDescription = if (isSpoofing) "Stop location simulation" else "Start location simulation",
            containerColor = if (isSpoofing) MaterialTheme.colorScheme.error else Color(AppConstants.MapColorConstants.ACTIVE_BUTTON_COLOR),
            contentColor = if (isSpoofing) MaterialTheme.colorScheme.onError else Color.White,
            onClick = { if (isSpoofing) onAction(MapAction.StopSpoofing) else onAction(MapAction.StartSpoofing) },
        )

        // Favorites — always visible
        MapIconButton(
            icon = LjIcons.Favorite,
            contentDescription = "Open favorites",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onAction(MapAction.OpenFavoritesPicker) },
        )

        // Routes — always visible (new)
        MapIconButton(
            icon = LjIcons.Route,
            contentDescription = "Go to routes",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onNavigateToRoutes,
        )

        // Roaming — collapsible: compass icon always, pause/resume+stop expand when roaming active
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4),
        ) {
            MapIconButton(
                icon = LjIcons.Explore,
                contentDescription =
                    when {
                        uiState.isRoaming -> "Roaming active"
                        uiState.isRoamingSheetMinimized -> "Expand roaming sheet"
                        else -> "Start roaming"
                    },
                containerColor =
                    when {
                        uiState.isRoaming -> Color(0xFF388E3C)
                        uiState.isRoamingSheetMinimized -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                contentColor =
                    when {
                        uiState.isRoaming || uiState.isRoamingSheetMinimized -> Color.White
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    },
                onClick = {
                    when {
                        uiState.isRoamingSheetMinimized -> onAction(MapAction.ExpandRoamingSheet)
                        !uiState.isRoaming -> onAction(MapAction.OpenRoamingSheet)
                    }
                },
            )

            AnimatedVisibility(visible = uiState.isRoaming) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiConstants.FAB_CONTAINER_SIZE / 4)) {
                    MapIconButton(
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
                    MapIconButton(
                        icon = LjIcons.Stop,
                        contentDescription = "Stop roaming",
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        onClick = { onAction(MapAction.StopRoaming) },
                    )
                }
            }
        }

        // Search — always visible
        MapIconButton(
            icon = Icons.Default.Search,
            contentDescription = "Search location",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onToggleSearch,
        )

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
            MapIconButton(
                icon = LjIcons.Delete,
                contentDescription = "Clear map",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClick = { onAction(MapAction.ClearMap) },
            )
        }
    }
}

@Composable
private fun MapIconButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(UiConstants.FAB_CONTAINER_SIZE)
                .background(containerColor, CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
        )
    }
}
