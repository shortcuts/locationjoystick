package com.locationjoystick.feature.routes.impl

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.FavoritesList
import com.locationjoystick.core.designsystem.component.LjMapIconButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.NominatimSearchBar
import com.locationjoystick.core.designsystem.component.rememberLjSheetState
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.map.geojson.buildPositionGeoJson
import com.locationjoystick.core.map.geojson.buildSegmentsGeoJson
import com.locationjoystick.core.map.geojson.buildWaypointsGeoJson
import com.locationjoystick.core.map.maplibre.addCreatorLayers
import com.locationjoystick.core.map.maplibre.rememberMapView
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.overlay.OverlayService
import com.locationjoystick.feature.routes.impl.R
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.geometry.LatLng as MapLatLng

@Composable
fun RouteCreatorRoute(
    onRouteSaved: () -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: RouteCreatorViewModel = hiltViewModel()
    val spoofToggle = rememberSpoofToggleState()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val livePosition by viewModel.livePosition.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()

    RouteCreatorScreen(
        state = state,
        routeType = viewModel.routeType,
        initialPosition = viewModel.currentPosition,
        favorites = favorites,
        currentPosition = livePosition,
        recentSearches = recentSearches,
        onAddWaypoint = viewModel::addWaypoint,
        onUndo = viewModel::undoLastWaypoint,
        onSaveRoute = { name ->
            viewModel.saveRoute(name)
            onRouteSaved()
        },
        onSearchCommitted = viewModel::addRecentSearch,
        onBack = onBack,
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun RouteCreatorScreenPreview() {
    RouteCreatorScreen(
        state = CreatorState(),
        routeType = RouteType.STRAIGHT,
        initialPosition = null,
        onAddWaypoint = { _, _ -> },
        onUndo = {},
        onSaveRoute = {},
        onBack = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteCreatorScreen(
    state: CreatorState,
    routeType: RouteType = RouteType.STRAIGHT,
    initialPosition: LatLng? = null,
    favorites: List<FavoriteLocation> = emptyList(),
    currentPosition: LatLng? = null,
    recentSearches: List<RecentSearch> = emptyList(),
    onAddWaypoint: (LatLng, Int) -> Unit,
    onUndo: () -> Unit,
    onSaveRoute: (String) -> Unit,
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
    onBack: () -> Unit,
    isSpoofing: Boolean = false,
    onToggleSpoofing: () -> Unit = {},
    locationLabel: String? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = rememberMapView()
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val segmentsSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val waypointsSource = remember { mutableStateOf<GeoJsonSource?>(null) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showFavoritesSheet by remember { mutableStateOf(false) }
    var pendingWaitPrompt by remember { mutableStateOf<LatLng?>(null) }

    LaunchedEffect(showSaveDialog) {
        context.sendBroadcast(
            Intent(
                if (showSaveDialog) {
                    OverlayService.ACTION_OVERLAY_HIDE
                } else {
                    OverlayService.ACTION_OVERLAY_SHOW
                },
            ),
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LjScaffold(
        title = stringResource(R.string.route_creator_create_route),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onBack,
        navigationIcon = LjIcons.ArrowBack,
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(UiConstants.FAB_SPACING),
            ) {
                LjMapIconButton(
                    icon = LjIcons.Search,
                    contentDescription = stringResource(R.string.route_creator_search_location_cd),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showSearch = !showSearch },
                )
                if (currentPosition != null) {
                    LjMapIconButton(
                        icon = LjIcons.MyLocation,
                        contentDescription = stringResource(R.string.route_creator_center_on_location_cd),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {
                            mapRef.value?.moveCamera(
                                CameraUpdateFactory.newLatLng(
                                    MapLatLng(currentPosition.latitude, currentPosition.longitude),
                                ),
                            )
                        },
                    )
                }
                LjMapIconButton(
                    icon = LjIcons.Favorite,
                    contentDescription = stringResource(R.string.route_creator_pick_from_favorites_cd),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { showFavoritesSheet = true },
                )
                LjMapIconButton(
                    icon = LjIcons.Undo,
                    contentDescription = stringResource(R.string.route_creator_undo_last_waypoint_cd),
                    containerColor =
                        if (state.waypoints.isNotEmpty()) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    contentColor =
                        if (state.waypoints.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    onClick = onUndo,
                )
                if (state.waypoints.size >= 2) {
                    LjMapIconButton(
                        icon = LjIcons.Save,
                        contentDescription = stringResource(R.string.route_creator_save_route),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { showSaveDialog = true },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding()),
        ) {
            AndroidView(
                factory = { _ ->
                    mapView.apply {
                        getMapAsync { map ->
                            mapRef.value = map
                            map.uiSettings.isAttributionEnabled = false
                            map.uiSettings.isLogoEnabled = false
                            map.cameraPosition =
                                CameraPosition
                                    .Builder()
                                    .target(
                                        if (initialPosition != null) {
                                            MapLatLng(initialPosition.latitude, initialPosition.longitude)
                                        } else {
                                            MapLatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON)
                                        },
                                    ).zoom(AppConstants.MapConstants.DEFAULT_ZOOM)
                                    .build()

                            map.setStyle(Style.Builder().fromUri(AppConstants.MapConstants.EMPTY_MAP_STYLE_URI)) { style ->
                                val layers =
                                    style.addCreatorLayers(
                                        currentPosGeoJson = initialPosition?.let { buildPositionGeoJson(it) },
                                    )
                                segmentsSource.value = layers.segmentsSource
                                waypointsSource.value = layers.waypointsSource
                            }

                            map.addOnMapClickListener { latLng ->
                                val position = LatLng(latLng.latitude, latLng.longitude)
                                if (routeType == RouteType.TELEPORT) {
                                    pendingWaitPrompt = position
                                } else {
                                    onAddWaypoint(position, 0)
                                }
                                true
                            }
                        }
                    }
                },
                update = { _ ->
                    val segSrc = segmentsSource.value ?: return@AndroidView
                    val wpSrc = waypointsSource.value ?: return@AndroidView

                    segSrc.setGeoJson(buildSegmentsGeoJson(state.segments))
                    wpSrc.setGeoJson(buildWaypointsGeoJson(state.waypoints))
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (state.isLoadingSegment) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Search bar — top overlay, shown when toggled
            if (showSearch) {
                NominatimSearchBar(
                    onLocationSelected = { lat, lon, _ ->
                        // Search only recenters the map — the user places waypoints themselves via map tap,
                        // matching the long-press/tap-to-add flow (issue: auto-added a waypoint on search).
                        showSearch = false
                        val map = mapRef.value ?: return@NominatimSearchBar
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(MapLatLng(lat, lon), AppConstants.MapConstants.DEFAULT_ZOOM),
                        )
                    },
                    recentSearches = recentSearches,
                    onSearchCommitted = onSearchCommitted,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(
                                top = paddingValues.calculateTopPadding() + 8.dp,
                                start = 12.dp,
                                end = 12.dp,
                            ),
                )
            }
        }
    }

    if (showSaveDialog) {
        SaveRouteDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onSaveRoute(name)
                showSaveDialog = false
            },
        )
    }

    if (showFavoritesSheet) {
        CreatorFavoritesSheet(
            favorites = favorites,
            onSelect = { position ->
                showFavoritesSheet = false
                mapRef.value?.moveCamera(
                    CameraUpdateFactory.newLatLng(MapLatLng(position.latitude, position.longitude)),
                )
            },
            onDismiss = { showFavoritesSheet = false },
        )
    }

    pendingWaitPrompt?.let { position ->
        TeleportWaitDialog(
            onDismiss = { pendingWaitPrompt = null },
            onConfirm = { seconds ->
                onAddWaypoint(position, seconds)
                pendingWaitPrompt = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorFavoritesSheet(
    favorites: List<FavoriteLocation>,
    onSelect: (LatLng) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberLjSheetState()) {
        FavoritesList(
            title = stringResource(R.string.route_creator_jump_to_favorite),
            favorites = favorites,
            onSelect = { onSelect(it.position) },
        )
    }
}

@Composable
private fun SaveRouteDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_creator_save_route)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.route_creator_route_name)) },
                modifier = Modifier,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim())
                    }
                },
            ) {
                Text(stringResource(R.string.route_creator_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.route_creator_cancel))
            }
        },
    )
}

@Composable
internal fun TeleportWaitDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    initialSeconds: Int = AppConstants.RouteConstants.DEFAULT_TELEPORT_WAIT_SECONDS,
    titleRes: Int = R.string.route_creator_wait_duration,
) {
    var text by remember { mutableStateOf(initialSeconds.toString()) }
    val seconds = text.toIntOrNull()
    val isValid = seconds != null && seconds >= AppConstants.RouteConstants.MIN_TELEPORT_WAIT_SECONDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.route_creator_seconds_to_wait_here)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { seconds?.let(onConfirm) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.route_creator_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.route_creator_cancel_2))
            }
        },
    )
}
