package com.locationjoystick.feature.map.impl

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.FavoritesList
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.NominatimSearchBar
import com.locationjoystick.core.map.geojson.buildLineGeoJson
import com.locationjoystick.core.map.geojson.buildPointsGeoJson
import com.locationjoystick.core.map.geojson.buildPositionGeoJson
import com.locationjoystick.core.map.geojson.buildRouteTraceGeoJson
import com.locationjoystick.core.map.geojson.emptyGeoJson
import com.locationjoystick.core.map.maplibre.MapLibreLayerIds
import com.locationjoystick.core.map.maplibre.MapLibreSourceIds
import com.locationjoystick.core.map.maplibre.addEphemeralRouteLayers
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.feature.map.api.MAP_ROUTE
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

private fun fadeInScale(): EnterTransition =
    fadeIn(
        animationSpec =
            spring(
                dampingRatio = AppConstants.AnimationConstants.SPRING_DAMPING_RATIO,
                stiffness = AppConstants.AnimationConstants.SPRING_STIFFNESS,
            ),
    ) +
        scaleIn(
            initialScale = 0.95f,
            animationSpec =
                spring(
                    dampingRatio = AppConstants.AnimationConstants.SPRING_DAMPING_RATIO,
                    stiffness = AppConstants.AnimationConstants.SPRING_STIFFNESS,
                ),
        )

private fun fadeOutScale(): ExitTransition =
    fadeOut(
        animationSpec =
            spring(
                dampingRatio = AppConstants.AnimationConstants.SPRING_DAMPING_RATIO,
                stiffness = AppConstants.AnimationConstants.SPRING_STIFFNESS,
            ),
    ) +
        scaleOut(
            targetScale = 0.95f,
            animationSpec =
                spring(
                    dampingRatio = AppConstants.AnimationConstants.SPRING_DAMPING_RATIO,
                    stiffness = AppConstants.AnimationConstants.SPRING_STIFFNESS,
                ),
        )

fun NavGraphBuilder.mapScreen(
    onOpenDrawer: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    composable(
        route = MAP_ROUTE,
        enterTransition = { fadeInScale() },
        exitTransition = { fadeOutScale() },
        popEnterTransition = { fadeInScale() },
        popExitTransition = { fadeOutScale() },
    ) {
        MapRoute(onOpenDrawer = onOpenDrawer, bottomBar = bottomBar)
    }
}

@Composable
fun MapRoute(
    onOpenDrawer: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    MapScreen(
        uiState = uiState,
        recentSearches = recentSearches,
        onOpenDrawer = onOpenDrawer,
        onAction = viewModel::onAction,
        onSearchCommitted = viewModel::addRecentSearch,
        bottomBar = bottomBar,
    )
}

