package com.locationjoystick.feature.routes.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.EmptyState
import com.locationjoystick.core.designsystem.component.LjActionSheetRow
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjDeleteConfirmDialog
import com.locationjoystick.core.designsystem.component.LjListItemCard
import com.locationjoystick.core.designsystem.component.LjListItemCardSkeletonList
import com.locationjoystick.core.designsystem.component.LjOverflowMenu
import com.locationjoystick.core.designsystem.component.LjOverflowMenuSectionLabel
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.RouteStartSheetContent
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.distanceTo
import com.locationjoystick.core.model.startWaypoint

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
        locationLabel = spoofToggle.locationLabel,
        onDeleteRoute = viewModel::deleteRoute,
        onExportRoute = { route -> viewModel.exportRouteAsGpx(context, route) },
        onStartReplay = { route, isLooping, isReverse, isReturnToLocation, followRoadsToStart ->
            viewModel.startReplay(route, isLooping, isReverse, isReturnToLocation, followRoadsToStart)
        },
        onTeleportToRouteStart = viewModel::teleportTo,
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
        onTeleportToRouteStart = {},
        onPauseReplay = {},
        onResumeReplay = {},
        onStopReplay = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onTeleportToRouteStart: (com.locationjoystick.core.model.LatLng) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    onToggleSort: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    var deletingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }

    // Hoisted here (not local to the sheet) so the empty-state CTA task can also flip it, with no new plumbing.
    var showAddOptionsSheet by remember { mutableStateOf(false) }

    LjScaffold(
        title = "Routes",
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            LjOverflowMenu { dismiss ->
                DropdownMenuItem(
                    text = { Text("Sort") },
                    onClick = {
                        dismiss()
                        onToggleSort()
                    },
                    leadingIcon = { Icon(LjIcons.SwapVert, null) },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptionsSheet = true }) {
                Icon(LjIcons.Add, contentDescription = "Add route")
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
                    LjListItemCardSkeletonList(trailingIconCount = 2)
                }

                uiState.routes.isEmpty() -> {
                    EmptyState(
                        icon = LjIcons.PlayArrow,
                        message = "No routes yet",
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            LjButton(onClick = { showAddOptionsSheet = true }) {
                                Text("Draw a route")
                            }
                        },
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                onTeleportToRouteStart = onTeleportToRouteStart,
                                onPauseReplay = onPauseReplay,
                                onResumeReplay = onResumeReplay,
                                onStopReplay = onStopReplay,
                                hideTeleportFeatures = uiState.hideTeleportFeatures,
                                isRoadRouteFetchInFlight = uiState.isRoadRouteFetchInFlight,
                            )
                        }
                    }
                }
            }
        }
    }

    deletingRoute?.let { route ->
        LjDeleteConfirmDialog(
            name = route.name,
            itemType = "route",
            onDismiss = { deletingRoute = null },
            onConfirm = {
                onDeleteRoute(route.id)
                deletingRoute = null
            },
        )
    }

    if (showAddOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddOptionsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Add a route", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                LjActionSheetRow(
                    icon = LjIcons.Map,
                    title = "Draw on map",
                    onClick = {
                        showAddOptionsSheet = false
                        onNavigateToCreate(RouteType.STRAIGHT)
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.Map,
                    title = "Draw on map (follow roads)",
                    onClick = {
                        showAddOptionsSheet = false
                        onNavigateToCreate(RouteType.GUIDED)
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.FileDownload,
                    title = "Import GPX file",
                    onClick = {
                        showAddOptionsSheet = false
                        onImportGpx()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteCard(
    route: com.locationjoystick.core.model.Route,
    playbackState: RoutePlaybackState,
    onNavigateToEdit: (String) -> Unit,
    onDeleteRoute: (com.locationjoystick.core.model.Route) -> Unit,
    onExport: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onTeleportToRouteStart: (com.locationjoystick.core.model.LatLng) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
    modifier: Modifier = Modifier,
    hideTeleportFeatures: Boolean = false,
    isRoadRouteFetchInFlight: Boolean = false,
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

    LjListItemCard(
        modifier = modifier,
        trailing = {
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
                    LjOverflowMenuSectionLabel("Danger")
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDeleteRoute(route)
                        },
                        leadingIcon = {
                            Icon(
                                LjIcons.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        },
    ) {
        val label = if (distanceText.isNotEmpty()) "${route.name} — $distanceText" else route.name
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(
            "${route.waypoints.size} waypoints",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showStartDialog) {
        ModalBottomSheet(
            onDismissRequest = { showStartDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Start route", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                RouteStartSheetContent(
                    key = route.id,
                    onTeleport = { reverse ->
                        route.startWaypoint(reverse)?.let { onTeleportToRouteStart(it.position) }
                    },
                    onStart = { loop, reverse, returnToLocation, followRoads ->
                        onStartReplay(route, loop, reverse, returnToLocation, followRoads)
                        if (!followRoads) showStartDialog = false
                    },
                    onCancel = { showStartDialog = false },
                    hideTeleport = hideTeleportFeatures,
                    isRoadRouteFetchInFlight = isRoadRouteFetchInFlight,
                )
            }
        }
    }
}
