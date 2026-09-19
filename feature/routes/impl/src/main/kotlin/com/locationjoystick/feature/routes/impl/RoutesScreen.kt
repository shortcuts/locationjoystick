package com.locationjoystick.feature.routes.impl

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.util.formatCapturedPointsForClipboard
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.DeleteItemType
import com.locationjoystick.core.designsystem.component.EmptyState
import com.locationjoystick.core.designsystem.component.ListSearchField
import com.locationjoystick.core.designsystem.component.LjActionSheetRow
import com.locationjoystick.core.designsystem.component.LjDeleteConfirmDialog
import com.locationjoystick.core.designsystem.component.LjListItemCard
import com.locationjoystick.core.designsystem.component.LjListItemCardSkeletonList
import com.locationjoystick.core.designsystem.component.LjOverflowMenuSectionLabel
import com.locationjoystick.core.designsystem.component.LjPrimaryButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.RouteStartSheetContent
import com.locationjoystick.core.designsystem.component.SavedItemSortMenu
import com.locationjoystick.core.designsystem.component.WideContentClamp
import com.locationjoystick.core.designsystem.component.rememberLjSheetState
import com.locationjoystick.core.location.RouteStartConfig
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.distanceTo
import com.locationjoystick.core.model.matchesSearch
import com.locationjoystick.core.model.startWaypoint
import com.locationjoystick.feature.routes.impl.R

