package com.locationjoystick.feature.routes.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.ui.component.NominatimSearchBar
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLatLng
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

private const val OSM_SOURCE_ID = "osm-source"
private const val OSM_LAYER_ID = "osm-layer"
private const val SEGMENTS_SOURCE_ID = "segments-source"
private const val SEGMENTS_LAYER_ID = "segments-layer"
private const val WAYPOINTS_SOURCE_ID = "waypoints-source"
private const val WAYPOINTS_LAYER_ID = "waypoints-layer"
private const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private const val DEFAULT_ZOOM = 15.0
private const val DEFAULT_LAT = 48.8566
private const val DEFAULT_LON = 2.3522

@Composable
fun RouteCreatorRoute(
    onRouteSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: RouteCreatorViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    RouteCreatorScreen(
        state = state,
        onAddWaypoint = viewModel::addWaypoint,
        onUndo = viewModel::undoLastWaypoint,
        onSaveRoute = { name ->
            viewModel.saveRoute(name)
            onRouteSaved()
        },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteCreatorScreen(
    state: CreatorState,
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Create Route") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        NominatimSearchBar(
            onLocationSelected = { lat, lon, _ ->
                onAddWaypoint(LatLng(lat, lon))
                val map = mapRef.value ?: return@NominatimSearchBar
                map.animateCamera(
                    CameraUpdateFactory.newLatLng(MapLatLng(lat, lon)),
                    500
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    MapLibre.getInstance(ctx)
                    mapView.apply {
                        getMapAsync { map ->
                            mapRef.value = map
                            map.uiSettings.isAttributionEnabled = false
                            map.uiSettings.isLogoEnabled = false
                            map.cameraPosition = CameraPosition.Builder()
                                .target(MapLatLng(DEFAULT_LAT, DEFAULT_LON))
                                .zoom(DEFAULT_ZOOM)
                                .build()

                            map.setStyle(Style.Builder().fromUri("asset://empty.json")) { style ->
                                style.addSource(
                                    RasterSource(
                                        OSM_SOURCE_ID,
                                        TileSet("2.2.0", OSM_TILE_URL).apply { maxZoom = 19f },
                                        256,
                                    ),
                                )
                                style.addLayer(RasterLayer(OSM_LAYER_ID, OSM_SOURCE_ID))

                                val segSrc = GeoJsonSource(SEGMENTS_SOURCE_ID, emptyGeoJson())
                                style.addSource(segSrc)
                                style.addLayer(
                                    LineLayer(SEGMENTS_LAYER_ID, SEGMENTS_SOURCE_ID)
                                        .withProperties(
                                            PropertyFactory.lineColor("#2196F3"),
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
                                            PropertyFactory.circleColor("#4CAF50"),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
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
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Waypoints: ${state.waypoints.size} | Distance: ${String.format("%.2f", state.totalDistanceMeters / 1000)}km",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onUndo,
                    enabled = state.waypoints.isNotEmpty()
                ) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo")
                }
            }

            Button(
                onClick = { showSaveDialog = true },
                enabled = state.waypoints.size >= 2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Route")
            }
        }
    }

    if (showSaveDialog) {
        SaveRouteDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onSaveRoute(name)
                showSaveDialog = false
            }
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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

private fun emptyGeoJson(): String =
    """{"type":"FeatureCollection","features":[]}"""

private fun buildSegmentsGeoJson(segments: List<List<LatLng>>): String {
    if (segments.isEmpty()) return emptyGeoJson()

    val features = segments.map { segment ->
        val coordinates = segment.map { listOf(it.longitude, it.latitude) }
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":$coordinates},"properties":{}}"""
    }

    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun buildWaypointsGeoJson(waypoints: List<LatLng>): String {
    if (waypoints.isEmpty()) return emptyGeoJson()

    val features = waypoints.map { wp ->
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${wp.longitude},${wp.latitude}]},"properties":{}}"""
    }

    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}
