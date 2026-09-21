package com.locationjoystick.feature.widget.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.DebugStats
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.RouteProgressBadge
import com.locationjoystick.core.designsystem.component.routeProgressStopContentDescription
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.feature.widget.impl.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tint for ignored joystick/roam icons: faded vs orange, still readable on the black circle. */
private val WidgetIgnoredTint = Color.White.copy(alpha = 0.42f)

/** Tint for inactive widget chrome icons. Mid-grey on the black circle is too close to the fill. */
private val WidgetInactiveTint = Color.White.copy(alpha = 0.82f)
private val WidgetColumnWidth = 50.dp
private val WidgetButtonSlotHeight = 52.dp
private val WidgetTouchTargetSize = 48.dp

/** Shared circular icon button for the widget panel: press scale + icon crossfade on state change. */
@Composable
private fun WidgetIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.96f else 1f,
        label = "widgetButtonPressScale",
    )
    val iconTint = if (enabled) tint else WidgetIgnoredTint
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.width(WidgetColumnWidth).height(WidgetButtonSlotHeight),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(WidgetTouchTargetSize)
                    .combinedClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(UiConstants.FAB_CONTAINER_SIZE)
                        .scale(scale)
                        .background(Color.Black, CircleShape),
            ) {
                Crossfade(
                    targetState = icon,
                    animationSpec = tween(150),
                    label = "widgetButtonIcon",
                ) { animatedIcon ->
                    Icon(
                        imageVector = animatedIcon,
                        contentDescription = contentDescription,
                        tint = iconTint,
                        modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
                    )
                }
            }
        }
    }
}

internal data class RouteControlsState(
    val expanded: Boolean,
    val isActive: Boolean,
    val isPaused: Boolean,
    val isPausable: Boolean,
    val isReplay: Boolean,
    val hideTeleportFeatures: Boolean,
    val showRouteJumpButtons: Boolean,
    val onIconClick: () -> Unit,
    val onPauseResume: () -> Unit,
    val onStop: () -> Unit,
    val onJumpNext: () -> Unit,
    val onJumpPrevious: () -> Unit,
)

internal data class RoamingControlsState(
    val expanded: Boolean,
    val isActive: Boolean,
    val isPaused: Boolean,
    val onIconClick: () -> Unit,
    val onPauseResume: () -> Unit,
    val onStop: () -> Unit,
)

internal data class MasterToggleState(
    val spoofingActive: Boolean,
    val stopPopupVisible: Boolean,
    val onToggle: () -> Unit,
    val onLongPress: () -> Unit,
    val onPark: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
)

internal data class PasteCaptureState(
    val expanded: Boolean,
    val onLongPress: () -> Unit,
    val onCaptureShortcut: () -> Unit,
)

internal sealed interface WidgetPanelSection {
    data class TapToWalk(
        val active: Boolean,
        val onClick: () -> Unit,
    ) : WidgetPanelSection

    data class AltitudeOverride(
        val expanded: Boolean,
        val prefillMeters: Double,
        val onClick: () -> Unit,
        val onConfirm: (Double) -> Unit,
    ) : WidgetPanelSection
}

