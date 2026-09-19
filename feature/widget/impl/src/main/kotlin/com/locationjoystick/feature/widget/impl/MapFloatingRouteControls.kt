package com.locationjoystick.feature.widget.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.component.LjMapIconButton
import com.locationjoystick.core.model.AppFeature

/**
 * Route replay controls (Stop/Pause/Resume/jump) plus the Route toggle icon on the floating
 * map's FAB column. Renders nothing when routes are disabled for this surface and no replay
 * is active.
 */
@Composable
internal fun MapFloatingRouteControlsRow(
    enabledMapFabFeatures: Set<AppFeature>,
    isRouteReplay: Boolean,
    isRouteControlsExpanded: Boolean,
    isRoutePaused: Boolean,
    hideTeleportFeatures: Boolean,
    showRouteJumpButtons: Boolean,
    onRouteControlsExpandedChange: (Boolean) -> Unit,
    onStopRouteReplay: () -> Unit,
    onPauseRouteReplay: () -> Unit,
    onResumeRouteReplay: () -> Unit,
    onJumpToPreviousWaypoint: () -> Unit,
    onJumpToNextWaypoint: () -> Unit,
    onOpenRoutes: () -> Unit,
) {
    if (AppFeature.ROUTES in enabledMapFabFeatures || isRouteReplay) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = isRouteReplay && isRouteControlsExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LjMapIconButton(
                        icon = LjIcons.Stop,
                        contentDescription = stringResource(R.string.overlay_stop_route_cd),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        onClick = {
                            onRouteControlsExpandedChange(false)
                            onStopRouteReplay()
                        },
                    )
                    LjMapIconButton(
                        icon = if (isRoutePaused) LjIcons.PlayArrow else LjIcons.Pause,
                        contentDescription =
                            stringResource(
                                if (isRoutePaused) {
                                    R.string.overlay_resume_route_cd
                                } else {
                                    R.string.overlay_pause_route_cd
                                },
                            ),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { if (isRoutePaused) onResumeRouteReplay() else onPauseRouteReplay() },
                    )
                    if (!hideTeleportFeatures && showRouteJumpButtons) {
                        LjMapIconButton(
                            icon = LjIcons.SkipPrevious,
                            contentDescription = stringResource(R.string.overlay_previous_waypoint_cd),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = LjSuccess,
                            onClick = onJumpToPreviousWaypoint,
                        )
                        LjMapIconButton(
                            icon = LjIcons.SkipNext,
                            contentDescription = stringResource(R.string.overlay_next_waypoint_cd),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = LjSuccess,
                            onClick = onJumpToNextWaypoint,
                        )
                    }
                }
            }
            LjMapIconButton(
                icon = LjIcons.Route,
                contentDescription =
                    stringResource(
                        if (isRouteReplay) {
                            R.string.overlay_route_active_cd
                        } else {
                            R.string.overlay_open_routes_cd
                        },
                    ),
                containerColor = if (isRouteReplay) LjSuccess else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isRouteReplay) LjBg else MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    if (isRouteReplay) {
                        onRouteControlsExpandedChange(!isRouteControlsExpanded)
                    } else {
                        onOpenRoutes()
                    }
                },
            )
        }
    }
}
