package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.feature.map.api.MAP_ROUTE
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

private const val OSM_SOURCE_ID = "osm-source"
private const val OSM_LAYER_ID = "osm-layer"
private const val POSITION_SOURCE_ID = "position-source"
private const val POSITION_LAYER_ID = "position-layer"
private const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private const val DEFAULT_ZOOM = 12.0
private const val DEFAULT_LAT = 48.8566
private const val DEFAULT_LON = 2.3522

fun NavGraphBuilder.mapScreen(onOpenDrawer: () -> Unit) {
    composable(route = MAP_ROUTE) {
        MapRoute(onOpenDrawer = onOpenDrawer)
    }
}

@Composable
fun MapRoute(
    onOpenDrawer: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MapScreen(uiState = uiState, onOpenDrawer = onOpenDrawer, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapScreen(
    uiState: MapUiState,
    onOpenDrawer: () -> Unit,
    onAction: (MapAction) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapLibre.getInstance(context); MapView(context) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val positionSource = remember { mutableStateOf<GeoJsonSource?>(null) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Open menu")
                    }
                }
            )
        },
        floatingActionButton = {
            MapFab(
                isSpoofing = uiState.isSpoofing,
                onStart = { onAction(MapAction.StartSpoofing) },
                onStop = { onAction(MapAction.StopSpoofing) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            AndroidView(
                factory = { _ ->
                    mapView.apply {
                        getMapAsync { map ->
                            mapRef.value = map
                            map.uiSettings.isAttributionEnabled = false
                            map.uiSettings.isLogoEnabled = false
                            map.uiSettings.isScrollGesturesEnabled = true
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

                                val src = GeoJsonSource(POSITION_SOURCE_ID, emptyGeoJson())
                                style.addSource(src)
                                style.addLayer(
                                    CircleLayer(POSITION_LAYER_ID, POSITION_SOURCE_ID)
                                        .withProperties(
                                            PropertyFactory.circleRadius(10f),
                                            PropertyFactory.circleColor("#1E88E5"),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(2f),
                                        ),
                                )
                                positionSource.value = src
                            }

                            map.addOnMapClickListener { latLng ->
                                onAction(
                                    MapAction.TapToTeleport(
                                        com.locationjoystick.core.model.LatLng(
                                            latitude = latLng.latitude,
                                            longitude = latLng.longitude,
                                        ),
                                    ),
                                )
                                true
                            }

                            map.addOnMapLongClickListener { latLng ->
                                onAction(
                                    MapAction.LongPressTapToWalk(
                                        com.locationjoystick.core.model.LatLng(
                                            latitude = latLng.latitude,
                                            longitude = latLng.longitude,
                                        ),
                                    ),
                                )
                                true
                            }

                            map.addOnCameraMoveStartedListener { reason ->
                                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                    onAction(MapAction.UserStartedPanning)
                                }
                            }
                        }
                    }
                },
                update = { _ ->
                    val src = positionSource.value ?: return@AndroidView
                    val map = mapRef.value ?: return@AndroidView
                    val position = uiState.currentPosition

                    src.setGeoJson(buildPositionGeoJson(position))

                    if (!uiState.isUserPanning && position != null) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLng(
                                MapLatLng(position.latitude, position.longitude),
                            ),
                            500,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MapFab(
    isSpoofing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    FloatingActionButton(
        onClick = if (isSpoofing) onStop else onStart,
        containerColor = if (isSpoofing) MaterialTheme.colorScheme.error else Color(0xFF43A047),
        contentColor = if (isSpoofing) MaterialTheme.colorScheme.onError else Color.White,
    ) {
        Icon(
            imageVector = if (isSpoofing) LjIcons.Stop else LjIcons.PlayArrow,
            contentDescription = if (isSpoofing) "Stop location simulation" else "Start location simulation",
        )
    }
}

private fun emptyGeoJson(): String =
    """{"type":"FeatureCollection","features":[]}"""

private fun buildPositionGeoJson(position: com.locationjoystick.core.model.LatLng?): String =
    if (position != null) {
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${position.longitude},${position.latitude}]},"properties":{}}]}"""
    } else {
        emptyGeoJson()
    }

@Preview(showBackground = true)
@Composable
private fun MapFabStartPreview() {
    LjTheme {
        MapFab(isSpoofing = false, onStart = {}, onStop = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun MapFabStopPreview() {
    LjTheme {
        MapFab(isSpoofing = true, onStart = {}, onStop = {})
    }
}
