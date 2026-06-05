package com.locationjoystick.feature.map.impl

import android.content.Intent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.NominatimSearchBar
import com.locationjoystick.core.map.geojson.buildLineGeoJson
import com.locationjoystick.core.map.geojson.buildMarkerGeoJson
import com.locationjoystick.core.map.geojson.buildPointsGeoJson
import com.locationjoystick.core.map.geojson.buildPositionGeoJson
import com.locationjoystick.core.map.geojson.buildRouteTraceGeoJson
import com.locationjoystick.core.map.geojson.emptyGeoJson
import com.locationjoystick.core.map.maplibre.addEphemeralRouteLayers
import com.locationjoystick.core.map.maplibre.addLocationLayers
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.feature.map.api.MAP_ROUTE
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
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
    onNavigateToRoutes: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    composable(
        route = MAP_ROUTE,
        enterTransition = { fadeInScale() },
        exitTransition = { fadeOutScale() },
        popEnterTransition = { fadeInScale() },
        popExitTransition = { fadeOutScale() },
    ) {
        MapRoute(onOpenDrawer = onOpenDrawer, onNavigateToRoutes = onNavigateToRoutes, bottomBar = bottomBar)
    }
}

@Composable
fun MapRoute(
    onOpenDrawer: () -> Unit,
    onNavigateToRoutes: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.completionMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    MapScreen(
        uiState = uiState,
        recentSearches = recentSearches,
        onOpenDrawer = onOpenDrawer,
        onAction = viewModel::onAction,
        onSearchCommitted = viewModel::addRecentSearch,
        onNavigateToRoutes = onNavigateToRoutes,
        bottomBar = bottomBar,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun MapScreen(
    uiState: MapUiState,
    onOpenDrawer: () -> Unit,
    onAction: (MapAction) -> Unit,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
    onNavigateToRoutes: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
    val searchMarkerSource = remember { mutableStateOf<GeoJsonSource?>(null) }
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
        // Use closer zoom for favorite teleports (street-level), default zoom for other cases
        val zoom = if (uiState.favoriteTarget != null) 18.0 else AppConstants.MapConstants.DEFAULT_ZOOM
        mapRef.value?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(MapLatLng(target.latitude, target.longitude), zoom),
            500,
        )
        onAction(MapAction.CameraTargetConsumed)
    }

    LjScaffold(
        title = "Map",
        onNavigationClick = onOpenDrawer,
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            MapFabColumn(
                uiState = uiState,
                isFollowingCamera = isFollowingCamera.value,
                onAction = onAction,
                onToggleSearch = { showSearch.value = !showSearch.value },
            )
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
                                val layers = style.addLocationLayers(includeSearchMarker = true)
                                positionSource.value = layers.positionSource
                                tracedSource.value = layers.tracedSource
                                remainingSource.value = layers.remainingSource
                                endpointsSource.value = layers.endpointsSource
                                searchMarkerSource.value = layers.searchMarkerSource
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

                    val traceWaypoints =
                        uiState.routeTrace
                            ?: uiState.ephemeralWaypoints.takeIf { it.size >= 2 }
                    if (traceWaypoints != null && position != null) {
                        val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(traceWaypoints, position)
                        tracedSrc.setGeoJson(tracedGeoJson)
                        remainingSrc.setGeoJson(remainingGeoJson)
                        endpointsSrc.setGeoJson(buildPointsGeoJson(traceWaypoints))
                    } else if (uiState.walkStart != null && uiState.walkTarget != null && position != null) {
                        val walkPoints = listOfNotNull(uiState.walkStart, uiState.walkTarget)
                        val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(walkPoints, position)
                        tracedSrc.setGeoJson(tracedGeoJson)
                        remainingSrc.setGeoJson(remainingGeoJson)
                        endpointsSrc.setGeoJson(buildPointsGeoJson(walkPoints))
                    } else {
                        tracedSrc.setGeoJson(emptyGeoJson())
                        remainingSrc.setGeoJson(emptyGeoJson())
                        endpointsSrc.setGeoJson(emptyGeoJson())
                    }

                    // Ephemeral route preview polyline — show roaming preview when the sheet is open or minimized
                    val displayWaypoints =
                        if (uiState.showRoamingSheet || uiState.isRoamingSheetMinimized) {
                            uiState.roamingPreviewWaypoints ?: emptyList()
                        } else {
                            uiState.ephemeralWaypoints
                        }
                    if (displayWaypoints.size >= 2) {
                        ephemeralRouteSrc.setGeoJson(buildLineGeoJson(displayWaypoints))
                        ephemeralEndpointsSrc.setGeoJson(buildPointsGeoJson(displayWaypoints))
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
                        val position =
                            com.locationjoystick.core.model
                                .LatLng(latitude = lat, longitude = lon)
                        mapRef.value?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(MapLatLng(lat, lon), AppConstants.MapConstants.DEFAULT_ZOOM),
                            500,
                        )
                        searchMarkerSource.value?.setGeoJson(buildMarkerGeoJson(lat, lon))
                        showSearch.value = false
                        onAction(MapAction.TapToTeleport(position))
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

    if (uiState.showRoutesSheet) {
        RoutesPickerSheet(
            uiState = uiState,
            onAction = onAction,
        )
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
            isEphemeralReplay = uiState.ephemeralWaypoints.isNotEmpty(),
            onAction = onAction,
            onShare = {
                val url = AppConstants.AppInfo.buildDeepLink(pending.latitude, pending.longitude)
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                context.startActivity(Intent.createChooser(shareIntent, null))
            },
        )
    }

    val roamingDraft = uiState.roamingDraft
    if (uiState.showRoamingSheet && !uiState.isRoamingSheetMinimized && roamingDraft != null) {
        RoamingSheet(
            draft = roamingDraft,
            hasCurrentPosition = uiState.currentPosition != null,
            isSpoofingActive = uiState.isSpoofing,
            speedUnit = uiState.speedUnit,
            hasPreview = uiState.roamingPreviewWaypoints != null,
            onAction = onAction,
            onGeneratePreview = { onAction(MapAction.GenerateRoamingPreview) },
            onMinimize = { onAction(MapAction.MinimizeRoamingSheet) },
            onDismiss = { onAction(MapAction.DismissRoamingSheet) },
        )
    }
}
