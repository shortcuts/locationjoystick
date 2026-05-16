package com.locationjoystick.feature.widget.impl

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationjoystick.core.common.constants.MapConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.component.NominatimSearchBar
import com.locationjoystick.core.model.LatLng
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

private const val MAP_FLOATING_VIEW_OSM_SOURCE = "panel-osm-source"
private const val MAP_FLOATING_VIEW_OSM_LAYER = "panel-osm-layer"
private const val MAP_FLOATING_VIEW_POS_SOURCE = "panel-pos-source"
private const val MAP_FLOATING_VIEW_POS_LAYER = "panel-pos-layer"
private const val MAP_FLOATING_VIEW_TRACED_SOURCE = "panel-traced-source"
private const val MAP_FLOATING_VIEW_TRACED_LAYER = "panel-traced-layer"
private const val MAP_FLOATING_VIEW_REMAINING_SOURCE = "panel-remaining-source"
private const val MAP_FLOATING_VIEW_REMAINING_LAYER = "panel-remaining-layer"

@Composable
internal fun MapFloatingView(
    locationRepository: LocationRepository,
    favoriteRepository: FavoriteRepository,
    onTeleport: (LatLng) -> Unit,
    onWalkTo: (LatLng) -> Unit,
    onStopRouteAndTeleport: (LatLng) -> Unit,
    onStopRouteAndWalkTo: (LatLng) -> Unit,
    onFinishRouteAndWalkTo: (LatLng) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
) {
    val currentPosition by locationRepository.currentPosition.collectAsState()
    val walkTarget by locationRepository.walkTarget.collectAsState()
    val mockMode by locationRepository.currentMode.collectAsState()
    val isRouteReplay = mockMode == com.locationjoystick.core.model.MockMode.ROUTE_REPLAY
    val favoritesFlow = remember { favoriteRepository.getFavorites() }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())

    val initialPosition = remember { locationRepository.currentPosition.value }

    var pendingTap by remember { mutableStateOf<LatLng?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showFavoritesPicker by remember { mutableStateOf(false) }
    val isFollowingCamera = remember { mutableStateOf(true) }

    val mapView =
        remember {
            MapLibre.getInstance(context)
            MapView(context)
        }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val positionSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val tracedSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val remainingSource = remember { mutableStateOf<GeoJsonSource?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        mapView.onStart()
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Dark backdrop (visual only — X button is the close mechanism, map touches must pass through)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(LjBg, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
    ) {
        AndroidView(
            factory = { _ ->
                mapView.apply {
                    getMapAsync { map ->
                        mapRef.value = map
                        map.uiSettings.isAttributionEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isScrollGesturesEnabled = true
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
                                    MAP_FLOATING_VIEW_OSM_SOURCE,
                                    TileSet("2.2.0", MapConstants.OSM_TILE_URL).apply { maxZoom = 19f },
                                    256,
                                ),
                            )
                            style.addLayer(RasterLayer(MAP_FLOATING_VIEW_OSM_LAYER, MAP_FLOATING_VIEW_OSM_SOURCE))

                            val tracedSrc =
                                GeoJsonSource(MAP_FLOATING_VIEW_TRACED_SOURCE, """{"type":"FeatureCollection","features":[]}""")
                            style.addSource(tracedSrc)
                            style.addLayer(
                                LineLayer(MAP_FLOATING_VIEW_TRACED_LAYER, MAP_FLOATING_VIEW_TRACED_SOURCE)
                                    .withProperties(
                                        PropertyFactory.lineColor(Color(0xFF1E88E5).toArgb()),
                                        PropertyFactory.lineWidth(3f),
                                    ),
                            )
                            tracedSource.value = tracedSrc

                            val remainingSrc =
                                GeoJsonSource(MAP_FLOATING_VIEW_REMAINING_SOURCE, """{"type":"FeatureCollection","features":[]}""")
                            style.addSource(remainingSrc)
                            style.addLayer(
                                LineLayer(MAP_FLOATING_VIEW_REMAINING_LAYER, MAP_FLOATING_VIEW_REMAINING_SOURCE)
                                    .withProperties(
                                        PropertyFactory.lineColor(Color(0xFF1E88E5).copy(alpha = 0.5f).toArgb()),
                                        PropertyFactory.lineWidth(3f),
                                    ),
                            )
                            remainingSource.value = remainingSrc

                            val src =
                                GeoJsonSource(MAP_FLOATING_VIEW_POS_SOURCE, """{"type":"FeatureCollection","features":[]}""")
                            style.addSource(src)
                            style.addLayer(
                                CircleLayer(MAP_FLOATING_VIEW_POS_LAYER, MAP_FLOATING_VIEW_POS_SOURCE)
                                    .withProperties(
                                        PropertyFactory.circleRadius(10f),
                                        PropertyFactory.circleColor(Color(0xFF1E88E5).toArgb()),
                                        PropertyFactory.circleStrokeColor(Color(0xFFFFFFFF).toArgb()),
                                        PropertyFactory.circleStrokeWidth(2f),
                                    ),
                            )
                            positionSource.value = src
                        }

                        map.addOnMapClickListener { latLng ->
                            pendingTap = LatLng(latLng.latitude, latLng.longitude)
                            true
                        }

                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                isFollowingCamera.value = false
                            }
                        }
                    }
                }
            },
            update = { _ ->
                val src = positionSource.value ?: return@AndroidView
                val tracedSrc = tracedSource.value ?: return@AndroidView
                val remainingSrc = remainingSource.value ?: return@AndroidView
                val position = currentPosition

                src.setGeoJson(
                    if (position != null) {
                        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${position.longitude},${position.latitude}]},"properties":{}}]}"""
                    } else {
                        """{"type":"FeatureCollection","features":[]}"""
                    },
                )

                val target = walkTarget
                if (position != null && target != null) {
                    val tracedGeoJson =
                        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[${position.longitude},${position.latitude}],[${target.longitude},${target.latitude}]]},"properties":{}}]}"""
                    tracedSrc.setGeoJson(tracedGeoJson)
                    remainingSrc.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
                } else {
                    tracedSrc.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
                    remainingSrc.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
                }

                if (isFollowingCamera.value && position != null) {
                    mapRef.value?.animateCamera(
                        CameraUpdateFactory.newLatLng(MapLatLng(position.latitude, position.longitude)),
                        500,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (showSearch) {
            NominatimSearchBar(
                onLocationSelected = { lat, lon, _ ->
                    mapRef.value?.animateCamera(
                        CameraUpdateFactory.newLatLng(MapLatLng(lat, lon)),
                        500,
                    )
                    showSearch = false
                },
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp),
            )
        }

        // Close button — top-right corner
        IconButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(LjBg, CircleShape),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = LjText)
        }

        // FAB column — bottom-right, mirrors main map layout
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isFollowingCamera.value) {
                FloatingActionButton(
                    onClick = {
                        isFollowingCamera.value = true
                        val pos = locationRepository.currentPosition.value
                        if (pos != null) {
                            mapRef.value?.animateCamera(
                                CameraUpdateFactory.newLatLng(MapLatLng(pos.latitude, pos.longitude)),
                                500,
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Re-center on location")
                }
            }
            FloatingActionButton(
                onClick = { showFavoritesPicker = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = "Open favorites")
            }
            FloatingActionButton(
                onClick = { showSearch = !showSearch },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Search location")
            }
        }

        // Tap confirmation panel (appears at bottom of map)
        val tap = pendingTap
        if (tap != null) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(LjBg)
                        .clickable { }
                        .padding(16.dp),
            ) {
                if (isRouteReplay) {
                    Text("Route in progress", style = MaterialTheme.typography.titleMedium, color = LjText)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onStopRouteAndTeleport(tap)
                            pendingTap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Stop route and teleport") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onStopRouteAndWalkTo(tap)
                            pendingTap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Stop route and walk here") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onFinishRouteAndWalkTo(tap)
                            pendingTap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Finish route and walk here") }
                } else {
                    Text("Move to this location?", style = MaterialTheme.typography.titleMedium, color = LjText)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onTeleport(tap)
                            pendingTap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Teleport here") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onWalkTo(tap)
                            pendingTap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Walk here") }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { pendingTap = null },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Do nothing") }
            }
        }

        // Favorites picker overlay (slides over the map)
        if (showFavoritesPicker) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(LjBg)
                        .clickable { }
                        .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Favorites", style = MaterialTheme.typography.titleLarge, color = LjText)
                    IconButton(onClick = { showFavoritesPicker = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close favorites", tint = LjText)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (favorites.isEmpty()) {
                    Text(
                        "No favorites saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LjText.copy(alpha = 0.6f),
                    )
                } else {
                    LazyColumn {
                        items(favorites, key = { it.id }) { fav ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    fav.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = LjText,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(onClick = {
                                    onTeleport(fav.position)
                                    showFavoritesPicker = false
                                }) { Text("Teleport") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    onWalkTo(fav.position)
                                    showFavoritesPicker = false
                                }) { Text("Walk") }
                            }
                        }
                    }
                }
            }
        }
    }
}
