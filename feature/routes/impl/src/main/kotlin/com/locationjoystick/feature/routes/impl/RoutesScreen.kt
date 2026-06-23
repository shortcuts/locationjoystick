package com.locationjoystick.feature.routes.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.EmptyState
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LoadingIndicator
import com.locationjoystick.core.location.rememberSpoofToggleState
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
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spoofToggle = rememberSpoofToggleState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage!!)
            viewModel.clearError()
        }
    }

    RoutesScreen(
        uiState = uiState,
        playbackState = playbackState,
        snackbarHostState = snackbarHostState,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToCreate = onNavigateToCreate,
        onImportGpx = onImportGpx,
        onOpenDrawer = onOpenDrawer,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        onDeleteRoute = viewModel::deleteRoute,
        onExportRoute = { route -> viewModel.exportRouteAsGpx(context, route) },
        onStartReplay = { route, isLooping, isReverse, isReturnToLocation, teleportToStart ->
            viewModel.startReplay(route, isLooping, isReverse, isReturnToLocation, teleportToStart)
        },
        onPauseReplay = viewModel::pauseReplay,
        onResumeReplay = viewModel::resumeReplay,
        onStopReplay = viewModel::stopReplay,
        onToggleSort = viewModel::toggleSort,
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun RoutesScreenPreview() {
    RoutesScreen(
        uiState = RoutesUiState(),
        playbackState = RoutePlaybackState(),
        snackbarHostState = remember { SnackbarHostState() },
        onNavigateToDetail = {},
        onNavigateToCreate = {},
        onImportGpx = {},
        onOpenDrawer = {},
        onDeleteRoute = {},
        onExportRoute = {},
        onStartReplay = { _, _, _, _, _ -> },
        onPauseReplay = {},
        onResumeReplay = {},
        onStopReplay = {},
    )
}

@Composable
internal fun RoutesScreen(
    uiState: RoutesUiState,
    playbackState: RoutePlaybackState,
    snackbarHostState: SnackbarHostState,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (RouteType) -> Unit,
    onImportGpx: () -> Unit,
    onOpenDrawer: () -> Unit,
    onDeleteRoute: (String) -> Unit,
    onExportRoute: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    onToggleSort: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    var deletingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

    LjScaffold(
        title = "Routes",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = onToggleSort) {
                Icon(LjIcons.SwapVert, contentDescription = "Sort")
            }
            IconButton(onClick = { showAddMenu = !showAddMenu }) {
                Icon(LjIcons.Add, contentDescription = "Add route")
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
                    leadingIcon = { Icon(LjIcons.Map, null) },
                )
                DropdownMenuItem(
                    text = { Text("from map follow roads") },
                    onClick = {
                        onNavigateToCreate(RouteType.GUIDED)
                        showAddMenu = false
                    },
                    leadingIcon = { Icon(LjIcons.Map, null) },
                )
                DropdownMenuItem(
                    text = { Text("from GPX file") },
                    onClick = {
                        onImportGpx()
                        showAddMenu = false
                    },
                    leadingIcon = { Icon(LjIcons.Add, null) },
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
                    LoadingIndicator()
                }

                uiState.routes.isEmpty() -> {
                    EmptyState(
                        icon = LjIcons.PlayArrow,
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
                                modifier = Modifier.animateItem(),
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
    onStartReplay: (com.locationjoystick.core.model.Route, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActiveRoute = playbackState.activeRouteId == route.id
    val isPlaying = isActiveRoute && playbackState.isPlaying
    val isPaused = isActiveRoute && playbackState.isPaused
    var menuExpanded by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }

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
            modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
    ) {
        // Single row: name + distance + play/controls + 3-dot menu
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
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
            when {
                isPlaying -> {
                    IconButton(onClick = onPauseReplay) {
                        Icon(LjIcons.Pause, contentDescription = "Pause")
                    }
                    IconButton(onClick = onStopReplay) {
                        Icon(LjIcons.Stop, contentDescription = "Stop")
                    }
                }

                isPaused -> {
                    IconButton(onClick = onResumeReplay) {
                        Icon(LjIcons.PlayArrow, contentDescription = "Resume")
                    }
                    IconButton(onClick = onStopReplay) {
                        Icon(LjIcons.Stop, contentDescription = "Stop")
                    }
                }

                else -> {
                    IconButton(onClick = { showStartDialog = true }) {
                        Icon(LjIcons.PlayArrow, contentDescription = "Start route")
                    }
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(LjIcons.MoreVert, contentDescription = "Menu")
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
                        leadingIcon = { Icon(LjIcons.Edit, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Export") },
                        onClick = {
                            menuExpanded = false
                            onExport(route)
                        },
                        leadingIcon = { Icon(LjIcons.FileDownload, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDeleteRoute(route)
                        },
                        leadingIcon = { Icon(LjIcons.Delete, contentDescription = null) },
                    )
                }
            }
        }
    }

    if (showStartDialog) {
        StartRouteDialog(
            onDismiss = { showStartDialog = false },
            onStart = { isLooping, isReverse, isReturnToLocation, teleportToStart ->
                onStartReplay(route, isLooping, isReverse, isReturnToLocation, teleportToStart)
                showStartDialog = false
            },
        )
    }
}

@Composable
private fun StartRouteDialog(
    onDismiss: () -> Unit,
    onStart: (isLooping: Boolean, isReverse: Boolean, isReturnToLocation: Boolean, teleportToStart: Boolean) -> Unit,
) {
    var loop by remember { mutableStateOf(false) }
    var reverse by remember { mutableStateOf(false) }
    var returnToLocation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start route") },
        text = {
            Column {
                CheckboxRow(label = "Loop", checked = loop, enabled = !returnToLocation, onCheckedChange = { loop = it })
                CheckboxRow(label = "Reverse", checked = reverse, onCheckedChange = { reverse = it })
                CheckboxRow(
                    label = "Return to location",
                    checked = returnToLocation,
                    enabled = !loop,
                    onCheckedChange = { returnToLocation = it },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onStart(loop, reverse, returnToLocation && !loop, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Teleport and start")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(loop, reverse, returnToLocation && !loop, false) }) {
                Text("Walk and start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(
            text = label,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
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