@Composable
internal fun WidgetPanel(
    features: List<AppFeature>,
    joystickVisible: Boolean,
    joystickLocked: Boolean,
    activeProfileId: String,
    routeControls: RouteControlsState,
    roamingControls: RoamingControlsState,
    joystickInputIgnored: Boolean = false,
    roamingStartIgnored: Boolean = false,
    isPanelExpanded: Boolean,
    hasPendingCompletion: Boolean,
    masterToggle: MasterToggleState,
    onFeatureClicked: (AppFeature) -> Unit,
    pasteCapture: PasteCaptureState,
    sections: List<WidgetPanelSection>,
    debugStats: DebugStats? = null,
    routeProgress: RouteProgress? = null,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Column(horizontalAlignment = Alignment.Start) {
        // Master toggle icon — always visible; drag to reposition, tap to toggle panel
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .width(WidgetColumnWidth)
                    .height(WidgetButtonSlotHeight)
                    .pointerInput(Unit) {
                        coroutineScope {
                            val jobScope = this
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var isDragging = false
                                var longPressFired = false
                                var accumulatedDistance = 0f
                                val longPressJob =
                                    jobScope.launch {
                                        delay(viewConfiguration.longPressTimeoutMillis.toLong())
                                        if (!isDragging) {
                                            longPressFired = true
                                            masterToggle.onLongPress()
                                        }
                                    }
                                try {
                                    do {
                                        val event = awaitPointerEvent()
                                        val drag = event.changes.firstOrNull() ?: break
                                        val delta = drag.position - drag.previousPosition
                                        if (delta != Offset.Zero) {
                                            if (!isDragging) {
                                                accumulatedDistance += delta.getDistance()
                                                if (accumulatedDistance > viewConfiguration.touchSlop) {
                                                    isDragging = true
                                                    longPressJob.cancel()
                                                }
                                            }
                                            if (isDragging) {
                                                onDrag(delta.x, delta.y)
                                            }
                                            drag.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                } finally {
                                    longPressJob.cancel()
                                }
                                if (!isDragging && !longPressFired) {
                                    masterToggle.onToggle()
                                }
                            }
                        }
                    },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(UiConstants.FAB_CONTAINER_SIZE)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_launcher),
                    contentDescription =
                        stringResource(
                            if (isPanelExpanded) R.string.widget_panel_collapse_cd else R.string.widget_panel_expand_cd,
                        ),
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
            WidgetSidePopup(visible = masterToggle.stopPopupVisible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (widgetMasterPopupMode(masterToggle.spoofingActive)) {
                        WidgetMasterPopupMode.PAUSE_AND_STOP -> {
                            WidgetIconButton(
                                icon = LjIcons.Pause,
                                contentDescription = stringResource(R.string.widget_panel_content_pause_spoofing),
                                tint = WidgetInactiveTint,
                                onClick = masterToggle.onPark,
                            )
                        }

                        WidgetMasterPopupMode.START_AND_STOP -> {
                            WidgetIconButton(
                                icon = LjIcons.PlayArrow,
                                contentDescription = stringResource(R.string.widget_panel_content_start_spoofing),
                                tint = LjSuccess,
                                onClick = masterToggle.onStart,
                            )
                        }
                    }
                    WidgetIconButton(
                        icon = LjIcons.Stop,
                        contentDescription = stringResource(R.string.widget_panel_content_stop_spoofing),
                        tint = MaterialTheme.colorScheme.error,
                        enabled = widgetStopEnabled(),
                        onClick = masterToggle.onStop,
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = hasPendingCompletion,
                    enter = scaleIn(tween(150)) + fadeIn(tween(150)),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .background(Color.Red, CircleShape),
                    )
                }
            }
        }

        // Feature icons — only shown when panel expanded
        if (isPanelExpanded) {
            val controlsEnabled = widgetControlsEnabled(masterToggle.spoofingActive)
            features.forEach { feature ->
                if (feature == AppFeature.ROUTES) {
                    val routeIconTint = if (routeControls.isActive) LjSuccess else MaterialTheme.colorScheme.primary
                    Box {
                        WidgetIconButton(
                            icon = LjIcons.Route,
                            contentDescription = stringResource(R.string.widget_panel_routes_picker_cd),
                            tint = routeIconTint,
                            enabled = controlsEnabled,
                            onClick = routeControls.onIconClick,
                        )
                        WidgetSidePopup(visible = routeControls.isActive && routeControls.expanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (routeControls.isPausable) {
                                    val pauseResumeIcon = if (routeControls.isPaused) LjIcons.PlayArrow else LjIcons.Pause
                                    val pauseResumeTint = if (routeControls.isPaused) LjSuccess else WidgetInactiveTint
                                    WidgetIconButton(
                                        icon = pauseResumeIcon,
                                        contentDescription =
                                            stringResource(
                                                if (routeControls.isPaused) {
                                                    R.string.widget_panel_resume_cd
                                                } else {
                                                    R.string.widget_panel_pause_cd
                                                },
                                            ),
                                        tint = pauseResumeTint,
                                        enabled = controlsEnabled,
                                        onClick = routeControls.onPauseResume,
                                    )
                                }
                                WidgetIconButton(
                                    icon = LjIcons.Stop,
                                    contentDescription = stringResource(R.string.widget_panel_stop_cd),
                                    tint = MaterialTheme.colorScheme.error,
                                    enabled = controlsEnabled,
                                    onClick = routeControls.onStop,
                                )
                                if (routeControls.isReplay &&
                                    !routeControls.hideTeleportFeatures &&
                                    routeControls.showRouteJumpButtons
                                ) {
                                    WidgetIconButton(
                                        icon = LjIcons.SkipPrevious,
                                        contentDescription = stringResource(R.string.overlay_previous_waypoint_cd),
                                        tint = LjSuccess,
                                        enabled = controlsEnabled,
                                        onClick = routeControls.onJumpPrevious,
                                    )
                                    WidgetIconButton(
                                        icon = LjIcons.SkipNext,
                                        contentDescription = stringResource(R.string.overlay_next_waypoint_cd),
                                        tint = LjSuccess,
                                        enabled = controlsEnabled,
                                        onClick = routeControls.onJumpNext,
                                    )
                                }
                            }
                        }
                    }
                } else if (feature == AppFeature.ROAMING) {
                    val roamingTint =
                        when {
                            roamingControls.isActive -> LjSuccess
                            roamingStartIgnored -> WidgetIgnoredTint
                            else -> MaterialTheme.colorScheme.primary
                        }
                    Box {
                        WidgetIconButton(
                            icon = LjIcons.Explore,
                            contentDescription =
                                if (roamingStartIgnored) {
                                    stringResource(
                                        R.string.widget_roaming_ignored_a_route_is_playing,
                                    )
                                } else {
                                    stringResource(R.string.widget_roaming)
                                },
                            tint = roamingTint,
                            enabled = controlsEnabled,
                            onClick = roamingControls.onIconClick,
                        )
                        WidgetSidePopup(visible = roamingControls.isActive && roamingControls.expanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val pauseResumeIcon = if (roamingControls.isPaused) LjIcons.PlayArrow else LjIcons.Pause
                                val pauseResumeTint = if (roamingControls.isPaused) LjSuccess else WidgetInactiveTint
                                WidgetIconButton(
                                    icon = pauseResumeIcon,
                                    contentDescription =
                                        if (roamingControls.isPaused) {
                                            stringResource(
                                                R.string.widget_resume_roaming,
                                            )
                                        } else {
                                            stringResource(R.string.widget_pause_roaming)
                                        },
                                    tint = pauseResumeTint,
                                    enabled = controlsEnabled,
                                    onClick = roamingControls.onPauseResume,
                                )
                                WidgetIconButton(
                                    icon = LjIcons.Stop,
                                    contentDescription = stringResource(R.string.widget_panel_content_stop_roaming),
                                    tint = MaterialTheme.colorScheme.error,
                                    enabled = controlsEnabled,
                                    onClick = roamingControls.onStop,
                                )
                            }
                        }
                    }
                } else {
                    val (icon, active) =
                        featureIconAndState(
                            feature,
                            joystickVisible,
                            joystickLocked,
                            activeProfileId,
                        )
                    val iconTint =
                        when {
                            (feature == AppFeature.JOYSTICK_TOGGLE || feature == AppFeature.JOYSTICK_LOCK) &&
                                joystickInputIgnored -> WidgetIgnoredTint
                            active -> MaterialTheme.colorScheme.primary
                            else -> WidgetInactiveTint
                        }
                    Box {
                        WidgetIconButton(
                            icon = icon,
                            contentDescription =
                                if (joystickInputIgnored &&
                                    (feature == AppFeature.JOYSTICK_TOGGLE || feature == AppFeature.JOYSTICK_LOCK)
                                ) {
                                    "${feature.toContentDescription()} — won't move while a route or roam is playing"
                                } else {
                                    feature.toContentDescription()
                                },
                            tint = iconTint,
                            enabled = controlsEnabled,
                            // While spoofing, joystick show/lock still launch the overlay; movement no-ops.
                            onClick = { onFeatureClicked(feature) },
                            onLongClick = if (feature == AppFeature.PASTE_COORDINATES) pasteCapture.onLongPress else null,
                        )
                        if (feature == AppFeature.PASTE_COORDINATES) {
                            WidgetSidePopup(visible = pasteCapture.expanded) {
                                WidgetIconButton(
                                    icon = LjIcons.AddLocationAlt,
                                    contentDescription = stringResource(R.string.widget_panel_content_open_capture),
                                    tint = MaterialTheme.colorScheme.primary,
                                    enabled = controlsEnabled,
                                    onClick = pasteCapture.onCaptureShortcut,
                                )
                            }
                        }
                    }
                }
            }
            sections.forEach { section ->
                when (section) {
                    is WidgetPanelSection.TapToWalk -> {
                        val crosshairTint = if (section.active) MaterialTheme.colorScheme.primary else WidgetInactiveTint
                        WidgetIconButton(
                            icon = LjIcons.MyLocation,
                            contentDescription =
                                stringResource(
                                    if (section.active) {
                                        R.string.tap_to_walk_overlay_cancel_tap_to_walk_cd
                                    } else {
                                        R.string.widget_panel_tap_to_walk_cd
                                    },
                                ),
                            tint = crosshairTint,
                            enabled = controlsEnabled,
                            onClick = section.onClick,
                        )
                    }

                    is WidgetPanelSection.AltitudeOverride -> {
                        Box {
                            WidgetIconButton(
                                icon = LjIcons.Terrain,
                                contentDescription = stringResource(R.string.widget_panel_altitude_override_cd),
                                tint = if (section.expanded) LjSuccess else MaterialTheme.colorScheme.primary,
                                enabled = controlsEnabled,
                                onClick = section.onClick,
                            )
                            WidgetSidePopup(visible = section.expanded, focusable = true) {
                                AltitudeOverrideInput(prefillMeters = section.prefillMeters, onConfirm = section.onConfirm)
                            }
                        }
                    }
                }
            }
            val progress = routeProgress
            if (controlsEnabled && routeControls.isReplay && progress != null) {
                RouteProgressBadge(
                    label = progress.label,
                    contentDescription = routeProgressStopContentDescription(progress.current, progress.total),
                    // Same 4.dp inset as WidgetIconButton so the chip's left edge matches the icon column.
                    modifier = Modifier.padding(4.dp),
                )
            }
            if (controlsEnabled && debugStats != null) {
                DebugStatsPanel(debugStats)
            }
        }
    }
}

