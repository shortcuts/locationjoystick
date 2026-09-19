package com.locationjoystick.feature.map.impl

import android.content.Intent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.NominatimSearchBar
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.map.geojson.buildCirclePolygonGeoJson
import com.locationjoystick.core.map.geojson.buildLineGeoJson
import com.locationjoystick.core.map.geojson.buildMarkerGeoJson
import com.locationjoystick.core.map.geojson.buildPointsGeoJson
import com.locationjoystick.core.map.geojson.buildPositionGeoJson
import com.locationjoystick.core.map.geojson.buildRouteTraceGeoJson
import com.locationjoystick.core.map.geojson.emptyGeoJson
import com.locationjoystick.core.map.maplibre.addEphemeralRouteLayers
import com.locationjoystick.core.map.maplibre.addLocationLayers
import com.locationjoystick.core.map.maplibre.applyZoomBounds
import com.locationjoystick.core.map.projection.projection
import com.locationjoystick.core.map.ui.MapAttribution
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.feature.map.api.MAP_ROUTE
import com.locationjoystick.feature.map.impl.R
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
    LaunchedEffect(Unit) {
        viewModel.routingErrors.collect { msg ->
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
    val pendingTapMarkerSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val jitterRadiusSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val showSearch = remember { mutableStateOf(false) }
    val isFollowingCamera = remember { mutableStateOf(true) }
    val spoofToggle = rememberSpoofToggleState()

    // Base-map provider + the WGS-84 <-> map-datum projection that goes with it. Everything drawn
    // on or read back from the MapLibre map passes through `proj`; the app's own state stays WGS-84.
    val tileSource = uiState.mapTileSource
    val proj = tileSource.projection
    val appliedTileSource = remember { mutableStateOf<MapTileSource?>(null) }

    fun LatLng.toMapLatLng(): MapLatLng = proj.toMap(this).let { MapLatLng(it.latitude, it.longitude) }

    val applyStyle: (MapLibreMap) -> Unit = { map ->
        appliedTileSource.value = tileSource
        map.applyZoomBounds(tileSource)
        map.setStyle(Style.Builder().fromUri(AppConstants.MapConstants.EMPTY_MAP_STYLE_URI)) { style ->
            val layers = style.addLocationLayers(tileSource = tileSource, includeSearchMarker = true)
            positionSource.value = layers.positionSource
            tracedSource.value = layers.tracedSource
            remainingSource.value = layers.remainingSource
            endpointsSource.value = layers.endpointsSource
            searchMarkerSource.value = layers.searchMarkerSource
            pendingTapMarkerSource.value = layers.pendingTapSource
            jitterRadiusSource.value = layers.jitterRadiusSource
            val ephemeralSrcs = style.addEphemeralRouteLayers()
            ephemeralRouteSource.value = ephemeralSrcs.routeSource
            ephemeralEndpointsSource.value = ephemeralSrcs.endpointsSource
        }
    }

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
            CameraUpdateFactory.newLatLngZoom(target.toMapLatLng(), zoom),
            500,
        )
        onAction(MapAction.CameraTargetConsumed)
    }

    // Re-create the style when the user switches base map in Settings. The camera is re-anchored
    // on the same WGS-84 point so the view does not visibly jump by the datum offset.
    LaunchedEffect(tileSource) {
        val map = mapRef.value ?: return@LaunchedEffect
        val previous = appliedTileSource.value ?: return@LaunchedEffect
        if (previous == tileSource) return@LaunchedEffect
        val prevProj = previous.projection
        val centerWgs = map.cameraPosition.target?.let { prevProj.fromMap(LatLng(it.latitude, it.longitude)) }
        applyStyle(map)
        if (centerWgs != null) map.moveCamera(CameraUpdateFactory.newLatLng(centerWgs.toMapLatLng()))
    }

    LjScaffold(
        title = stringResource(R.string.map_screen_title),
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
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
                                        (
                                            initialPosition
                                                ?: tileSource.defaultCenter
                                        ).toMapLatLng(),
                                    ).zoom(AppConstants.MapConstants.DEFAULT_ZOOM)
                                    .build()

                            applyStyle(map)

                            map.addOnMapClickListener { latLng ->
                                onAction(MapAction.TapToTeleport(proj.fromMap(LatLng(latLng.latitude, latLng.longitude))))
                                true
                            }

                            map.addOnMapLongClickListener { latLng ->
                                onAction(MapAction.LongPressTapToWalk(proj.fromMap(LatLng(latLng.latitude, latLng.longitude))))
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
                    val mapPosition = position?.let(proj::toMap)

                    src.setGeoJson(buildPositionGeoJson(mapPosition))

                    jitterRadiusSource.value?.setGeoJson(
                        if (uiState.showJitterRadiusOverlay && mapPosition != null) {
                            buildCirclePolygonGeoJson(mapPosition, uiState.jitterRadiusMeters)
                        } else {
                            emptyGeoJson()
                        },
                    )

                    pendingTapMarkerSource.value?.setGeoJson(buildPositionGeoJson(uiState.pendingTapPosition?.let(proj::toMap)))

                    val traceWaypoints =
                        uiState.routeTrace
                            ?: uiState.ephemeralWaypoints.takeIf { it.size >= 2 }
                    if (traceWaypoints != null && mapPosition != null) {
                        val mapWaypoints = proj.toMap(traceWaypoints)
                        val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(mapWaypoints, mapPosition)
                        tracedSrc.setGeoJson(tracedGeoJson)
                        remainingSrc.setGeoJson(remainingGeoJson)
                        endpointsSrc.setGeoJson(buildPointsGeoJson(mapWaypoints))
                    } else if (uiState.walkStart != null && uiState.walkTarget != null && mapPosition != null) {
                        val walkPoints = proj.toMap(listOfNotNull(uiState.walkStart, uiState.walkTarget))
                        val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(walkPoints, mapPosition)
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
                        val mapDisplayWaypoints = proj.toMap(displayWaypoints)
                        ephemeralRouteSrc.setGeoJson(buildLineGeoJson(mapDisplayWaypoints))
                        ephemeralEndpointsSrc.setGeoJson(buildPointsGeoJson(mapDisplayWaypoints))
                    } else {
                        ephemeralRouteSrc.setGeoJson(emptyGeoJson())
                        ephemeralEndpointsSrc.setGeoJson(emptyGeoJson())
                    }

                    if (isFollowingCamera.value && position != null) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLng(position.toMapLatLng()),
                            500,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            MapAttribution(
                tileSource = tileSource,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            )

            if (uiState.walkTarget == null && !uiState.isRouteReplay && !uiState.isRoaming) {
                Text(
                    text =
                        stringResource(
                            if (uiState.hideTeleportFeatures) {
                                R.string.map_hint_long_press_to_walk
                            } else {
                                R.string.map_hint_tap_to_teleport_long_press
                            },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                MaterialTheme.shapes.small,
                            ).padding(horizontal = 10.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showSearch.value) {
                NominatimSearchBar(
                    onLocationSelected = { lat, lon, _ ->
                        val position = LatLng(latitude = lat, longitude = lon)
                        val mapPos = proj.toMap(position)
                        mapRef.value?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(position.toMapLatLng(), AppConstants.MapConstants.DEFAULT_ZOOM),
                            500,
                        )
                        searchMarkerSource.value?.setGeoJson(buildMarkerGeoJson(mapPos.latitude, mapPos.longitude))
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

    DisposableEffect(Unit) {
        onDispose { onAction(MapAction.ClearPinnedPoint) }
    }

    val pending = uiState.pendingTapPosition
    if (uiState.isPendingTapSheetOpen && pending != null) {
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
            hideTeleportFeatures = uiState.hideTeleportFeatures,
            isRoadRouteFetchInFlight = uiState.isRoadRouteFetchInFlight,
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
            isPreviewLoading = uiState.isRoamingPreviewLoading,
            onAction = onAction,
            onGeneratePreview = { onAction(MapAction.GenerateRoamingPreview) },
            onMinimize = { onAction(MapAction.MinimizeRoamingSheet) },
            onDismiss = { onAction(MapAction.DismissRoamingSheet) },
        )
    }
}
