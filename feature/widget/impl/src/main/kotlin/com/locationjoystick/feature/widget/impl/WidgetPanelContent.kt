package com.locationjoystick.feature.widget.impl

import android.content.Intent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.currentLocationShareText
import com.locationjoystick.core.common.util.formatCapturedPointsForClipboard
import com.locationjoystick.core.common.util.parseTeleportBetweenDelaySeconds
import com.locationjoystick.core.common.util.shareCurrentLocationCoordinates
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.DebugStats
import com.locationjoystick.core.data.toBadgeText
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.FavoriteTargetDetail
import com.locationjoystick.core.designsystem.component.FavoritesList
import com.locationjoystick.core.designsystem.component.ListSearchField
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjRouteStartOptions
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.designsystem.component.PasteCoordinatesForm
import com.locationjoystick.core.designsystem.component.RoamingSheetContent
import com.locationjoystick.core.designsystem.component.RouteProgressBadge
import com.locationjoystick.core.designsystem.component.RoutesPickerList
import com.locationjoystick.core.designsystem.component.SavedItemSortMenu
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.SavedItemSortMode
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.startWaypoint
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

internal sealed interface WidgetPanelSection {
    data class TapToWalk(
        val active: Boolean,
        val onClick: () -> Unit,
    ) : WidgetPanelSection