@Composable
internal fun MapScreen(
    uiState: MapUiState,
    onOpenDrawer: () -> Unit,
    onAction: (MapAction) -> Unit,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
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
    val tracedSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val remainingSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val endpointsSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val ephemeralRouteSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val ephemeralEndpointsSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val showSearch = remember { mutableStateOf(false) }
    val isFollowingCamera = remember { mutableStateOf(true) }

    LaunchedEffect(uiState.isUserPanning) {
        if (!uiState.isUserPanning) isFollowingCamera.value = true
    }

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

    LaunchedEffect(uiState.pendingCameraTarget) {
        val target = uiState.pendingCameraTarget ?: return@LaunchedEffect
        mapRef.value?.animateCamera(
            CameraUpdateFactory.newLatLng(MapLatLng(target.latitude, target.longitude)),
            500,
        )
        onAction(MapAction.CameraTargetConsumed)
    }

    LjScaffold(
        title = "Lj",
        onNavigationClick = onOpenDrawer,
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isFollowingCamera.value) {
                    FloatingActionButton(
                        onClick = { onAction(MapAction.RecenterCamera) },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Icon(
                            imageVector = LjIcons.MyLocation,
                            contentDescription = "Re-center on location",
                        )
                    }
                }
                if (uiState.walkTarget != null) {
                    FloatingActionButton(
                        onClick = { onAction(MapAction.StopWalk) },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ) {
                        Icon(LjIcons.Stop, contentDescription = "Stop walk")
                    }
                    FloatingActionButton(
                        onClick = {
                            if (uiState.isWalkPaused) {
                                onAction(MapAction.ResumeWalk)
                            } else {
                                onAction(MapAction.PauseWalk)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Icon(
                            imageVector = if (uiState.isWalkPaused) LjIcons.PlayArrow else LjIcons.Pause,
                            contentDescription = if (uiState.isWalkPaused) "Resume walk" else "Pause walk",
                        )
                    }
                }
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
                FloatingActionButton(
                    onClick = {
                        if (uiState.isRoaming) {
                            onAction(MapAction.StopRoaming)
                        } else {
                            onAction(MapAction.OpenRoamingSheet)
                        }
                    },
                    containerColor =
                        if (uiState.isRoaming) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                    contentColor =
                        if (uiState.isRoaming) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                ) {
                    Icon(
                        imageVector = if (uiState.isRoaming) LjIcons.Stop else LjIcons.Explore,
                        contentDescription = if (uiState.isRoaming) "Stop roaming" else "Start roaming",
                    )
                }
                FloatingActionButton(
                    onClick = { showSearch.value = !showSearch.value },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search location",
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
                                            MapLatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON)
                                        },
                                    ).zoom(AppConstants.MapConstants.DEFAULT_ZOOM)
                                    .build()

                            map.setStyle(Style.Builder().fromUri("asset://empty.json")) { style ->
                                style.addSource(
                                    RasterSource(
                                        MapLibreSourceIds.OSM,
                                        TileSet(AppConstants.MapConstants.TILESET_VERSION, AppConstants.MapConstants.OSM_TILE_URL).apply {
                                            maxZoom =
                                                AppConstants.MapConstants.OSM_MAX_ZOOM
                                        },
                                        256,
                                    ),
                                )
                                style.addLayer(RasterLayer(MapLibreLayerIds.OSM, MapLibreSourceIds.OSM))

                                val src = GeoJsonSource(MapLibreSourceIds.POSITION, emptyGeoJson())
                                style.addSource(src)
                                style.addLayer(
                                    CircleLayer(MapLibreLayerIds.POSITION, MapLibreSourceIds.POSITION)
                                        .withProperties(
                                            PropertyFactory.circleRadius(10f),
                                            PropertyFactory.circleColor(
                                                Color(AppConstants.MapColorConstants.ENDPOINT_CIRCLE_COLOR).toArgb(),
                                            ),
                                            PropertyFactory.circleStrokeColor(
                                                Color(AppConstants.MapColorConstants.ENDPOINT_STROKE_COLOR).toArgb(),
                                            ),
                                            PropertyFactory.circleStrokeWidth(2f),
                                        ),
                                )
                                positionSource.value = src

                                val tracedSrc = GeoJsonSource(MapLibreSourceIds.TRACE_TRACED, emptyGeoJson())
                                style.addSource(tracedSrc)
                                style.addLayer(
                                    LineLayer(MapLibreLayerIds.TRACE_TRACED, MapLibreSourceIds.TRACE_TRACED)
                                        .withProperties(
                                            PropertyFactory.lineColor(Color(AppConstants.MapColorConstants.ROUTE_LINE_COLOR).toArgb()),
                                            PropertyFactory.lineWidth(4f),
                                            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                                        ),
                                )
                                tracedSource.value = tracedSrc

                                val remainingSrc = GeoJsonSource(MapLibreSourceIds.TRACE_REMAINING, emptyGeoJson())
                                style.addSource(remainingSrc)
                                style.addLayer(
                                    LineLayer(MapLibreLayerIds.TRACE_REMAINING, MapLibreSourceIds.TRACE_REMAINING)
                                        .withProperties(
                                            PropertyFactory.lineColor(Color(AppConstants.MapColorConstants.ROUTE_LINE_COLOR).toArgb()),
                                            PropertyFactory.lineWidth(4f),
                                        ),
                                )
                                remainingSource.value = remainingSrc

                                val endpointsSrc = GeoJsonSource(MapLibreSourceIds.ENDPOINTS, emptyGeoJson())
                                style.addSource(endpointsSrc)
                                style.addLayer(
                                    CircleLayer(MapLibreLayerIds.ENDPOINTS, MapLibreSourceIds.ENDPOINTS)
                                        .withProperties(
                                            PropertyFactory.circleRadius(8f),
                                            PropertyFactory.circleColor(
                                                Color(AppConstants.MapColorConstants.ENDPOINT_CIRCLE_COLOR).toArgb(),
                                            ),
                                            PropertyFactory.circleStrokeColor(
                                                Color(AppConstants.MapColorConstants.ENDPOINT_STROKE_COLOR).toArgb(),
                                            ),
                                            PropertyFactory.circleStrokeWidth(2f),
                                        ),
                                )
                                endpointsSource.value = endpointsSrc

                                val ephemeralSrcs = style.addEphemeralRouteLayers()
                                ephemeralRouteSource.value = ephemeralSrcs.routeSource
                                ephemeralEndpointsSource.value = ephemeralSrcs.endpointsSource
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
                                    isFollowingCamera.value = false
                                    onAction(MapAction.UserStartedPanning)
                                }
                            }
                        }
                    }
                },
                update = { _ ->
                    val src = positionSource.value ?: return@AndroidView
                    val map = mapRef.value ?: return@AndroidView
                    val tracedSrc = tracedSource.value ?: return@AndroidView
                    val remainingSrc = remainingSource.value ?: return@AndroidView
                    val endpointsSrc = endpointsSource.value ?: return@AndroidView
                    val ephemeralRouteSrc = ephemeralRouteSource.value ?: return@AndroidView
                    val ephemeralEndpointsSrc = ephemeralEndpointsSource.value ?: return@AndroidView
                    val position = uiState.currentPosition

                    src.setGeoJson(buildPositionGeoJson(position))

                    if (uiState.routeTrace != null && position != null) {
                        val (tracedGeoJson, remainingGeoJson) =
                            buildRouteTraceGeoJson(
                                uiState.routeTrace,
                                position,
                            )
                        tracedSrc.setGeoJson(tracedGeoJson)
                        remainingSrc.setGeoJson(remainingGeoJson)
                        endpointsSrc.setGeoJson(buildPointsGeoJson(uiState.routeTrace))
                    } else if (uiState.walkStart != null && uiState.walkTarget != null && position != null) {
                        val walkPoints = listOfNotNull(uiState.walkStart, uiState.walkTarget)
                        val (tracedGeoJson, remainingGeoJson) =
                            buildRouteTraceGeoJson(
                                walkPoints,
                                position,
                            )
                        tracedSrc.setGeoJson(tracedGeoJson)
                        remainingSrc.setGeoJson(remainingGeoJson)
                        endpointsSrc.setGeoJson(buildPointsGeoJson(walkPoints))
                    } else {
                        tracedSrc.setGeoJson(emptyGeoJson())
                        remainingSrc.setGeoJson(emptyGeoJson())
                        endpointsSrc.setGeoJson(emptyGeoJson())
                    }

                    // Ephemeral route preview polyline
                    val ephemeralPts = uiState.ephemeralWaypoints
                    if (ephemeralPts.size >= 2) {
                        ephemeralRouteSrc.setGeoJson(buildLineGeoJson(ephemeralPts))
                        ephemeralEndpointsSrc.setGeoJson(buildPointsGeoJson(ephemeralPts))
                    } else {
                        ephemeralRouteSrc.setGeoJson(emptyGeoJson())
                        ephemeralEndpointsSrc.setGeoJson(emptyGeoJson())
                    }

                    if (isFollowingCamera.value && position != null) {
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
            isRouteReplay = uiState.isRouteReplay,
            isWalkActive = uiState.isWalkActive,
            cooldownState = uiState.cooldownState,
            onAction = onAction,
        )
    }

    val roamingDraft = uiState.roamingDraft
    if (uiState.showRoamingSheet && roamingDraft != null) {
        RoamingSheet(
            draft = roamingDraft,
            hasCurrentPosition = uiState.currentPosition != null,
            speedUnit = uiState.speedUnit,
            onAction = onAction,
            onDismiss = { onAction(MapAction.DismissRoamingSheet) },
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
            FavoritesList(
                title = "Favorites",
                favorites = uiState.favorites,
                onSelect = { onAction(MapAction.SelectFavorite(it)) },
                onSaveCurrentLocation =
                    if (uiState.currentPosition != null) {
                        { showSaveDialog = true }
                    } else {
                        null
                    },
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
    isRouteReplay: Boolean,
    isWalkActive: Boolean,
    cooldownState: CooldownState,
    onAction: (MapAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onAction(MapAction.ClearPendingTap) },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (isRouteReplay) {
                Text("Route in progress", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onAction(MapAction.StopRouteAndTeleport(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop route and teleport")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onAction(MapAction.StopRouteAndWalkTo(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Stop route and walk here")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onAction(MapAction.FinishRouteAndWalkTo(position)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Finish route and walk here")
                }
            } else {
                Text("Move to this location?", style = MaterialTheme.typography.titleMedium)
                if (cooldownState is CooldownState.Cooling) {
                    Spacer(Modifier.height(12.dp))
                    val distKm = cooldownState.distanceMeters / 1000.0
                    val distLabel = if (distKm >= 1.0) "%.1f km".format(distKm) else "%.0f m".format(cooldownState.distanceMeters)
                    val remaining = cooldownState.remainingSeconds
                    val hours = remaining / AppConstants.TimeConstants.SECONDS_PER_HOUR
                    val minutes = (remaining % AppConstants.TimeConstants.SECONDS_PER_HOUR) / AppConstants.TimeConstants.SECONDS_PER_MINUTE
                    val seconds = remaining % AppConstants.TimeConstants.SECONDS_PER_MINUTE
                    val timeLabel =
                        when {
                            hours > 0 -> "%dh %dm".format(hours, minutes)
                            minutes > 0 -> "%dm %ds".format(minutes, seconds)
                            else -> "%ds".format(seconds)
                        }
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Suggested wait: $timeLabel · $distLabel teleport",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
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
                if (isWalkActive) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onAction(MapAction.AddEphemeralWaypoint(position)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add next point")
                    }
                }
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
        containerColor = if (isSpoofing) MaterialTheme.colorScheme.error else Color(AppConstants.MapColorConstants.ACTIVE_BUTTON_COLOR),
        contentColor = if (isSpoofing) MaterialTheme.colorScheme.onError else Color.White,
    ) {
        Icon(
            imageVector = if (isSpoofing) LjIcons.Stop else LjIcons.PlayArrow,
            contentDescription = if (isSpoofing) "Stop location simulation" else "Start location simulation",
        )
    }
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
