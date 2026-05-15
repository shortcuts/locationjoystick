package com.locationjoystick.feature.map.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.locationjoystick.core.common.constants.MapConstants
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.ui.component.LjTopBar
import com.locationjoystick.core.ui.component.NominatimSearchBar
import com.locationjoystick.feature.map.api.MAP_ROUTE
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.geometry.LatLng as MapLatLng

private const val OSM_SOURCE_ID = "osm-source"
private const val OSM_LAYER_ID = "osm-layer"
private const val POSITION_SOURCE_ID = "position-source"
private const val POSITION_LAYER_ID = "position-layer"

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

@Composable
internal fun MapScreen(
    uiState: MapUiState,
    onOpenDrawer: () -> Unit,
    onAction: (MapAction) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val initialPosition = remember { uiState.currentPosition }

    val mapView =
        remember {
            MapLibre.getInstance(context)
            MapView(context)
        }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val positionSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val showSearch = remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
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

    LaunchedEffect(uiState.pendingCameraTarget) {
        val target = uiState.pendingCameraTarget ?: return@LaunchedEffect
        mapRef.value?.animateCamera(
            CameraUpdateFactory.newLatLng(MapLatLng(target.latitude, target.longitude)),
            500,
        )
        onAction(MapAction.CameraTargetConsumed)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LjTopBar(
                title = "Lj",
                onMenuClick = onOpenDrawer,
                actions = {
                    IconButton(onClick = { showSearch.value = !showSearch.value }) {
                        Icon(Icons.Default.Search, contentDescription = "Search location")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingActionButton(
                    onClick = { onAction(MapAction.OpenFavoritesPicker) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                        imageVector = LjIcons.Favorite,
                        contentDescription = "Open favorites",
                    )
                }
                MapFab(
                    isSpoofing = uiState.isSpoofing,
                    onStart = { onAction(MapAction.StartSpoofing) },
                    onStop = { onAction(MapAction.StopSpoofing) },
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
                                        OSM_SOURCE_ID,
                                        TileSet("2.2.0", MapConstants.OSM_TILE_URL).apply { maxZoom = 19f },
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
                                            PropertyFactory.circleColor(Color(0xFF1E88E5).toArgb()),
                                            PropertyFactory.circleStrokeColor(Color(0xFFFFFFFF).toArgb()),
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

            if (showSearch.value) {
                NominatimSearchBar(
                    onLocationSelected = { lat, lon, _ ->
                        mapRef.value?.animateCamera(
                            CameraUpdateFactory.newLatLng(MapLatLng(lat, lon)),
                            500,
                        )
                        showSearch.value = false
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

    if (uiState.showFavoritesSheet) {
        FavoritesPickerSheet(
            uiState = uiState,
            onAction = onAction,
        )
    }

    val pending = uiState.pendingTapPosition
    if (pending != null) {
        PendingTapSheet(
            position = pending,
            onAction = onAction,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesPickerSheet(
    uiState: MapUiState,
    onAction: (MapAction) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onAction(MapAction.CloseFavoritesPicker) },
    ) {
        val target = uiState.favoriteTarget
        if (target == null) {
            FavoritesPickerList(
                favorites = uiState.favorites,
                hasCurrentPosition = uiState.currentPosition != null,
                onSelect = { onAction(MapAction.SelectFavorite(it)) },
                onSaveCurrentLocation = { showSaveDialog = true },
            )
        } else {
            FavoriteTargetDetail(
                favorite = target,
                onSetLocation = { onAction(MapAction.SetLocationTo(target.position)) },
                onGoToLocation = { onAction(MapAction.WalkStraightTo(target.position)) },
                onBack = { onAction(MapAction.DeselectFavorite) },
            )
        }
    }

    if (showSaveDialog) {
        SaveCurrentLocationDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onAction(MapAction.SaveCurrentLocation(name))
                showSaveDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingTapSheet(
    position: com.locationjoystick.core.model.LatLng,
    onAction: (MapAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(MapAction.ClearPendingTap) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Move to this location?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onAction(MapAction.ConfirmTeleport(position)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Teleport here")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onAction(MapAction.LongPressTapToWalk(position))
                    onAction(MapAction.ClearPendingTap)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Walk here")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { onAction(MapAction.ClearPendingTap) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Do nothing")
            }
        }
    }
}

@Composable
private fun FavoritesPickerList(
    favorites: List<FavoriteLocation>,
    hasCurrentPosition: Boolean,
    onSelect: (FavoriteLocation) -> Unit,
    onSaveCurrentLocation: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Favorites", style = MaterialTheme.typography.headlineSmall)
            if (hasCurrentPosition) {
                IconButton(onClick = onSaveCurrentLocation) {
                    Icon(Icons.Default.Add, contentDescription = "Save current location")
                }
            }
        }

        if (favorites.isEmpty()) {
            Text(
                "No saved favorites yet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = favorites, key = { it.id }) { favorite ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp),
                                ).clickable { onSelect(favorite) }
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(favorite.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${String.format("%.4f", favorite.position.latitude)}, " +
                                    "${String.format("%.4f", favorite.position.longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteTargetDetail(
    favorite: FavoriteLocation,
    onSetLocation: () -> Unit,
    onGoToLocation: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Text(favorite.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${String.format("%.4f", favorite.position.latitude)}, " +
                "${String.format("%.4f", favorite.position.longitude)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        Button(
            onClick = onSetLocation,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
        ) {
            Text("Set Location")
        }
        Button(
            onClick = onGoToLocation,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text("Walk To Location")
        }
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("← Back")
        }
    }
}

@Composable
private fun SaveCurrentLocationDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save current location") },
        text = {
            androidx.compose.material3.OutlinedTextField(
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
                    if (name.isNotBlank()) onSave(name.trim())
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

private fun emptyGeoJson(): String = """{"type":"FeatureCollection","features":[]}"""

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