    data class GroupSync(
        val expanded: Boolean,
        val onClick: () -> Unit,
        val onTeleport: () -> Unit,
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
    stopPopupVisible: Boolean,
    spoofingActive: Boolean,
    onToggleMaster: () -> Unit,
    onLongPressMaster: () -> Unit,
    onParkSpoofing: () -> Unit,
    onStartSpoofing: () -> Unit,
    onStopSpoofing: () -> Unit,
    onFeatureClicked: (AppFeature) -> Unit,
    pasteCaptureExpanded: Boolean,
    onLongPressPaste: () -> Unit,
    onCaptureShortcut: () -> Unit,
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
                                            onLongPressMaster()
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
                                    onToggleMaster()
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
                        if (isPanelExpanded) {
                            stringResource(
                                R.string.widget_collapse_widget,
                            )
                        } else {
                            stringResource(R.string.widget_expand_widget)
                        },
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
            WidgetSidePopup(visible = stopPopupVisible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (widgetMasterPopupMode(spoofingActive)) {
                        WidgetMasterPopupMode.PAUSE_AND_STOP -> {
                            WidgetIconButton(
                                icon = LjIcons.Pause,
                                contentDescription = stringResource(R.string.widget_panel_content_pause_spoofing),
                                tint = WidgetInactiveTint,
                                onClick = onParkSpoofing,
                            )
                        }

                        WidgetMasterPopupMode.START_AND_STOP -> {
                            WidgetIconButton(
                                icon = LjIcons.PlayArrow,
                                contentDescription = stringResource(R.string.widget_panel_content_start_spoofing),
                                tint = LjSuccess,
                                onClick = onStartSpoofing,
                            )
                        }
                    }
                    WidgetIconButton(
                        icon = LjIcons.Stop,
                        contentDescription = stringResource(R.string.widget_panel_content_stop_spoofing),
                        tint = MaterialTheme.colorScheme.error,
                        enabled = widgetStopEnabled(spoofingActive),
                        onClick = onStopSpoofing,
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
            val controlsEnabled = widgetControlsEnabled(spoofingActive)
            features.forEach { feature ->
                if (feature == AppFeature.ROUTES) {
                    val routeIconTint = if (routeControls.isActive) LjSuccess else MaterialTheme.colorScheme.primary
                    Box {
                        WidgetIconButton(
                            icon = LjIcons.Route,
                            contentDescription = stringResource(R.string.widget_panel_content_routes_picker),
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
                                            if (routeControls.isPaused) {
                                                stringResource(
                                                    R.string.widget_resume,
                                                )
                                            } else {
                                                stringResource(R.string.widget_pause)
                                            },
                                        tint = pauseResumeTint,
                                        enabled = controlsEnabled,
                                        onClick = routeControls.onPauseResume,
                                    )
                                }
                                WidgetIconButton(
                                    icon = LjIcons.Stop,
                                    contentDescription = stringResource(R.string.widget_panel_content_stop),
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
                                        contentDescription = stringResource(R.string.widget_panel_content_previous_waypoint),
                                        tint = LjSuccess,
                                        enabled = controlsEnabled,
                                        onClick = routeControls.onJumpPrevious,
                                    )
                                    WidgetIconButton(
                                        icon = LjIcons.SkipNext,
                                        contentDescription = stringResource(R.string.widget_panel_content_next_waypoint),
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
                            onLongClick = if (feature == AppFeature.PASTE_COORDINATES) onLongPressPaste else null,
                        )
                        if (feature == AppFeature.PASTE_COORDINATES) {
                            WidgetSidePopup(visible = pasteCaptureExpanded) {
                                WidgetIconButton(
                                    icon = LjIcons.AddLocationAlt,
                                    contentDescription = stringResource(R.string.widget_panel_content_open_capture),
                                    tint = MaterialTheme.colorScheme.primary,
                                    enabled = controlsEnabled,
                                    onClick = onCaptureShortcut,
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
                                if (section.active) {
                                    stringResource(
                                        R.string.widget_cancel_tap_to_walk,
                                    )
                                } else {
                                    stringResource(R.string.widget_tap_to_walk)
                                },
                            tint = crosshairTint,
                            enabled = controlsEnabled,
                            onClick = section.onClick,
                        )
                    }

                    is WidgetPanelSection.GroupSync -> {
                        Box {
                            WidgetIconButton(
                                icon = LjIcons.Group,
                                contentDescription = stringResource(R.string.widget_panel_content_group_sync),
                                tint = MaterialTheme.colorScheme.primary,
                                enabled = controlsEnabled,
                                onClick = section.onClick,
                            )
                            WidgetSidePopup(visible = section.expanded) {
                                WidgetIconButton(
                                    icon = LjIcons.MyLocation,
                                    contentDescription = stringResource(R.string.widget_panel_content_teleport_to_leader_now),
                                    tint = LjSuccess,
                                    enabled = controlsEnabled,
                                    onClick = section.onTeleport,
                                )
                            }
                        }
                    }

                    is WidgetPanelSection.AltitudeOverride -> {
                        Box {
                            WidgetIconButton(
                                icon = LjIcons.Terrain,
                                contentDescription = stringResource(R.string.widget_panel_content_altitude_override),
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
                    contentDescription = "Stop ${progress.current} of ${progress.total}",
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

internal fun formatBearingText(stats: DebugStats): String = if (stats.hasBearing) "%.0f°".format(stats.bearing) else "—"

@Composable
private fun DebugStatsPanel(stats: DebugStats) {
    Column(
        modifier =
            Modifier
                .padding(4.dp)
                .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.small)
                .padding(8.dp),
    ) {
        val tickHz = if (stats.tickIntervalMs > 0) 1000f / stats.tickIntervalMs else 0f
        Text(
            stringResource(R.string.widget_panel_content_2f_6f).format(stats.latitude, stats.longitude),
            color = LjText,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            stringResource(R.string.widget_panel_content_speed_2f_m_s_alt_ellipsoidal_2f_m).format(stats.speedMs, stats.altitudeMeters),
            color = LjText,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            stringResource(
                R.string.widget_panel_content_acc_1f_m_bearing_s_1f_hz,
            ).format(stats.accuracyMeters, formatBearingText(stats), tickHz),
            color = LjText,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AltitudeOverrideInput(
    prefillMeters: Double,
    onConfirm: (Double) -> Unit,
) {
    // Captured once when this composable enters composition (i.e. on expand), not re-read on
    // every recomposition — the live altitude changes every tick while spoofing and would
    // otherwise stomp on what the user is typing.
    var value by remember { mutableStateOf(prefillMeters.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.width(100.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.widget_panel_altitude_m)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { value.toDoubleOrNull()?.let(onConfirm) }),
        )
        IconButton(onClick = { value.toDoubleOrNull()?.let(onConfirm) }) {
            Icon(LjIcons.Check, contentDescription = stringResource(R.string.widget_panel_confirm_altitude_cd), tint = LjSuccess)
        }
    }
}

@Composable
private fun FloatingPickerShell(
    title: String,
    onDismiss: () -> Unit,
    hasBack: Boolean,
    onBack: () -> Unit,
    searchEnabled: Boolean = false,
    searchVisible: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: () -> Unit = {},
    showShareCurrentLocation: Boolean = false,
    onShareCurrentLocation: () -> Unit = {},
    sortMode: SavedItemSortMode? = null,
    onSortModeSelected: (SavedItemSortMode) -> Unit = {},
    searchLabel: String = stringResource(R.string.widget_search),
    compactOverlay: Boolean = false,
    minimized: Boolean = false,
    onToggleMinimize: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (compactOverlay) {
        CompactFloatingPickerShell(
            title = title,
            colors = colors,
            minimized = minimized,
            onToggleMinimize = onToggleMinimize,
            onDismiss = onDismiss,
            content = content,
        )
        return
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { if (hasBack) onBack() else onDismiss() },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.medium)
                    .background(colors.background, MaterialTheme.shapes.medium)
                    .clickable { /* consume touches inside panel */ },
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                ) {
                    FloatingPickerTitleRow(
                        title = title,
                        colors = colors,
                        hasBack = hasBack,
                        onBack = onBack,
                        searchEnabled = searchEnabled,
                        searchVisible = searchVisible,
                        onToggleSearch = onToggleSearch,
                        showShareCurrentLocation = showShareCurrentLocation,
                        onShareCurrentLocation = onShareCurrentLocation,
                        sortMode = sortMode,
                        onSortModeSelected = onSortModeSelected,
                        onDismiss = onDismiss,
                        onToggleMinimize = null,
                        minimized = false,
                    )
                    if (!hasBack && searchEnabled && searchVisible) {
                        ListSearchField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            label = searchLabel,
                            autoFocus = true,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun CompactFloatingPickerShell(
    title: String,
    colors: ColorScheme,
    minimized: Boolean,
    onToggleMinimize: (() -> Unit)?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    colors.background,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                ).navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            FloatingPickerTitleRow(
                title = title,
                colors = colors,
                hasBack = false,
                onBack = onDismiss,
                searchEnabled = false,
                searchVisible = false,
                onToggleSearch = {},
                sortMode = null,
                onSortModeSelected = {},
                onDismiss = onDismiss,
                onToggleMinimize = onToggleMinimize,
                minimized = minimized,
            )
            if (minimized) {
                Text(
                    text = stringResource(R.string.widget_panel_content_switch_apps_to_copy_then_expand_to_paste),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            // Keep the form composed while shrunk so pasted text is not reset.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (minimized) {
                                Modifier.size(0.dp).clipToBounds()
                            } else {
                                Modifier.heightIn(max = maxHeight)
                            },
                        ),
                content = content,
            )
        }
    }
}

@Composable
private fun FloatingPickerTitleRow(
    title: String,
    colors: ColorScheme,
    hasBack: Boolean,
    onBack: () -> Unit,
    searchEnabled: Boolean,
    searchVisible: Boolean,
    onToggleSearch: () -> Unit,
    showShareCurrentLocation: Boolean = false,
    onShareCurrentLocation: () -> Unit = {},
    sortMode: SavedItemSortMode?,
    onSortModeSelected: (SavedItemSortMode) -> Unit,
    onDismiss: () -> Unit,
    onToggleMinimize: (() -> Unit)?,
    minimized: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasBack) {
            IconButton(onClick = onBack) {
                Icon(LjIcons.ArrowBack, contentDescription = stringResource(R.string.widget_panel_content_back), tint = colors.onBackground)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (!hasBack && showShareCurrentLocation) {
            IconButton(onClick = onShareCurrentLocation) {
                Icon(
                    imageVector = LjIcons.Share,
                    contentDescription = stringResource(R.string.widget_panel_content_share_current_location),
                    tint = colors.onBackground,
                )
            }
        }
        if (!hasBack && sortMode != null) {
            SavedItemSortMenu(selected = sortMode, onSelected = onSortModeSelected)
        }
        if (!hasBack && searchEnabled) {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = LjIcons.Search,
                    contentDescription =
                        if (searchVisible) {
                            stringResource(
                                R.string.widget_hide_search,
                            )
                        } else {
                            stringResource(R.string.widget_search)
                        },
                    tint = if (searchVisible) LjSuccess else colors.onBackground,
                )
            }
        }
        if (onToggleMinimize != null) {
            IconButton(onClick = onToggleMinimize) {
                Icon(
                    imageVector = if (minimized) LjIcons.ExpandLess else LjIcons.ExpandMore,
                    contentDescription =
                        if (minimized) {
                            stringResource(
                                R.string.widget_expand_paste_box,
                            )
                        } else {
                            stringResource(R.string.widget_shrink_paste_box)
                        },
                    tint = colors.onBackground,
                )
            }
        }
        if (!hasBack) {
            IconButton(onClick = onDismiss) {
                Icon(LjIcons.Close, contentDescription = stringResource(R.string.widget_panel_content_close), tint = colors.onBackground)
            }
        }
    }
}

@Composable
internal fun FavoritesFloatingView(
    favorites: List<FavoriteLocation>,
    onDismiss: () -> Unit,
    onTeleport: (FavoriteLocation) -> Unit,
    onWalk: (FavoriteLocation) -> Unit,
    onWalkViaRoads: (FavoriteLocation) -> Unit,
    onRename: (FavoriteLocation, String) -> Unit,
    onDelete: (FavoriteLocation) -> Unit,
    cooldownStates: Map<String, CooldownState> = emptyMap(),
    currentPosition: LatLng? = null,
    onAddFromHere: ((name: String) -> Unit)? = null,
    hideTeleport: Boolean = false,
    onShareOpened: () -> Unit = onDismiss,
    sortMode: SavedItemSortMode = SavedItemSortMode.NEWEST_FIRST,
    onSortModeSelected: (SavedItemSortMode) -> Unit = {},
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newFavName by remember { mutableStateOf("") }
    var selectedFavorite by remember { mutableStateOf<FavoriteLocation?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var renamingFavorite by remember { mutableStateOf<FavoriteLocation?>(null) }
    var deletingFavorite by remember { mutableStateOf<FavoriteLocation?>(null) }
    var renameText by remember { mutableStateOf("") }
    val context = LocalContext.current

    FloatingPickerShell(
        title = selectedFavorite?.name ?: stringResource(R.string.widget_favorites),
        onDismiss = onDismiss,
        hasBack = selectedFavorite != null,
        onBack = { selectedFavorite = null },
        searchEnabled = selectedFavorite == null && favorites.isNotEmpty(),
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
        onShareCurrentLocation = {
            if (currentLocationShareText(currentPosition) == null) {
                shareCurrentLocationCoordinates(context, currentPosition)
            } else {
                // Close the overlay first: it sits above activities, so the share sheet is hidden
                // until this panel is gone.
                onShareOpened()
                shareCurrentLocationCoordinates(context, currentPosition)
            }
        },
        showShareCurrentLocation = selectedFavorite == null,
        sortMode = if (selectedFavorite == null) sortMode else null,
        onSortModeSelected = onSortModeSelected,
        searchLabel = stringResource(R.string.widget_search_favorites),
    ) {
        val selected = selectedFavorite
        if (selected != null) {
            FavoriteTargetDetail(
                favorite = selected,
                onSetLocation = {
                    onTeleport(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                onGoToLocation = {
                    onWalk(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                onGoToLocationViaRoads = {
                    onWalkViaRoads(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                onDismiss = { selectedFavorite = null },
                hideTeleportFeatures = hideTeleport,
                showDismissButton = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        renameText = selected.name
                        renamingFavorite = selected
                    },
                ) {
                    Icon(LjIcons.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.widget_panel_content_rename))
                }
                TextButton(onClick = { deletingFavorite = selected }) {
                    Icon(LjIcons.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.widget_panel_content_delete))
                }
            }
        } else {
            FavoritesList(
                title = null,
                favorites = favorites,
                onSelect = { selectedFavorite = it },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
                enableSearch = false,
                filterQuery = searchQuery,
                cooldownBadgeText = { fav ->
                    (cooldownStates[fav.id] ?: CooldownState.Ready).toBadgeText(currentPosition, fav.position)
                },
            )
            if (onAddFromHere != null) {
                Spacer(Modifier.height(12.dp))
                if (showAddForm) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    OutlinedTextField(
                        value = newFavName,
                        onValueChange = { newFavName = it },
                        label = { Text(stringResource(R.string.widget_panel_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    if (newFavName.isNotBlank()) {
                                        onAddFromHere(newFavName.trim())
                                        newFavName = ""
                                        showAddForm = false
                                    }
                                },
                            ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LjTextButton(onClick = {
                            showAddForm = false
                            newFavName = ""
                        }) {
                            Text(stringResource(R.string.widget_panel_cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        LjButton(
                            onClick = {
                                if (newFavName.isNotBlank()) {
                                    onAddFromHere(newFavName.trim())
                                    newFavName = ""
                                    showAddForm = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.widget_panel_save))
                        }
                    }
                } else {
                    LjButton(
                        onClick = { showAddForm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(LjIcons.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.widget_panel_add_from_current_location))
                    }
                }
            }
        }
    }
    renamingFavorite?.let { favorite ->
        AlertDialog(
            onDismissRequest = { renamingFavorite = null },
            title = { Text(stringResource(R.string.widget_panel_content_rename_favorite)) },
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
                        val name = renameText.trim()
                        onRename(favorite, name)
                        selectedFavorite = favorite.copy(name = name)
                        renamingFavorite = null
                    },
                ) { Text(stringResource(R.string.widget_panel_content_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { renamingFavorite = null },
                ) { Text(stringResource(R.string.widget_panel_content_cancel)) }
            },
        )
    }
    deletingFavorite?.let { favorite ->
        AlertDialog(
            onDismissRequest = { deletingFavorite = null },
            title = { Text(stringResource(R.string.widget_panel_content_delete_favorite)) },
            text = { Text(favorite.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(favorite)
                        selectedFavorite = null
                        deletingFavorite = null
                    },
                ) { Text(stringResource(R.string.widget_panel_content_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingFavorite = null },
                ) { Text(stringResource(R.string.widget_panel_content_cancel)) }
            },
        )
    }
}

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
            if (selectedRouteId !=
                null
            ) {
                routes.find { it.id == selectedRouteId }?.name ?: stringResource(R.string.widget_routes)
            } else {
                stringResource(R.string.widget_routes)
            },
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
        AppFeature.JOYSTICK_TOGGLE -> stringResource(R.string.widget_show_hide_joystick)
        AppFeature.JOYSTICK_LOCK -> stringResource(R.string.widget_lock_joystick_position)
        AppFeature.ROUTES -> stringResource(R.string.widget_routes_picker)
        AppFeature.FAVORITES -> stringResource(R.string.widget_favorites_picker)
        AppFeature.SPEED_CYCLE -> stringResource(R.string.widget_speed_cycle)
        AppFeature.MAP_FLOATING -> stringResource(R.string.widget_open_map)
        AppFeature.PASTE_COORDINATES -> stringResource(R.string.widget_paste_coordinates)
        AppFeature.ROAMING -> stringResource(R.string.widget_roaming)
        AppFeature.SEARCH, AppFeature.CAPTURE_COORDINATES ->
            error(
                "$this is map-only and never appears in the widget panel",
            )
    }

@Composable
internal fun PasteCoordinatesFloatingView(
    onDismiss: () -> Unit,
    onTeleport: (LatLng) -> Unit,
    onWalk: (LatLng) -> Unit,
    onWalkViaRoads: (LatLng) -> Unit,
    onSaveFavorite: (name: String, position: LatLng) -> Unit,
    onSaveRoute: (name: String, points: List<LatLng>) -> Unit,
    onStartRoute: (
        points: List<LatLng>,
        loop: Boolean,
        reverse: Boolean,
        returnToLocation: Boolean,
        followRoads: Boolean,
        planting: Boolean,
        teleportBetweenWaypoints: Boolean,
        teleportBetweenDelaySeconds: Int,
    ) -> Unit,
    hideTeleport: Boolean = false,
    minimized: Boolean = false,
    onToggleMinimize: () -> Unit = {},
) {
    FloatingPickerShell(
        title = stringResource(R.string.widget_panel_content_paste_coordinates),
        onDismiss = onDismiss,
        hasBack = false,
        onBack = onDismiss,
        compactOverlay = true,
        minimized = minimized,
        onToggleMinimize = onToggleMinimize,
    ) {
        PasteCoordinatesForm(
            onDismiss = onDismiss,
            onTeleport = onTeleport,
            onWalk = onWalk,
            onWalkViaRoads = onWalkViaRoads,
            onSaveFavorite = onSaveFavorite,
            onSaveRoute = onSaveRoute,
            onStartRoute = onStartRoute,
            hideTeleportFeatures = hideTeleport,
            showTitle = false,
            contentPadding = PaddingValues(0.dp),
        )
    }
}

@Composable
internal fun RoamingFloatingView(
    draft: RoamingDefaults,
    speedUnit: SpeedUnit,
    hasCurrentPosition: Boolean,
    isSpoofingActive: Boolean,
    routePlaying: Boolean = false,
    onDismiss: () -> Unit,
    onDraftChange: (RoamingDefaults) -> Unit,
    onGenerate: suspend (RoamingDefaults) -> List<LatLng>?,
    onStart: (RoamingDefaults) -> Unit,
) {
    var hasPreview by remember { mutableStateOf(false) }
    var isPreviewLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FloatingPickerShell(
        title = stringResource(R.string.widget_panel_content_roaming),
        onDismiss = onDismiss,
        hasBack = false,
        onBack = onDismiss,
    ) {
        RoamingSheetContent(
            draft = draft,
            speedUnit = speedUnit,
            hasCurrentPosition = hasCurrentPosition,
            isSpoofingActive = isSpoofingActive,
            hasPreview = hasPreview,
            isPreviewLoading = isPreviewLoading,
            showViewOnMap = false,
            routePlaying = routePlaying,
            onDraftChange = onDraftChange,
            onGenerate = { kind ->
                val next = draft.copy(kind = kind)
                onDraftChange(next)
                scope.launch {
                    isPreviewLoading = true
                    try {
                        hasPreview = (onGenerate(next)?.size ?: 0) >= 2
                    } finally {
                        isPreviewLoading = false
                    }
                }
            },
            onStart = { kind ->
                onStart(draft.copy(kind = kind))
                onDismiss()
            },
            onViewOnMap = onDismiss,
        )
    }
}
