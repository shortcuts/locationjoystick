package com.locationjoystick.feature.routes.impl

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.MapConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.overlay.OverlayService
import com.locationjoystick.core.ui.component.NominatimSearchBar
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

private const val OSM_SOURCE_ID = "osm-source"
private const val OSM_LAYER_ID = "osm-layer"
private const val CURRENT_POS_SOURCE_ID = "current-pos-source"
private const val CURRENT_POS_LAYER_ID = "current-pos-layer"
private const val SEGMENTS_SOURCE_ID = "segments-source"
private const val SEGMENTS_LAYER_ID = "segments-layer"
private const val WAYPOINTS_SOURCE_ID = "waypoints-source"
private const val WAYPOINTS_LAYER_ID = "waypoints-layer"

@Composable
fun RouteCreatorRoute(
    onRouteSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: RouteCreatorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    RouteCreatorScreen(
        state = state,
        initialPosition = viewModel.currentSpoofPosition,
        onAddWaypoint = viewModel::addWaypoint,
        onUndo = viewModel::undoLastWaypoint,
        onSaveRoute = { name ->
            viewModel.saveRoute(name)
            onRouteSaved()
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteCreatorScreen(
    state: CreatorState,
    initialPosition: LatLng? = null,
    onAddWaypoint: (LatLng) -> Unit,
    onUndo: () -> Unit,
    onSaveRoute: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val segmentsSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val waypointsSource = remember { mutableStateOf<GeoJsonSource?>(null) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Create Route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search location")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = onUndo,
                    containerColor =
                        if (state.waypoints.isNotEmpty()) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo last waypoint",
                        tint =
                            if (state.waypoints.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(24.dp),
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = { if (state.waypoints.size >= 2) showSaveDialog = true },
                    expanded = state.waypoints.size >= 2,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                        )
                    },
                    text = { Text("Save Route") },
                )
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
                                            MapLatLng(MapConstants.DEFAULT_LAT, MapConstants.DEFAULT_LON)
                                        },
                                    ).zoom(MapConstants.DEFAULT_ZOOM)
                                    .build()

                            map.setStyle(Style.Builder().fromUri("asset://empty.json")) { style ->
                                style.addSource(
                                    RasterSource(
                                        OSM_SOURCE_ID,
                                        TileSet("2.2.0", MapConstants.OSM_TILE_URL).apply { maxZoom = 19f },
                                        256,
                                    ),
                                )
                                style.addLayer(RasterLayer(OSM_LAYER_ID, OSM_SOURCE_ID))

                                if (initialPosition != null) {
                                    val currentPosSrc = GeoJsonSource(
                                        CURRENT_POS_SOURCE_ID,
                                        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${initialPosition.longitude},${initialPosition.latitude}]},"properties":{}}]}""",
                                    )
                                    style.addSource(currentPosSrc)
                                    style.addLayer(
                                        CircleLayer(CURRENT_POS_LAYER_ID, CURRENT_POS_SOURCE_ID)
                                            .withProperties(
                                                PropertyFactory.circleRadius(9f),
                                                PropertyFactory.circleColor(Color(0xFF1976D2).toArgb()),
                                                PropertyFactory.circleStrokeColor(Color(0xFFFFFFFF).toArgb()),
                                                PropertyFactory.circleStrokeWidth(2f),
                                            ),
                                    )
                                }

                                val segSrc = GeoJsonSource(SEGMENTS_SOURCE_ID, emptyGeoJson())
                                style.addSource(segSrc)
                                style.addLayer(
                                    LineLayer(SEGMENTS_LAYER_ID, SEGMENTS_SOURCE_ID)
                                        .withProperties(
                                            PropertyFactory.lineColor(
                                                Color(0xFF2196F3).toArgb(),
                                            ),
                                            PropertyFactory.lineWidth(3f),
                                        ),
                                )
                                segmentsSource.value = segSrc

                                val wpSrc = GeoJsonSource(WAYPOINTS_SOURCE_ID, emptyGeoJson())
                                style.addSource(wpSrc)
                                style.addLayer(
                                    CircleLayer(WAYPOINTS_LAYER_ID, WAYPOINTS_SOURCE_ID)
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

private fun emptyGeoJson(): String = """{"type":"FeatureCollection","features":[]}"""

private fun buildSegmentsGeoJson(segments: List<List<LatLng>>): String {
    if (segments.isEmpty()) return emptyGeoJson()

    val features =
        segments.map { segment ->
            val coordinates = segment.map { listOf(it.longitude, it.latitude) }
            """{"type":"Feature","geometry":{"type":"LineString","coordinates":$coordinates},"properties":{}}"""
        }

    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun buildWaypointsGeoJson(waypoints: List<LatLng>): String {
    if (waypoints.isEmpty()) return emptyGeoJson()

    val features =
        waypoints.map { wp ->
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[${wp.longitude},${wp.latitude}]},"properties":{}}"""
        }

    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}
