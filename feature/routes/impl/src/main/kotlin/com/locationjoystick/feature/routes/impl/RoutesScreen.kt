package com.locationjoystick.feature.routes.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.model.distanceTo
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.ui.component.EmptyState
import com.locationjoystick.core.ui.component.LjTopBar

@Composable
fun RoutesRoute(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (RouteType) -> Unit,
    onOpenDrawer: () -> Unit,
    viewModel: RoutesViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    RoutesScreen(
        uiState = uiState,
        playbackState = playbackState,
        onNavigateToCreate = onNavigateToCreate,
        onOpenDrawer = onOpenDrawer,
        onDeleteRoute = viewModel::deleteRoute,
        onRenameRoute = viewModel::renameRoute,
        onExportRoute = { route -> viewModel.exportRouteAsGpx(context, route) },
        onStartReplay = { route, fromFirstWaypoint -> viewModel.startReplay(route, fromFirstWaypoint) },
        onPauseReplay = viewModel::pauseReplay,
        onResumeReplay = viewModel::resumeReplay,
        onStopReplay = viewModel::stopReplay,
    )
}

@Composable
internal fun RoutesScreen(
    uiState: RoutesUiState,
    playbackState: RoutePlaybackState,
    onNavigateToCreate: (RouteType) -> Unit,
    onOpenDrawer: () -> Unit,
    onDeleteRoute: (String) -> Unit,
    onRenameRoute: (String, String) -> Unit,
    onExportRoute: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, Boolean) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
) {
    var renamingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }
    var deletingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }

    Scaffold(
        topBar = {
            LjTopBar(title = "locationjoystick", onMenuClick = onOpenDrawer)
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToCreate(RouteType.GUIDED) },
                    icon = { Icon(Icons.Rounded.Map, null) },
                    text = { Text("guided route") }
                )
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToCreate(RouteType.STRAIGHT) },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("route") }
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.routes.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Rounded.Add,
                        message = "No routes yet",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(
                            items = uiState.routes,
                            key = { it.id }
                        ) { route ->
                            RouteCard(
                                route = route,
                                playbackState = playbackState,
                                onTap = { renamingRoute = route },
                                onDelete = { deletingRoute = it },
                                onExport = { onExportRoute(it) },
                                onStartReplay = onStartReplay,
                                onPauseReplay = onPauseReplay,
                                onResumeReplay = onResumeReplay,
                                onStopReplay = onStopReplay,
                            )
                        }
                    }
                }
            }
        }
    }

    renamingRoute?.let { route ->
        RenameRouteDialog(
            routeName = route.name,
            onDismiss = { renamingRoute = null },
            onSave = { newName ->
                onRenameRoute(route.id, newName)
                renamingRoute = null
            }
        )
    }

    deletingRoute?.let { route ->
        DeleteConfirmDialog(
            name = route.name,
            onDismiss = { deletingRoute = null },
            onConfirm = {
                onDeleteRoute(route.id)
                deletingRoute = null
            }
        )
    }
}

@Composable
private fun RouteCard(
    route: com.locationjoystick.core.model.Route,
    playbackState: RoutePlaybackState,
    onTap: () -> Unit,
    onDelete: (com.locationjoystick.core.model.Route) -> Unit,
    onExport: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, Boolean) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
) {
    val isActiveRoute = playbackState.activeRouteId == route.id
    val isPlaying = isActiveRoute && playbackState.isPlaying
    val isPaused = isActiveRoute && playbackState.isPaused
    val isActive = isPlaying || isPaused

    val distanceText = remember(route.waypoints) {
        if (route.waypoints.size < 2) {
            ""
        } else {
            val totalMeters = route.waypoints.zipWithNext { a, b ->
                a.position.distanceTo(b.position)
            }.sum()
            if (totalMeters >= 1000.0) {
                "%.1f km".format(totalMeters / 1000.0)
            } else {
                "%.0f m".format(totalMeters)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
    ) {
        // Row 1: name + distance (tap to rename)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTap() }
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val label = if (distanceText.isNotEmpty()) "${route.name} — $distanceText" else route.name
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${route.waypoints.size} waypoints",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = { onExport(route) }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export GPX")
            }
            IconButton(onClick = { onDelete(route) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }

        // Row 2: playback controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isPlaying -> {
                    IconButton(onClick = onPauseReplay) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                    IconButton(onClick = onStopReplay) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                isPaused -> {
                    IconButton(onClick = onResumeReplay) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                    IconButton(onClick = onStopReplay) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                else -> {
                    IconButton(
                        onClick = { onStartReplay(route, false) },
                        enabled = !isActive,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = "Start from current location"
                        )
                    }
                    IconButton(
                        onClick = { onStartReplay(route, true) },
                        enabled = !isActive,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Teleport to route start"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameRouteDialog(
    routeName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(routeName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Route") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Route name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotEmpty()) {
                        onSave(name)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$name\"?") },
        text = { Text("This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
