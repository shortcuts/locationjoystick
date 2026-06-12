package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.model.ElevationMode
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.WidgetFeature

@Composable
internal fun WidgetPanel(
    features: List<WidgetFeature>,
    joystickVisible: Boolean,
    joystickLocked: Boolean,
    activeProfileId: String,
    isActivityActive: Boolean,
    isActivityPaused: Boolean,
    isActivityPausable: Boolean,
    routeExpanded: Boolean,
    isPanelExpanded: Boolean,
    elevationMode: ElevationMode?,
    onToggleMaster: () -> Unit,
    onFeatureClicked: (WidgetFeature) -> Unit,
    onElevationModeSelected: (ElevationMode?) -> Unit,
    onRouteClicked: () -> Unit,
    onRoutePauseResume: () -> Unit,
    onRouteStop: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Column(horizontalAlignment = Alignment.Start) {
        // Master toggle icon — always visible; drag to reposition, tap to toggle panel
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(4.dp)
                    .size(UiConstants.FAB_CONTAINER_SIZE)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .pointerInput(Unit) {
                        var isDragging = false
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                isDragging = false
                                do {
                                    val event = awaitPointerEvent()
                                    val drag = event.changes.firstOrNull() ?: break
                                    val delta = drag.position - drag.previousPosition
                                    if (delta != androidx.compose.ui.geometry.Offset.Zero) {
                                        isDragging = true
                                        onDrag(delta.x, delta.y)
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

        // Feature icons — only shown when panel expanded
        if (isPanelExpanded) {
            features.forEach { feature ->
                if (feature == WidgetFeature.ROUTES_FLOATING) {
                    val routeIconTint = if (isActivityActive) LjSuccess else MaterialTheme.colorScheme.primary
                    // Route icon + active controls in a horizontal row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .padding(4.dp)
                                    .size(UiConstants.FAB_CONTAINER_SIZE)
                                    .background(Color.Black, CircleShape)
                                    .clickable { onRouteClicked() },
                        ) {
                            Icon(
                                imageVector = LjIcons.Route,
                                contentDescription = "Routes picker",
                                tint = routeIconTint,
                                modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
                            )
                        }
                        // Pause/stop shown to the right when activity active and expanded
                        if (isActivityActive && routeExpanded) {
                            if (isActivityPausable) {
                                val pauseResumeIcon = if (isActivityPaused) LjIcons.PlayArrow else LjIcons.Pause
                                val pauseResumeTint = if (isActivityPaused) LjSuccess else LjInactive
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .padding(4.dp)
                                            .size(UiConstants.FAB_CONTAINER_SIZE)
                                            .background(Color.Black, CircleShape)
                                            .clickable { onRoutePauseResume() },
                                ) {
                                    Icon(
                                        imageVector = pauseResumeIcon,
                                        contentDescription = if (isActivityPaused) "Resume" else "Pause",
                                        tint = pauseResumeTint,
                                        modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
                                    )
                                }
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .padding(4.dp)
                                        .size(UiConstants.FAB_CONTAINER_SIZE)
                                        .background(Color.Black, CircleShape)
                                        .clickable { onRouteStop() },
                            ) {
                                Icon(
                                    imageVector = LjIcons.Stop,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
                                )
                            }
                        }
                    }
                } else if (feature == WidgetFeature.ELEVATION_CONTROLS) {
                    listOf(
                        Triple(ElevationMode.TiltUp, LjIcons.ElevationUp, "Tilt up"),
                        Triple(ElevationMode.Neutral, LjIcons.ElevationNeutral, "Neutral"),
                        Triple(ElevationMode.TiltDown, LjIcons.ElevationDown, "Tilt down"),
                    ).forEach { (mode, icon, desc) ->
                        val tint = if (elevationMode == mode) MaterialTheme.colorScheme.primary else LjInactive
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .padding(4.dp)
                                    .size(UiConstants.FAB_CONTAINER_SIZE)
                                    .background(Color.Black, CircleShape)
                                    .clickable { onElevationModeSelected(if (elevationMode == mode) null else mode) },
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = desc,
                                tint = tint,
                                modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
                            )
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
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .size(UiConstants.FAB_CONTAINER_SIZE)
                                .background(Color.Black, CircleShape)
                                .clickable { onFeatureClicked(feature) },
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = feature.toContentDescription(),
                            tint = iconTint,
                            modifier = Modifier.size(UiConstants.FAB_ICON_SIZE),
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
                    .background(LjBg, RoundedCornerShape(16.dp))
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
            Button(
                onClick = {
                    onTeleport(selected)
                    selectedFavorite = null
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Teleport") }
            Spacer(Modifier.height(8.dp))
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
                                            androidx.compose.foundation.shape
                                                .RoundedCornerShape(8.dp),
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
                                    val badgeText = (cooldownStates[fav.id] ?: CooldownState.Ready).toBadgeText(currentPosition, fav.position)
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
    onStartRoute: (routeId: String, isLooping: Boolean, isReverse: Boolean, isReturnToLocation: Boolean, teleportToStart: Boolean) -> Unit,
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

            LjCheckboxRow(title = "Loop", checked = loop, onCheckedChange = { loop = it }, enabled = !returnToLocation, textColor = LjText)
            LjCheckboxRow(title = "Reverse", checked = reverse, onCheckedChange = { reverse = it }, textColor = LjText)
            LjCheckboxRow(title = "Return to location", checked = returnToLocation, onCheckedChange = {
                returnToLocation = it
            }, enabled = !loop, textColor = LjText)

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    onStartRoute(routeId, loop, reverse, returnToLocation && !loop, false)
                    selectedRouteId = null
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Walk and start", color = LjText)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onStartRoute(routeId, loop, reverse, returnToLocation && !loop, true)
                    selectedRouteId = null
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Teleport and start", color = LjText)
            }
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
                                            RoundedCornerShape(8.dp),
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
    feature: WidgetFeature,
    joystickVisible: Boolean,
    joystickLocked: Boolean,
    activeProfileId: String,
): Pair<ImageVector, Boolean> =
    when (feature) {
        WidgetFeature.JOYSTICK_TOGGLE -> {
            Pair(LjIcons.Visibility, joystickVisible)
        }

        WidgetFeature.JOYSTICK_LOCK -> {
            Pair(
                if (joystickLocked) LjIcons.Lock else LjIcons.LockOpen,
                joystickLocked,
            )
        }

        WidgetFeature.ROUTES_FLOATING -> {
            Pair(LjIcons.Route, true)
        }

        WidgetFeature.FAVORITES_FLOATING -> {
            Pair(LjIcons.Favorite, true)
        }

        WidgetFeature.SPEED_CYCLE -> {
            Pair(
                when (activeProfileId) {
                    AppConstants.ProfileConstants.PROFILE_ID_RUN -> LjIcons.DirectionsRun
                    AppConstants.ProfileConstants.PROFILE_ID_BIKE -> LjIcons.DirectionsBike
                    else -> LjIcons.DirectionsWalk
                },
                true,
            )
        }

        WidgetFeature.MAP_FLOATING -> {
            Pair(LjIcons.LocationOn, true)
        }

        WidgetFeature.ELEVATION_CONTROLS -> {
            Pair(LjIcons.Layers, false)
        }
    }

private fun WidgetFeature.toContentDescription(): String =
    when (this) {
        WidgetFeature.JOYSTICK_TOGGLE -> "Show/hide joystick"
        WidgetFeature.JOYSTICK_LOCK -> "Lock joystick position"
        WidgetFeature.ROUTES_FLOATING -> "Routes picker"
        WidgetFeature.FAVORITES_FLOATING -> "Favorites picker"
        WidgetFeature.SPEED_CYCLE -> "Speed cycle"
        WidgetFeature.MAP_FLOATING -> "Open map"
        WidgetFeature.ELEVATION_CONTROLS -> "Elevation controls"
    }
