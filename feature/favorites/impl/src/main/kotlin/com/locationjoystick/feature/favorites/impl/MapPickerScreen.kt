package com.locationjoystick.feature.favorites.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.locationjoystick.core.ui.component.NominatimSearchBar
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
private const val MARKER_SOURCE_ID = "marker-source"
private const val MARKER_LAYER_ID = "marker-layer"
private const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private const val DEFAULT_ZOOM = 15.0
private const val DEFAULT_LAT = 48.8566
private const val DEFAULT_LON = 2.3522

@Composable
fun MapPickerRoute(
    onLocationPicked: (name: String, lat: Double, lon: Double) -> Unit,
    onBack: () -> Unit,
) {
    MapPickerScreen(
        onLocationPicked = onLocationPicked,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapPickerScreen(
    onLocationPicked: (name: String, lat: Double, lon: Double) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val markerSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val selectedPosition = remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }

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
            title = { Text("Pick Location") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        NominatimSearchBar(
            onLocationSelected = { lat, lon, _ ->
                selectedPosition.value = lat to lon
                val map = mapRef.value ?: return@NominatimSearchBar
                map.animateCamera(
                    CameraUpdateFactory.newLatLng(MapLatLng(lat, lon)),
                    500
                )
                val src = markerSource.value ?: return@NominatimSearchBar
                src.setGeoJson(buildMarkerGeoJson(lat, lon))
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

                                val src = GeoJsonSource(MARKER_SOURCE_ID, emptyGeoJson())
                                style.addSource(src)
                                style.addLayer(
                                    CircleLayer(MARKER_LAYER_ID, MARKER_SOURCE_ID)
                                        .withProperties(
                                            PropertyFactory.circleRadius(10f),
                                            PropertyFactory.circleColor("#FF5722"),
                                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                                            PropertyFactory.circleStrokeWidth(2f),
                                        ),
                                )
                                markerSource.value = src
                            }

                            map.addOnMapClickListener { latLng ->
                                selectedPosition.value = latLng.latitude to latLng.longitude
                                val src = markerSource.value ?: return@addOnMapClickListener true
                                src.setGeoJson(buildMarkerGeoJson(latLng.latitude, latLng.longitude))
                                true
                            }
                        }
                    }
                },
                update = { },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            val pos = selectedPosition.value
            if (pos != null) {
                Text(
                    "Latitude: ${String.format("%.4f", pos.first)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Longitude: ${String.format("%.4f", pos.second)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = { showNameDialog = true },
                enabled = selectedPosition.value != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }

    if (showNameDialog) {
        val pos = selectedPosition.value
        if (pos != null) {
            SaveLocationDialog(
                onDismiss = { showNameDialog = false },
                onSave = { name ->
                    onLocationPicked(name, pos.first, pos.second)
                    showNameDialog = false
                },
            )
        }
    }
}

@Composable
private fun SaveLocationDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Location") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim())
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
        },
    )
}

private fun emptyGeoJson(): String =
    """{"type":"FeatureCollection","features":[]}"""

private fun buildMarkerGeoJson(lat: Double, lon: Double): String =
    """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{}}]}"""