@Composable
fun RoutesRoute(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCreate: (RouteType) -> Unit,
    onNavigateToPaste: () -> Unit,
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
        onNavigateToPaste = onNavigateToPaste,
        onImportGpx = onImportGpx,
        onOpenDrawer = onOpenDrawer,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onDeleteRoute = viewModel::deleteRoute,
        onExportRoute = { route -> viewModel.exportRouteAsGpx(context, route) },
        onStartReplay = viewModel::startReplay,
        onTeleportToRouteStart = viewModel::teleportTo,
        onPauseReplay = viewModel::pauseReplay,
        onResumeReplay = viewModel::resumeReplay,
        onStopReplay = viewModel::stopReplay,
        onSortModeSelected = viewModel::setSortMode,
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
        onNavigateToPaste = {},
        onImportGpx = {},
        onOpenDrawer = {},
        onDeleteRoute = {},
        onExportRoute = {},
        onStartReplay = { _, _ -> },
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
    onNavigateToPaste: () -> Unit,
    onImportGpx: () -> Unit,
    onOpenDrawer: () -> Unit,
    onDeleteRoute: (String) -> Unit,
    onExportRoute: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, RouteStartConfig) -> Unit,
    onTeleportToRouteStart: (com.locationjoystick.core.model.LatLng) -> Unit,
    onPauseReplay: () -> Unit,
    onResumeReplay: () -> Unit,
    onStopReplay: () -> Unit,
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    onSortModeSelected: (com.locationjoystick.core.model.SavedItemSortMode) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    var deletingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }
    var sharingRoute by remember { mutableStateOf<com.locationjoystick.core.model.Route?>(null) }
    var showAddOptionsSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LjScaffold(
        title = stringResource(R.string.routes_screen_title),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            SavedItemSortMenu(selected = uiState.sortMode, onSelected = onSortModeSelected)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptionsSheet = true }) {
                Icon(LjIcons.Add, contentDescription = stringResource(R.string.routes_screen_add_route))
            }
        },
    ) { paddingValues ->
        val filteredRoutes =
            remember(uiState.routes, searchQuery) {
                uiState.routes.filter { it.matchesSearch(searchQuery) }
            }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            WideContentClamp(modifier = Modifier.fillMaxSize()) {
                if (uiState.routes.isNotEmpty()) {
                    ListSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        label = stringResource(R.string.routes_screen_search_routes),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> {
                            LjListItemCardSkeletonList(trailingIconCount = 2)
                        }

                        uiState.routes.isEmpty() -> {
                            EmptyState(
                                icon = LjIcons.PlayArrow,
                                message = stringResource(R.string.routes_no_routes_yet),
                                modifier = Modifier.align(Alignment.Center),
                                action = {
                                    LjPrimaryButton(
                                        text = stringResource(R.string.routes_add_a_route),
                                        onClick = { showAddOptionsSheet = true },
                                    )
                                },
                            )
                        }

                        filteredRoutes.isEmpty() -> {
                            EmptyState(
                                icon = LjIcons.Search,
                                message = stringResource(R.string.routes_no_routes_match_your_search),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    items = filteredRoutes,
                                    key = { it.id },
                                ) { route ->
                                    RouteCard(
                                        modifier = Modifier.animateItem(),
                                        route = route,
                                        playbackState = playbackState,
                                        onNavigateToEdit = { onNavigateToDetail(route.id) },
                                        onDeleteRoute = { deletingRoute = route },
                                        onShare = { sharingRoute = route },
                                        onExport = { onExportRoute(route) },
                                        onStartReplay = onStartReplay,
                                        onTeleportToRouteStart = onTeleportToRouteStart,
                                        onPauseReplay = onPauseReplay,
                                        onResumeReplay = onResumeReplay,
                                        onStopReplay = onStopReplay,
                                        hideTeleportFeatures = uiState.hideTeleportFeatures,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletingRoute?.let { route ->
        LjDeleteConfirmDialog(
            name = route.name,
            itemType = DeleteItemType.ROUTE,
            onDismiss = { deletingRoute = null },
            onConfirm = {
                onDeleteRoute(route.id)
                deletingRoute = null
            },
        )
    }

    sharingRoute?.let { route ->
        RouteShareDialog(
            route = route,
            onDismiss = { sharingRoute = null },
        )
    }

    if (showAddOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddOptionsSheet = false },
            sheetState = rememberLjSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.routes_screen_add_a_route), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                LjActionSheetRow(
                    icon = LjIcons.Map,
                    title = stringResource(R.string.routes_screen_draw_on_map),
                    onClick = {
                        showAddOptionsSheet = false
                        onNavigateToCreate(RouteType.STRAIGHT)
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.Map,
                    title = stringResource(R.string.routes_screen_draw_on_map_follow_roads),
                    onClick = {
                        showAddOptionsSheet = false
                        onNavigateToCreate(RouteType.GUIDED)
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.Map,
                    title = stringResource(R.string.routes_screen_draw_on_map_teleport),
                    onClick = {
                        showAddOptionsSheet = false
                        onNavigateToCreate(RouteType.TELEPORT)
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.ContentPaste,
                    title = stringResource(R.string.routes_screen_paste_coordinates),
                    onClick = {
                        showAddOptionsSheet = false
                        onNavigateToPaste()
                    },
                )
                LjActionSheetRow(
                    icon = LjIcons.Add,
                    title = stringResource(R.string.routes_screen_import_gpx_file),
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
    onShare: () -> Unit,
    onExport: (com.locationjoystick.core.model.Route) -> Unit,
    onStartReplay: (com.locationjoystick.core.model.Route, RouteStartConfig) -> Unit,
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
    val startSheetState = rememberLjSheetState()

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
        modifier = modifier.fillMaxWidth(),
    ) {
        LjListItemCard(
            trailing = {
                when {
                    isPlaying -> {
                        IconButton(onClick = onPauseReplay) {
                            Crossfade(
                                targetState = LjIcons.Pause,
                                animationSpec = tween(150),
                                label = "routePlayIcon",
                            ) { icon ->
                                Icon(icon, contentDescription = stringResource(R.string.routes_screen_pause))
                            }
                        }
                        IconButton(onClick = onStopReplay) {
                            Icon(LjIcons.Stop, contentDescription = stringResource(R.string.routes_screen_stop_cd))
                        }
                    }

                    isPaused -> {
                        IconButton(onClick = onResumeReplay) {
                            Crossfade(
                                targetState = LjIcons.PlayArrow,
                                animationSpec = tween(150),
                                label = "routePlayIcon",
                            ) { icon ->
                                Icon(icon, contentDescription = stringResource(R.string.routes_screen_resume))
                            }
                        }
                        IconButton(onClick = onStopReplay) {
                            Icon(LjIcons.Stop, contentDescription = stringResource(R.string.routes_screen_stop_cd))
                        }
                    }

                    else -> {
                        IconButton(onClick = { showStartDialog = true }) {
                            Crossfade(
                                targetState = LjIcons.PlayArrow,
                                animationSpec = tween(150),
                                label = "routePlayIcon",
                            ) { icon ->
                                Icon(icon, contentDescription = stringResource(R.string.routes_screen_start_route))
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(LjIcons.MoreVert, contentDescription = stringResource(R.string.routes_screen_menu_cd))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.routes_screen_edit)) },
                            onClick = {
                                menuExpanded = false
                                onNavigateToEdit(route.id)
                            },
                            leadingIcon = { Icon(LjIcons.Edit, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.routes_screen_share)) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                            leadingIcon = { Icon(LjIcons.Share, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.routes_screen_export)) },
                            onClick = {
                                menuExpanded = false
                                onExport(route)
                            },
                            leadingIcon = { Icon(LjIcons.FileDownload, contentDescription = null) },
                        )
                        LjOverflowMenuSectionLabel(stringResource(R.string.routes_screen_danger))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.routes_screen_delete), color = MaterialTheme.colorScheme.error) },
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
    }

    if (showStartDialog) {
        ModalBottomSheet(
            onDismissRequest = { showStartDialog = false },
            sheetState = startSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.routes_screen_start_route), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                RouteStartSheetContent(
                    key = route.id,
                    onTeleport = { reverse ->
                        route.startWaypoint(reverse)?.let { onTeleportToRouteStart(it.position) }
                    },
                    onStart = {
                        loop,
                        reverse,
                        returnToLocation,
                        followRoads,
                        planting,
                        teleportBetweenWaypoints,
                        delaySeconds,
                        ->
                        onStartReplay(
                            route,
                            RouteStartConfig(
                                isLooping = loop,
                                isReverse = reverse,
                                isReturnToLocation = returnToLocation,
                                followRoadsToStart = followRoads,
                                isPlanting = planting,
                                teleportBetweenWaypoints = teleportBetweenWaypoints,
                                teleportBetweenDelaySeconds = delaySeconds,
                            ),
                        )
                        if (!followRoads) showStartDialog = false
                    },
                    onCancel = { showStartDialog = false },
                    hideTeleport = hideTeleportFeatures,
                    isTeleportRoute = route.routeType == RouteType.TELEPORT,
                    isRoadRouteFetchInFlight = isRoadRouteFetchInFlight,
                )
            }
        }
    }
}

@Composable
private fun RouteShareDialog(
    route: com.locationjoystick.core.model.Route,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.routes_copied_coordinates)
    val coordText =
        remember(route.waypoints) {
            formatCapturedPointsForClipboard(route.waypoints.map { it.position })
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routes_share_title, route.name)) },
        text = {
            OutlinedTextField(
                value = coordText,
                onValueChange = {},
                readOnly = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp),
            )
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(coordText))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(stringResource(R.string.routes_screen_copy))
                }
                TextButton(
                    onClick = {
                        val shareIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, coordText)
                            }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    },
                ) {
                    Text(stringResource(R.string.routes_screen_send_as_message))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.routes_screen_close))
            }
        },
    )
}