private fun featureIconAndState(
    feature: AppFeature,
    joystickVisible: Boolean,
    joystickLocked: Boolean,
    activeProfileId: String,
): Pair<ImageVector, Boolean> =
    when (feature) {
        AppFeature.JOYSTICK_TOGGLE -> {
            Pair(LjIcons.Visibility, joystickVisible)
        }

        AppFeature.JOYSTICK_LOCK -> {
            Pair(
                if (joystickLocked) LjIcons.Lock else LjIcons.LockOpen,
                joystickLocked,
            )
        }

        AppFeature.ROUTES -> {
            Pair(LjIcons.Route, true)
        }

        AppFeature.FAVORITES -> {
            Pair(LjIcons.Favorite, true)
        }

        AppFeature.SPEED_CYCLE -> {
            Pair(
                when (activeProfileId) {
                    AppConstants.ProfileConstants.PROFILE_ID_SLOW_WALK -> LjIcons.Hiking
                    AppConstants.ProfileConstants.PROFILE_ID_RUN -> LjIcons.DirectionsRun
                    AppConstants.ProfileConstants.PROFILE_ID_BIKE -> LjIcons.DirectionsBike
                    AppConstants.ProfileConstants.PROFILE_ID_DRIVE -> LjIcons.DirectionsCar
                    else -> LjIcons.DirectionsWalk
                },
                true,
            )
        }

        AppFeature.MAP_FLOATING -> {
            Pair(LjIcons.LocationOn, true)
        }

        AppFeature.PASTE_COORDINATES -> {
            Pair(LjIcons.ContentPaste, true)
        }

        AppFeature.ROAMING -> {
            Pair(LjIcons.Explore, true)
        }

        AppFeature.SEARCH, AppFeature.CAPTURE_COORDINATES -> {
            error("$feature is map-only and never appears in the widget panel")
        }
    }

@Composable
private fun AppFeature.toContentDescription(): String =
    when (this) {
        AppFeature.JOYSTICK_TOGGLE -> stringResource(R.string.widget_feature_show_hide_joystick_cd)
        AppFeature.JOYSTICK_LOCK -> stringResource(R.string.widget_feature_lock_joystick_cd)
        AppFeature.ROUTES -> stringResource(R.string.widget_panel_routes_picker_cd)
        AppFeature.FAVORITES -> stringResource(R.string.widget_feature_favorites_cd)
        AppFeature.SPEED_CYCLE -> stringResource(R.string.widget_feature_speed_cycle_cd)
        AppFeature.MAP_FLOATING -> stringResource(R.string.widget_feature_open_map_cd)
        AppFeature.PASTE_COORDINATES -> stringResource(R.string.widget_paste_coordinates)
        AppFeature.ROAMING, AppFeature.SEARCH, AppFeature.CAPTURE_COORDINATES ->
            error(
                "$this is map-only and never appears in the widget panel",
            )
    }
