package com.locationjoystick.feature.favorites.impl

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.LjMapIconButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.NominatimSearchBar
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.map.geojson.buildMarkerGeoJson
import com.locationjoystick.core.map.maplibre.addPickerLayers
import com.locationjoystick.core.map.maplibre.applyZoomBounds
import com.locationjoystick.core.map.projection.projection
import com.locationjoystick.core.map.ui.MapAttribution
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.overlay.OverlayService
import com.locationjoystick.feature.favorites.impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.HttpURLConnection
import java.net.URL
import org.maplibre.android.geometry.LatLng as MapLatLng

@Composable
fun MapPickerRoute(
    initialPosition: com.locationjoystick.core.model.LatLng? = null,
    onLocationPicked: (name: String, lat: Double, lon: Double) -> Unit,
    onBack: () -> Unit,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    tileSource: MapTileSource = MapTileSource.DEFAULT,
) {
    MapPickerScreen(
        initialPosition = initialPosition,
        onLocationPicked = onLocationPicked,
        onBack = onBack,
        recentSearches = recentSearches,
        onSearchCommitted = onSearchCommitted,
        bottomBar = bottomBar,
        tileSource = tileSource,
    )
}

@Preview(showBackground = true)
@Composable
private fun MapPickerScreenPreview() {
    MapPickerScreen(
        initialPosition = null,
        onLocationPicked = { _, _, _ -> },
        onBack = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapPickerScreen(
    initialPosition: com.locationjoystick.core.model.LatLng? = null,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
    onLocationPicked: (name: String, lat: Double, lon: Double) -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    tileSource: MapTileSource = MapTileSource.DEFAULT,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Selected/persisted positions are WGS-84; only the MapLibre drawing/tap boundary is projected.
    val proj = tileSource.projection
    val appliedTileSource = remember { mutableStateOf<MapTileSource?>(null) }

    fun LatLng.toMapLatLng(): MapLatLng = proj.toMap(this).let { MapLatLng(it.latitude, it.longitude) }

    fun markerGeoJson(
        lat: Double,
        lon: Double,
    ): String = proj.toMap(LatLng(lat, lon)).let { buildMarkerGeoJson(it.latitude, it.longitude) }

    val mapView =
        remember {
            MapLibre.getInstance(context)
            MapView(context)
        }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val markerSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val selectedPosition = remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var suggestedName by remember { mutableStateOf("") }
    val spoofToggle = rememberSpoofToggleState()

    val effectivePosition = { selectedPosition.value ?: initialPosition?.let { it.latitude to it.longitude } }

    val applyStyle: (MapLibreMap) -> Unit = { map ->
        appliedTileSource.value = tileSource
        map.applyZoomBounds(tileSource)
        map.setStyle(Style.Builder().fromUri(AppConstants.MapConstants.EMPTY_MAP_STYLE_URI)) { style ->
            val layers =
                style.addPickerLayers(
                    tileSource = tileSource,
                    currentPosGeoJson = effectivePosition()?.let { (lat, lon) -> markerGeoJson(lat, lon) },
                )
            markerSource.value = layers.markerSource
        }
    }

    LaunchedEffect(tileSource) {
        val map = mapRef.value ?: return@LaunchedEffect
        val previous = appliedTileSource.value ?: return@LaunchedEffect
        if (previous == tileSource) return@LaunchedEffect
        val prevProj = previous.projection
        val centerWgs = map.cameraPosition.target?.let { prevProj.fromMap(LatLng(it.latitude, it.longitude)) }
        applyStyle(map)
        if (centerWgs != null) map.moveCamera(CameraUpdateFactory.newLatLng(centerWgs.toMapLatLng()))
    }

    LaunchedEffect(showNameDialog) {
        context.sendBroadcast(
            Intent(
                if (showNameDialog) {
                    OverlayService.ACTION_OVERLAY_HIDE
                } else {
                    OverlayService.ACTION_OVERLAY_SHOW
                },
            ),
        )
        if (showNameDialog) {
            val pos = effectivePosition()
            if (pos != null) {
                suggestedName = ""
                withContext(Dispatchers.IO) {
                    try {
                        val url = URL("${AppConstants.NominatimConstants.REVERSE_URL}?lat=${pos.first}&lon=${pos.second}&format=json")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", "locationjoystick/1.0")
                        conn.connectTimeout = AppConstants.NominatimConstants.CONNECT_TIMEOUT_MS
                        conn.readTimeout = AppConstants.NominatimConstants.READ_TIMEOUT_MS
                        try {
                            val json = JSONObject(conn.inputStream.bufferedReader().readText())
                            val address = json.optJSONObject("address")
                            if (address != null) {
                                val city =
                                    address
                                        .optString("city")
                                        .ifEmpty { address.optString("town") }
                                        .ifEmpty { address.optString("village") }
                                        .ifEmpty { address.optString("state") }
                                val country = address.optString("country")
                                suggestedName = listOf(country, city).filter { it.isNotEmpty() }.joinToString(", ")
                            }
                        } finally {
                            conn.disconnect()
                        }
                    } catch (e: Exception) {
                        Log.e("MapPickerScreen", "Reverse geocode failed", e)
                    }
                }
            }
        }
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
        title = stringResource(R.string.map_picker_pick_location),
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
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
                    contentDescription = stringResource(R.string.map_picker_search_location_cd),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showSearchBar = !showSearchBar },
                )
                if (effectivePosition() != null) {
                    LjMapIconButton(
                        icon = LjIcons.Save,
                        contentDescription = stringResource(R.string.map_picker_save_location),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { showNameDialog = true },
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
                                        (
                                            initialPosition
                                                ?: tileSource.defaultCenter
                                        ).toMapLatLng(),
                                    ).zoom(AppConstants.MapConstants.DEFAULT_ZOOM)
                                    .build()

                            applyStyle(map)

                            map.addOnMapClickListener { latLng ->
                                val wgs = proj.fromMap(LatLng(latLng.latitude, latLng.longitude))
                                selectedPosition.value = wgs.latitude to wgs.longitude
                                val src = markerSource.value ?: return@addOnMapClickListener true
                                // Marker is drawn where the user tapped (already in map CRS).
                                src.setGeoJson(buildMarkerGeoJson(latLng.latitude, latLng.longitude))
                                true
                            }
                        }
                    }
                },
                update = { },
                modifier = Modifier.fillMaxSize(),
            )

            MapAttribution(
                tileSource = tileSource,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            )

            // Search bar — top overlay, shown when toggled
            if (showSearchBar) {
                NominatimSearchBar(
                    onLocationSelected = { lat, lon, _ ->
                        selectedPosition.value = lat to lon
                        showSearchBar = false
                        val map = mapRef.value ?: return@NominatimSearchBar
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon).toMapLatLng(), AppConstants.MapConstants.DEFAULT_ZOOM),
                            500,
                        )
                        val src = markerSource.value ?: return@NominatimSearchBar
                        src.setGeoJson(markerGeoJson(lat, lon))
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

    if (showNameDialog) {
        val pos = effectivePosition()
        if (pos != null) {
            SaveLocationDialog(
                initialName = suggestedName,
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
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_picker_save_location)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.common_name)) },
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
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
