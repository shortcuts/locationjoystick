package com.locationjoystick.feature.routes.impl

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.locationjoystick.core.map.geojson.buildSegmentsGeoJson
import com.locationjoystick.core.map.geojson.buildWaypointsGeoJson
import com.locationjoystick.core.map.geojson.emptyGeoJson
import com.locationjoystick.core.map.maplibre.MapLibreLayerIds
import com.locationjoystick.core.map.maplibre.MapLibreSourceIds
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.overlay.OverlayService
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.geometry.LatLng as MapLatLng

private val OSM_SOURCE_ID = AppConstants.MapConstants.OSM_SOURCE_ID
private val OSM_LAYER_ID = AppConstants.MapConstants.OSM_LAYER_ID

@Composable
fun RouteCreatorRoute(
    onRouteSaved: () -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: RouteCreatorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val livePosition by viewModel.livePosition.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()

    RouteCreatorScreen(
        state = state,
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
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun RouteCreatorScreenPreview() {
    RouteCreatorScreen(
        state = CreatorState(),
        initialPosition = null,
        onAddWaypoint = {},
        onUndo = {},
        onSaveRoute = {},
        onBack = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteCreatorScreen(
    state: CreatorState,
    initialPosition: LatLng? = null,
    favorites: List<FavoriteLocation> = emptyList(),
    currentPosition: LatLng? = null,
    recentSearches: List<RecentSearch> = emptyList(),
    onAddWaypoint: (LatLng) -> Unit,
    onUndo: () -> Unit,
    onSaveRoute: (String) -> Unit,
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val segmentsSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val waypointsSource = remember { mutableStateOf<GeoJsonSource?>(null) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showFavoritesSheet by remember { mutableStateOf(false) }

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
        title = "Create Route",
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
                    contentDescription = "Search location",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showSearch = !showSearch },
                )
                if (currentPosition != null) {
                    LjMapIconButton(
                        icon = LjIcons.MyLocation,
                        contentDescription = "Center on location",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {
                            mapRef.value?.animateCamera(
                                CameraUpdateFactory.newLatLng(
                                    MapLatLng(currentPosition.latitude, currentPosition.longitude),
                                ),
                                500,
                            )
                        },
                    )
                }
                LjMapIconButton(
                    icon = LjIcons.Favorite,
                    contentDescription = "Pick from favorites",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { showFavoritesSheet = true },
                )
                LjMapIconButton(
                    icon = LjIcons.Undo,
                    contentDescription = "Undo last waypoint",
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
                        contentDescription = "Save route",
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
                factory = { ctx ->
                    MapLibre.getInstance(ctx)
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

                            map.setStyle(Style.Builder().fromUri("asset://empty.json")) { style ->
                                style.addSource(
                                    RasterSource(
                                        OSM_SOURCE_ID,
                                        TileSet(AppConstants.MapConstants.TILESET_VERSION, AppConstants.MapConstants.OSM_TILE_URL).apply {
                                            maxZoom =
                                                AppConstants.MapConstants.OSM_MAX_ZOOM
                                        },
                                        256,
                                    ),
                                )
                                style.addLayer(RasterLayer(OSM_LAYER_ID, OSM_SOURCE_ID))

                                if (initialPosition != null) {
                                    val currentPosSrc =
                                        GeoJsonSource(
                                            MapLibreSourceIds.CURRENT_POS,
                                            """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${initialPosition.longitude},${initialPosition.latitude}]},"properties":{}}]}""",
                                        )
                                    style.addSource(currentPosSrc)
                                    style.addLayer(
                                        CircleLayer(MapLibreLayerIds.CURRENT_POS, MapLibreSourceIds.CURRENT_POS)
                                            .withProperties(
                                                PropertyFactory.circleRadius(9f),
                                                PropertyFactory.circleColor(Color(0xFF1976D2).toArgb()),
                                                PropertyFactory.circleStrokeColor(Color(0xFFFFFFFF).toArgb()),
                                                PropertyFactory.circleStrokeWidth(2f),
                                            ),
                                    )
                                }

                                val segSrc = GeoJsonSource(MapLibreSourceIds.ROUTE_SEGMENTS, emptyGeoJson())
                                style.addSource(segSrc)
                                style.addLayer(
                                    LineLayer(MapLibreLayerIds.ROUTE_SEGMENTS, MapLibreSourceIds.ROUTE_SEGMENTS)
                                        .withProperties(
                                            PropertyFactory.lineColor(
                                                Color(0xFF2196F3).toArgb(),
                                            ),
                                            PropertyFactory.lineWidth(3f),
                                        ),
                                )
                                segmentsSource.value = segSrc

                                val wpSrc = GeoJsonSource(MapLibreSourceIds.ROUTE_WAYPOINTS, emptyGeoJson())
                                style.addSource(wpSrc)
                                style.addLayer(
                                    CircleLayer(MapLibreLayerIds.ROUTE_WAYPOINTS, MapLibreSourceIds.ROUTE_WAYPOINTS)
                                        .withProperties(
                                            PropertyFactory.circleRadius(8f),
                                            PropertyFactory.circleColor(
                                                Color(0xFF4CAF50).toArgb(),
                                            ),
                                            PropertyFactory.circleStrokeColor(
                                                Color(0xFFFFFFFF).toArgb(),
                                            ),
                                            PropertyFactory.circleStrokeWidth(2f),
                                        ),
                                )
                                waypointsSource.value = wpSrc
                            }

                            map.addOnMapClickListener { latLng ->
                                onAddWaypoint(LatLng(latLng.latitude, latLng.longitude))
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
                        onAddWaypoint(LatLng(lat, lon))
                        showSearch = false
                        val map = mapRef.value ?: return@NominatimSearchBar
                        map.animateCamera(
                            CameraUpdateFactory.newLatLng(MapLatLng(lat, lon)),
                            500,
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
                mapRef.value?.animateCamera(
                    CameraUpdateFactory.newLatLng(MapLatLng(position.latitude, position.longitude)),
                    500,
                )
            },
            onDismiss = { showFavoritesSheet = false },
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        FavoritesList(
            title = "Jump to Favorite",
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
        title = { Text("Save Route") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Route name") },
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
