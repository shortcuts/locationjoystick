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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.designsystem.component.EmptyState
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.model.RouteReplayMode
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.distanceTo

@Composable
fun RoutesRoute(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (RouteType) -> Unit,
    onImportGpx: () -> Unit,
    onOpenDrawer: () -> Unit,
    viewModel: RoutesViewModel,
    bottomBar: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    RoutesScreen(
        uiState = uiState,
        playbackState = playbackState,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToCreate = onNavigateToCreate,
        onImportGpx = onImportGpx,
        onOpenDrawer = onOpenDrawer,
        onDeleteRoute = viewModel::deleteRoute,
        onExportRoute = { route -> viewModel.exportRouteAsGpx(context, route) },
        onStartReplay = { route, mode -> viewModel.startReplay(route, mode) },
        onPauseReplay = viewModel::pauseReplay,
        onResumeReplay = viewModel::resumeReplay,
        onStopReplay = viewModel::stopReplay,
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun RoutesScreenPreview() {
    RoutesScreen(
        uiState = RoutesUiState(),
        playbackState = RoutePlaybackState(),
        onNavigateToDetail = {},
        onNavigateToCreate = {},
        onImportGpx = {},
        onOpenDrawer = {},
        onDeleteRoute = {},
        onExportRoute = {},
        onStartReplay = { _, _ -> },
        onPauseReplay = {},
        onResumeReplay = {},
        onStopReplay = {},
    )
}

@Composable
internal fun RoutesScreen(
    uiState: RoutesUiState,
    playbackState: RoutePlaybackState,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (RouteType) -> Unit,
    onImportGpx: () -> Unit,
    onOpenDrawer: () -> Unit,
    onDeleteRoute: (String) -> Unit,
    onExportRoute: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, RouteReplayMode) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    var deletingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

    LjScaffold(
        title = "Lj",
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        actions = {
            IconButton(onClick = { showAddMenu = !showAddMenu }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Add options")
            }
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("from map") },
                    onClick = {
                        onNavigateToCreate(RouteType.STRAIGHT)
                        showAddMenu = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.Map, null) },
                )
                DropdownMenuItem(
                    text = { Text("from GPX file") },
                    onClick = {
                        onImportGpx()
                        showAddMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.routes.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.PlayArrow,
                        message = "No routes yet",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(
                            items = uiState.routes,
                            key = { it.id },
                        ) { route ->
                            RouteCard(
                                route = route,
                                playbackState = playbackState,
                                onNavigateToEdit = { onNavigateToDetail(route.id) },
                                onDeleteRoute = { deletingRoute = route },
                                onExport = { onExportRoute(route) },
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

    deletingRoute?.let { route ->
        DeleteConfirmDialog(
            name = route.name,
            onDismiss = { deletingRoute = null },
            onConfirm = {
                onDeleteRoute(route.id)
                deletingRoute = null
            },
        )
    }
}

@Composable
private fun RouteCard(
    route: com.locationjoystick.core.model.Route,
    playbackState: RoutePlaybackState,
    onNavigateToEdit: (String) -> Unit,
    onDeleteRoute: (com.locationjoystick.core.model.Route) -> Unit,
    onExport: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, RouteReplayMode) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
) {
    val isActiveRoute = playbackState.activeRouteId == route.id
    val isPlaying = isActiveRoute && playbackState.isPlaying
    val isPaused = isActiveRoute && playbackState.isPaused
    val isActive = isPlaying || isPaused
    var menuExpanded by remember { mutableStateOf(false) }

    val distanceText =
        remember(route.waypoints) {
            if (route.waypoints.size < 2) {
                ""
            } else {
                val totalMeters =
                    route.waypoints
                        .zipWithNext { a, b ->
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
    ) {
        // Row 1: name + distance + 3-dot menu
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val label = if (distanceText.isNotEmpty()) "${route.name} — $distanceText" else route.name
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${route.waypoints.size} waypoints",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded = false
                            onNavigateToEdit(route.id)
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Export") },
                        onClick = {
                            menuExpanded = false
                            onExport(route)
                        },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDeleteRoute(route)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    )
                }
            }
        }

        // Row 2: playback controls
        Row(
            modifier =
                Modifier
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
                    var replayMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { replayMenuExpanded = true },
                            enabled = !isActive,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start route")
                        }
                        DropdownMenu(
                            expanded = replayMenuExpanded,
                            onDismissRequest = { replayMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Walk route") },
                                onClick = {
                                    onStartReplay(route, RouteReplayMode.ONE_WAY)
                                    replayMenuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Return to location") },
                                onClick = {
                                    onStartReplay(route, RouteReplayMode.RETURN_TO_LOCATION)
                                    replayMenuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Loop") },
                                onClick = {
                                    onStartReplay(route, RouteReplayMode.LOOP)
                                    replayMenuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.Default.Repeat, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Loop in reverse") },
                                onClick = {
                                    onStartReplay(route, RouteReplayMode.LOOP_REVERSE)
                                    replayMenuExpanded = false
                                },
                                leadingIcon = { Icon(Icons.Default.Repeat, null) },
                            )
                        }
                    }
                }
            }
        }
    }
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
        },
    )
}
