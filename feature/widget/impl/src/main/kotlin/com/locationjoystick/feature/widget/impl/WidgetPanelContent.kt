package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.RouteReplayMode
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
    onToggleMaster: () -> Unit,
    onFeatureClicked: (WidgetFeature) -> Unit,
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
                    .size(36.dp)
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
                modifier = Modifier.size(25.dp),
            )
        }

        // Feature icons — only shown when panel expanded
        if (isPanelExpanded) {
            features.forEach { feature ->
                if (feature == WidgetFeature.ROUTES_FLOATING) {
                    val routeIconTint = if (isActivityActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    // Route icon + active controls in a horizontal row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .padding(4.dp)
                                    .size(36.dp)
                                    .background(Color.Black, CircleShape)
                                    .clickable { onRouteClicked() },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Route,
                                contentDescription = "Routes picker",
                                tint = routeIconTint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        // Pause/stop shown to the right when activity active and expanded
                        if (isActivityActive && routeExpanded) {
                            if (isActivityPausable) {
                                val pauseResumeIcon = if (isActivityPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                                val pauseResumeTint = if (isActivityPaused) Color(0xFF4CAF50) else Color(0xFF757575)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .padding(4.dp)
                                            .size(36.dp)
                                            .background(Color.Black, CircleShape)
                                            .clickable { onRoutePauseResume() },
                                ) {
                                    Icon(
                                        imageVector = pauseResumeIcon,
                                        contentDescription = if (isActivityPaused) "Resume" else "Pause",
                                        tint = pauseResumeTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .padding(4.dp)
                                        .size(36.dp)
                                        .background(Color.Black, CircleShape)
                                        .clickable { onRouteStop() },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop",
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                } else {
                    val (icon, active) = featureIconAndState(feature, joystickVisible, joystickLocked, activeProfileId)
                    val iconTint = if (active) MaterialTheme.colorScheme.primary else Color(0xFF757575)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .size(36.dp)
                                .background(Color.Black, CircleShape)
                                .clickable { onFeatureClicked(feature) },
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = feature.toContentDescription(),
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
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
    onAddFromHere: (name: String) -> Unit,
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newFavName by remember { mutableStateOf("") }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
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
                    Text(
                        text = "Favorites",
                        style = MaterialTheme.typography.titleLarge,
                        color = LjText,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = LjText)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (favorites.isEmpty()) {
                        Text(
                            text = "No favorites saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LjText.copy(alpha = 0.6f),
                        )
                    } else {
                        LazyColumn {
                            items(favorites, key = { it.id }) { fav ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = fav.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = LjText,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(onClick = {
                                        onTeleport(fav)
                                        onDismiss()
                                    }) {
                                        Text("Teleport")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = {
                                        onWalk(fav)
                                        onDismiss()
                                    }) {
                                        Text("Walk")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = {
                                        onWalkViaRoads(fav)
                                        onDismiss()
                                    }) {
                                        Text("Via roads")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (showAddForm) {
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
                        modifier = Modifier.fillMaxWidth(),
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
                        Icon(Icons.Rounded.Add, contentDescription = null)
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
    onStartWithMode: (routeId: String, RouteReplayMode) -> Unit,
    onCreateFromMap: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
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
                    Text(
                        text = "Routes",
                        style = MaterialTheme.typography.titleLarge,
                        color = LjText,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = LjText)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (routes.isEmpty()) {
                        Text(
                            text = "No routes saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LjText.copy(alpha = 0.6f),
                        )
                    } else {
                        LazyColumn {
                            items(routes, key = { it.id }) { route ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = route.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = LjText,
                                        modifier = Modifier.weight(1f),
                                    )
                                    var routeMenuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        Button(onClick = { routeMenuExpanded = true }) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = routeMenuExpanded,
                                            onDismissRequest = { routeMenuExpanded = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Walk route") },
                                                onClick = {
                                                    onStartWithMode(route.id, RouteReplayMode.ONE_WAY)
                                                    routeMenuExpanded = false
                                                    onDismiss()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Return to location") },
                                                onClick = {
                                                    onStartWithMode(route.id, RouteReplayMode.RETURN_TO_LOCATION)
                                                    routeMenuExpanded = false
                                                    onDismiss()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Loop") },
                                                onClick = {
                                                    onStartWithMode(route.id, RouteReplayMode.LOOP)
                                                    routeMenuExpanded = false
                                                    onDismiss()
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Loop in reverse") },
                                                onClick = {
                                                    onStartWithMode(route.id, RouteReplayMode.LOOP_REVERSE)
                                                    routeMenuExpanded = false
                                                    onDismiss()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onCreateFromMap()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create route from map")
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
            Pair(Icons.Rounded.Visibility, joystickVisible)
        }

        WidgetFeature.JOYSTICK_LOCK -> {
            Pair(
                if (joystickLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                joystickLocked,
            )
        }

        WidgetFeature.ROUTES_FLOATING -> {
            Pair(Icons.Rounded.Route, true)
        }

        WidgetFeature.FAVORITES_FLOATING -> {
            Pair(Icons.Rounded.Favorite, true)
        }

        WidgetFeature.SPEED_CYCLE -> {
            Pair(
                when (activeProfileId) {
                    AppConstants.ProfileConstants.PROFILE_ID_RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
                    AppConstants.ProfileConstants.PROFILE_ID_BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
                    else -> Icons.AutoMirrored.Rounded.DirectionsWalk
                },
                true,
            )
        }

        WidgetFeature.MAP_FLOATING -> {
            Pair(Icons.Rounded.LocationOn, true)
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
    }
