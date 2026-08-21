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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.toBadgeText
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjInactive
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.LjRouteStartOptions
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng

/** Shared circular icon button for the widget panel: press scale + icon crossfade on state change. */
@Composable
private fun WidgetIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "widgetButtonPressScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .padding(4.dp)
                .size(UiConstants.FAB_CONTAINER_SIZE)
                .scale(scale)
                .background(Color.Black, CircleShape)
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        Crossfade(targetState = icon, animationSpec = tween(150), label = "widgetButtonIcon") { animatedIcon ->
            Icon(
                imageVector = animatedIcon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
            )
        }
    }
}

@Composable
internal fun WidgetPanel(
    features: List<AppFeature>,
    joystickVisible: Boolean,
    joystickLocked: Boolean,
    activeProfileId: String,
    isActivityActive: Boolean,
    isActivityPaused: Boolean,
    isActivityPausable: Boolean,
    isRouteReplay: Boolean = false,
    onJumpToNextWaypoint: () -> Unit = {},
    onJumpToPreviousWaypoint: () -> Unit = {},
    hideTeleportFeatures: Boolean = false,
    showRouteJumpButtons: Boolean = false,
    routeExpanded: Boolean,
    isPanelExpanded: Boolean,
    hasPendingCompletion: Boolean,
    onToggleMaster: () -> Unit,
    onFeatureClicked: (AppFeature) -> Unit,
    onRouteClicked: () -> Unit,
    onRoutePauseResume: () -> Unit,
    onRouteStop: () -> Unit,
    isTapToWalkEnabled: Boolean = false,
    isTapToWalkActive: Boolean = false,
    onTapToWalkClicked: () -> Unit = {},
    isGroupSyncButtonVisible: Boolean = false,
    isGroupSyncExpanded: Boolean = false,
    onGroupSyncClicked: () -> Unit = {},
    onTeleportToLeaderClicked: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Column(horizontalAlignment = Alignment.Start) {
        // Master toggle icon — always visible; drag to reposition, tap to toggle panel
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .padding(4.dp)
                        .size(UiConstants.FAB_CONTAINER_SIZE)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var isDragging = false
                                    var accumulatedDistance = 0f
                                    do {
                                        val event = awaitPointerEvent()
                                        val drag = event.changes.firstOrNull() ?: break
                                        val delta = drag.position - drag.previousPosition
                                        if (delta != androidx.compose.ui.geometry.Offset.Zero) {
                                            if (!isDragging) {
                                                accumulatedDistance += delta.getDistance()
                                                if (accumulatedDistance > viewConfiguration.touchSlop) {
                                                    isDragging = true
                                                }
                                            }
                                            if (isDragging) {
                                                onDrag(delta.x, delta.y)
                                            }
                                            drag.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                    if (!isDragging) {
                                        onToggleMaster()
                                    }
                                }
                            }
                        },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_launcher),
                    contentDescription = if (isPanelExpanded) "Collapse widget" else "Expand widget",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
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
            features.forEach { feature ->
                if (feature == AppFeature.ROUTES) {
                    val routeIconTint = if (isActivityActive) LjSuccess else MaterialTheme.colorScheme.primary
                    // Route icon + active controls in a horizontal row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetIconButton(
                            icon = LjIcons.Route,
                            contentDescription = "Routes picker",
                            tint = routeIconTint,
                            onClick = onRouteClicked,
                        )
                        // Pause/stop shown to the right when activity active and expanded
                        AnimatedVisibility(
                            visible = isActivityActive && routeExpanded,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(150)),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActivityPausable) {
                                    val pauseResumeIcon = if (isActivityPaused) LjIcons.PlayArrow else LjIcons.Pause
                                    val pauseResumeTint = if (isActivityPaused) LjSuccess else LjInactive
                                    WidgetIconButton(
                                        icon = pauseResumeIcon,
                                        contentDescription = if (isActivityPaused) "Resume" else "Pause",
                                        tint = pauseResumeTint,
                                        onClick = onRoutePauseResume,
                                    )
                                }
                                WidgetIconButton(
                                    icon = LjIcons.Stop,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = onRouteStop,
                                )
                                if (isRouteReplay && !hideTeleportFeatures && showRouteJumpButtons) {
                                    WidgetIconButton(
                                        icon = LjIcons.SkipPrevious,
                                        contentDescription = "Previous waypoint",
                                        tint = LjSuccess,
                                        onClick = onJumpToPreviousWaypoint,
                                    )
                                    WidgetIconButton(
                                        icon = LjIcons.SkipNext,
                                        contentDescription = "Next waypoint",
                                        tint = LjSuccess,
                                        onClick = onJumpToNextWaypoint,
                                    )
                                }
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
                    val iconTint = if (active) MaterialTheme.colorScheme.primary else LjInactive
                    WidgetIconButton(
                        icon = icon,
                        contentDescription = feature.toContentDescription(),
                        tint = iconTint,
                        onClick = { onFeatureClicked(feature) },
                    )
                }
            }
            if (isTapToWalkEnabled) {
                val crosshairTint = if (isTapToWalkActive) MaterialTheme.colorScheme.primary else LjInactive
                WidgetIconButton(
                    icon = LjIcons.MyLocation,
                    contentDescription = if (isTapToWalkActive) "Cancel tap-to-walk" else "Tap to walk",
                    tint = crosshairTint,
                    onClick = onTapToWalkClicked,
                )
            }
            if (isGroupSyncButtonVisible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidgetIconButton(
                        icon = LjIcons.Group,
                        contentDescription = "Group sync",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onGroupSyncClicked,
                    )
                    AnimatedVisibility(
                        visible = isGroupSyncExpanded,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                    ) {
                        WidgetIconButton(
                            icon = LjIcons.MyLocation,
                            contentDescription = "Teleport to leader now",
                            tint = LjSuccess,
                            onClick = onTeleportToLeaderClicked,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingPickerShell(
    title: String,
    onDismiss: () -> Unit,
    hasBack: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
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
                    .background(LjBg, MaterialTheme.shapes.medium)
                    .clickable { /* consume touches inside panel */ },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasBack) {
                        IconButton(onClick = onBack) {
                            Icon(LjIcons.ArrowBack, contentDescription = "Back", tint = LjText)
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = LjText,
                        modifier = Modifier.weight(1f),
                    )
                    if (!hasBack) {
                        IconButton(onClick = onDismiss) {
                            Icon(LjIcons.Close, contentDescription = "Close", tint = LjText)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                content()
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
    cooldownStates: Map<String, CooldownState> = emptyMap(),
    currentPosition: LatLng? = null,
    onAddFromHere: ((name: String) -> Unit)? = null,
    hideTeleport: Boolean = false,
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newFavName by remember { mutableStateOf("") }
    var selectedFavorite by remember { mutableStateOf<FavoriteLocation?>(null) }

    FloatingPickerShell(
        title = selectedFavorite?.name ?: "Favorites",
        onDismiss = onDismiss,
        hasBack = selectedFavorite != null,
        onBack = { selectedFavorite = null },
    ) {
        val selected = selectedFavorite
        if (selected != null) {
            if (!hideTeleport) {
                Button(
                    onClick = {
                        onTeleport(selected)
                        selectedFavorite = null
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Teleport") }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = {
                    onWalk(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Walk", color = LjText) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onWalkViaRoads(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Via roads", color = LjText) }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                if (favorites.isEmpty()) {
                    Text(
                        text = "No favorites saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LjText.copy(alpha = 0.6f),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favorites, key = { it.id }) { fav ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            androidx.compose.ui.graphics.Color.White
                                                .copy(alpha = 0.12f),
                                            MaterialTheme.shapes.small,
                                        ).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fav.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LjText,
                                    )
                                    Text(
                                        text = "${String.format(
                                            "%.4f",
                                            fav.position.latitude,
                                        )}, ${String.format("%.4f", fav.position.longitude)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LjText.copy(alpha = 0.7f),
                                    )
                                    val badgeText =
                                        (cooldownStates[fav.id] ?: CooldownState.Ready).toBadgeText(
                                            currentPosition,
                                            fav.position,
                                        )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LjText.copy(alpha = 0.5f),
                                    )
                                }
                                Button(onClick = { selectedFavorite = fav }) {
                                    Icon(LjIcons.PlayArrow, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
            if (onAddFromHere != null) {
                Spacer(Modifier.height(12.dp))
                if (showAddForm) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    OutlinedTextField(
                        value = newFavName,
                        onValueChange = { newFavName = it },
                        label = { Text("Name", color = LjText) },
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
                        TextButton(onClick = {
                            showAddForm = false
                            newFavName = ""
                        }) {
                            Text("Cancel", color = LjText)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newFavName.isNotBlank()) {
                                    onAddFromHere(newFavName.trim())
                                    newFavName = ""
                                    showAddForm = false
                                }
                            },
                        ) {
                            Text("Save")
                        }
                    }
                } else {
                    Button(
                        onClick = { showAddForm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(LjIcons.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add from current location")
                    }
                }
            }
        }
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
        teleportToStart: Boolean,
        followRoadsToStart: Boolean,
    ) -> Unit,
    hideTeleport: Boolean = false,
) {
    var selectedRouteId by remember { mutableStateOf<String?>(null) }

    FloatingPickerShell(
        title = if (selectedRouteId != null) routes.find { it.id == selectedRouteId }?.name ?: "Routes" else "Routes",
        onDismiss = onDismiss,
        hasBack = selectedRouteId != null,
        onBack = { selectedRouteId = null },
    ) {
        if (selectedRouteId != null) {
            val routeId = selectedRouteId!!
            var loop by remember(routeId) { mutableStateOf(false) }
            var reverse by remember(routeId) { mutableStateOf(false) }
            var returnToLocation by remember(routeId) { mutableStateOf(false) }

            LjRouteStartOptions(
                loop = loop,
                onLoopChange = { loop = it },
                reverse = reverse,
                onReverseChange = { reverse = it },
                returnToLocation = returnToLocation,
                onReturnToLocationChange = { returnToLocation = it },
                onWalkAndStart = {
                    onStartRoute(routeId, loop, reverse, returnToLocation && !loop, false, false)
                    selectedRouteId = null
                    onDismiss()
                },
                onWalkViaRoadsAndStart = {
                    onStartRoute(routeId, loop, reverse, returnToLocation && !loop, false, true)
                    selectedRouteId = null
                    onDismiss()
                },
                onTeleportAndStart = {
                    onStartRoute(routeId, loop, reverse, returnToLocation && !loop, true, false)
                    selectedRouteId = null
                    onDismiss()
                },
                hideTeleport = hideTeleport,
                textColor = LjText,
            )
        } else {
            Box(modifier = Modifier.weight(1f)) {
                if (routes.isEmpty()) {
                    Text(
                        text = "No routes saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LjText.copy(alpha = 0.6f),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(routes, key = { it.id }) { route ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Color.White.copy(alpha = 0.12f),
                                            MaterialTheme.shapes.small,
                                        ).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = route.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LjText,
                                    )
                                    Text(
                                        text = "${route.waypoints.size} waypoints",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LjText.copy(alpha = 0.7f),
                                    )
                                }
                                Button(onClick = { selectedRouteId = route.id }) {
                                    Icon(LjIcons.PlayArrow, contentDescription = null)
                                }
                            }
                        }
                    }
                }
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

        AppFeature.ROAMING, AppFeature.SEARCH -> {
            error("$feature is map-only and never appears in the widget panel")
        }
    }

private fun AppFeature.toContentDescription(): String =
    when (this) {
        AppFeature.JOYSTICK_TOGGLE -> "Show/hide joystick"
        AppFeature.JOYSTICK_LOCK -> "Lock joystick position"
        AppFeature.ROUTES -> "Routes picker"
        AppFeature.FAVORITES -> "Favorites picker"
        AppFeature.SPEED_CYCLE -> "Speed cycle"
        AppFeature.MAP_FLOATING -> "Open map"
        AppFeature.ROAMING, AppFeature.SEARCH -> error("$this is map-only and never appears in the widget panel")
    }
