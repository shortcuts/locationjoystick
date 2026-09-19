package com.locationjoystick.feature.widget.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSuccess
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.component.CooldownAdvisoryBadge
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjMapIconButton
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.designsystem.component.NominatimSearchBar
import com.locationjoystick.core.designsystem.component.RoamingSheetContent
import com.locationjoystick.core.map.geojson.buildLineGeoJson
import com.locationjoystick.core.map.geojson.buildPointsGeoJson
import com.locationjoystick.core.map.geojson.buildPositionGeoJson
import com.locationjoystick.core.map.geojson.buildRouteTraceGeoJson
import com.locationjoystick.core.map.geojson.emptyGeoJson
import com.locationjoystick.core.map.maplibre.MapLibreLayerIds
import com.locationjoystick.core.map.maplibre.MapLibreSourceIds
import com.locationjoystick.core.map.maplibre.addEphemeralRouteLayers
import com.locationjoystick.core.map.maplibre.addLocationLayers
import com.locationjoystick.core.map.maplibre.applyZoomBounds
import com.locationjoystick.core.map.projection.projection
import com.locationjoystick.core.map.ui.MapAttribution
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.feature.widget.impl.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.geometry.LatLng as MapLatLng

@Composable
internal fun MapFloatingView(
    currentPosition: LatLng?,
    initialPosition: LatLng?,
    walkTarget: LatLng?,
    walkStart: LatLng?,
    routeWaypoints: List<LatLng>?,
    mockMode: MockMode,
    mockLocationState: MockLocationState,
    isRoamingPaused: Boolean,
    favorites: List<FavoriteLocation>,
    roamingDefaults: RoamingDefaults,
    speedUnit: SpeedUnit,
    onResumeRoaming: () -> Unit,
    onPauseRoaming: () -> Unit,
    onGeneratePreviewRoute: suspend (center: LatLng, radiusMeters: Double, followRoads: Boolean, speedProfileId: String) -> List<LatLng>?,
    onTeleport: (LatLng) -> Unit,
    onWalkTo: (LatLng) -> Unit,
    onWalkViaRoads: (LatLng) -> Unit,
    onStopRouteAndTeleport: (LatLng) -> Unit,
    onStopRouteAndWalkTo: (LatLng) -> Unit,
    onFinishRouteAndWalkTo: (LatLng) -> Unit,
    onAddEphemeralWaypoint: (LatLng, Boolean) -> Unit,
    onStartRoaming: (RoamingDefaults) -> Unit,
    onStopRoaming: () -> Unit,
    enabledMapFabFeatures: Set<AppFeature> = AppFeature.DEFAULT_MAP_ENABLED,
    onStopRouteReplay: () -> Unit,
    onPauseRouteReplay: () -> Unit,
    onResumeRouteReplay: () -> Unit,
    onJumpToNextWaypoint: () -> Unit = {},
    onJumpToPreviousWaypoint: () -> Unit = {},
    isRouteControlsExpanded: Boolean,
    onRouteControlsExpandedChange: (Boolean) -> Unit,
    onOpenRoutes: () -> Unit,
    onDismiss: () -> Unit,
    ephemeralWaypoints: List<LatLng>? = null,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
    cooldownForPosition: ((LatLng) -> Flow<CooldownState>)? = null,
    onSaveCurrentLocation: ((String) -> Unit)? = null,
    quickWalk: Boolean = false,
    hideTeleportFeatures: Boolean = false,
    showRouteJumpButtons: Boolean = false,
    tileSource: MapTileSource = MapTileSource.DEFAULT,
) {
    val isRoaming = mockMode == MockMode.ROAMING
    val isRouteReplay = mockMode == MockMode.ROUTE_REPLAY
    val isRoutePaused = isRouteReplay && mockLocationState == MockLocationState.PAUSED
    val isEphemeralReplayActive = ephemeralWaypoints?.isNotEmpty() == true
    val isWalkActive = walkTarget != null || isRouteReplay || isEphemeralReplayActive
    val context = LocalContext.current
    var roamingPreviewWaypoints by remember { mutableStateOf<List<com.locationjoystick.core.model.LatLng>?>(null) }
    var showRoamingSheet by remember { mutableStateOf(false) }
    var pendingTap by remember { mutableStateOf<LatLng?>(null) }
    val quickWalkState = rememberUpdatedState(quickWalk)
    val onWalkToState = rememberUpdatedState(onWalkTo)
    var showSearch by remember { mutableStateOf(false) }
    var showFavoritesPicker by remember { mutableStateOf(false) }
    val isFollowingCamera = remember { mutableStateOf(true) }

    // See MapScreen: everything drawn on / read from the MapLibre map goes through `proj`.
    val proj = tileSource.projection
    val appliedTileSource = remember { mutableStateOf<MapTileSource?>(null) }

    fun LatLng.toMapLatLng(): MapLatLng = proj.toMap(this).let { MapLatLng(it.latitude, it.longitude) }

    val mapView =
        remember(context) {
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
    val pendingTapSource = remember { mutableStateOf<GeoJsonSource?>(null) }

    val applyStyle: (MapLibreMap) -> Unit = { map ->
        appliedTileSource.value = tileSource
        map.applyZoomBounds(tileSource)
        map.setStyle(Style.Builder().fromUri(AppConstants.MapConstants.EMPTY_MAP_STYLE_URI)) { style ->
            val layers =
                style.addLocationLayers(
                    tileSource = tileSource,
                    osmSourceId = MapLibreSourceIds.PANEL_OSM,
                    osmLayerId = MapLibreLayerIds.PANEL_OSM,
                    lineWidth = 3f,
                )
            positionSource.value = layers.positionSource
            tracedSource.value = layers.tracedSource
            remainingSource.value = layers.remainingSource
            endpointsSource.value = layers.endpointsSource
            pendingTapSource.value = layers.pendingTapSource
            val ephemeralSrcs = style.addEphemeralRouteLayers()
            ephemeralRouteSource.value = ephemeralSrcs.routeSource
            ephemeralEndpointsSource.value = ephemeralSrcs.endpointsSource
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

    LaunchedEffect(roamingPreviewWaypoints) {
        val src = ephemeralRouteSource.value ?: return@LaunchedEffect
        val endSrc = ephemeralEndpointsSource.value ?: return@LaunchedEffect
        val pts = roamingPreviewWaypoints
        if (pts != null && pts.size >= 2) {
            val mapPts = proj.toMap(pts)
            src.setGeoJson(buildLineGeoJson(mapPts))
            endSrc.setGeoJson(buildPointsGeoJson(mapPts))
        } else if (pts == null) {
            src.setGeoJson(emptyGeoJson())
            endSrc.setGeoJson(emptyGeoJson())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        mapView.onStart()
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
                .background(LjBg, MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium),
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
                            val pos = proj.fromMap(LatLng(latLng.latitude, latLng.longitude))
                            if (quickWalkState.value) {
                                onWalkToState.value(pos)
                            } else {
                                pendingTap = pos
                            }
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
                val endpointsSrc = endpointsSource.value ?: return@AndroidView
                val ephemeralRouteSrc = ephemeralRouteSource.value ?: return@AndroidView
                val ephemeralEndpointsSrc = ephemeralEndpointsSource.value ?: return@AndroidView
                val position = currentPosition
                val mapPosition = position?.let(proj::toMap)

                src.setGeoJson(buildPositionGeoJson(mapPosition))

                val waypoints = routeWaypoints
                val walkStartSnap = walkStart
                val target = walkTarget
                if (waypoints != null && mapPosition != null) {
                    val mapWaypoints = proj.toMap(waypoints)
                    val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(mapWaypoints, mapPosition)
                    tracedSrc.setGeoJson(tracedGeoJson)
                    remainingSrc.setGeoJson(remainingGeoJson)
                    endpointsSrc.setGeoJson(buildPointsGeoJson(mapWaypoints))
                } else if (ephemeralWaypoints != null && mapPosition != null) {
                    val mapEphemeral = proj.toMap(ephemeralWaypoints)
                    val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(mapEphemeral, mapPosition)
                    tracedSrc.setGeoJson(tracedGeoJson)
                    remainingSrc.setGeoJson(remainingGeoJson)
                    endpointsSrc.setGeoJson(buildPointsGeoJson(mapEphemeral))
                } else if (walkStartSnap != null && target != null && mapPosition != null) {
                    val walkPoints = proj.toMap(listOf(walkStartSnap, target))
                    val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(walkPoints, mapPosition)
                    tracedSrc.setGeoJson(tracedGeoJson)
                    remainingSrc.setGeoJson(remainingGeoJson)
                    endpointsSrc.setGeoJson(buildPointsGeoJson(walkPoints))
                } else {
                    val empty = emptyGeoJson()
                    tracedSrc.setGeoJson(empty)
                    remainingSrc.setGeoJson(empty)
                    endpointsSrc.setGeoJson(empty)
                }

                ephemeralRouteSrc.setGeoJson(emptyGeoJson())
                ephemeralEndpointsSrc.setGeoJson(emptyGeoJson())

                pendingTapSource.value?.setGeoJson(buildPositionGeoJson(pendingTap?.let(proj::toMap)))

                if (isFollowingCamera.value && position != null) {
                    mapRef.value?.animateCamera(
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

        if (showSearch) {
            NominatimSearchBar(
                onLocationSelected = { lat, lon, _ ->
                    val position = LatLng(latitude = lat, longitude = lon)
                    mapRef.value?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(position.toMapLatLng(), AppConstants.MapConstants.DEFAULT_ZOOM),
                        500,
                    )
                    showSearch = false
                    pendingTap = position
                },
                recentSearches = recentSearches,
                onSearchCommitted = onSearchCommitted,
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
            Icon(LjIcons.Close, contentDescription = stringResource(R.string.map_floating_close_cd), tint = LjText)
        }

        // FAB column — bottom-right, mirrors main map layout
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (!isFollowingCamera.value) {
                LjMapIconButton(
                    icon = LjIcons.MyLocation,
                    contentDescription = stringResource(R.string.overlay_recenter_cd),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        isFollowingCamera.value = true
                        if (currentPosition != null) {
                            mapRef.value?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    currentPosition.toMapLatLng(),
                                    AppConstants.MapConstants.DEFAULT_ZOOM,
                                ),
                                500,
                            )
                        }
                    },
                )
            }
            LjMapIconButton(
                icon = LjIcons.Favorite,
                contentDescription = stringResource(R.string.overlay_open_favorites_cd),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { showFavoritesPicker = true },
            )
            if (AppFeature.ROUTES in enabledMapFabFeatures || isRouteReplay) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(visible = isRouteReplay && isRouteControlsExpanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            LjMapIconButton(
                                icon = LjIcons.Stop,
                                contentDescription = stringResource(R.string.overlay_stop_route_cd),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                onClick = {
                                    onRouteControlsExpandedChange(false)
                                    onStopRouteReplay()
                                },
                            )
                            LjMapIconButton(
                                icon = if (isRoutePaused) LjIcons.PlayArrow else LjIcons.Pause,
                                contentDescription =
                                    stringResource(
                                        if (isRoutePaused) {
                                            R.string.overlay_resume_route_cd
                                        } else {
                                            R.string.overlay_pause_route_cd
                                        },
                                    ),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { if (isRoutePaused) onResumeRouteReplay() else onPauseRouteReplay() },
                            )
                            if (!hideTeleportFeatures && showRouteJumpButtons) {
                                LjMapIconButton(
                                    icon = LjIcons.SkipPrevious,
                                    contentDescription = stringResource(R.string.overlay_previous_waypoint_cd),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = LjSuccess,
                                    onClick = onJumpToPreviousWaypoint,
                                )
                                LjMapIconButton(
                                    icon = LjIcons.SkipNext,
                                    contentDescription = stringResource(R.string.overlay_next_waypoint_cd),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = LjSuccess,
                                    onClick = onJumpToNextWaypoint,
                                )
                            }
                        }
                    }
                    LjMapIconButton(
                        icon = LjIcons.Route,
                        contentDescription =
                            stringResource(
                                if (isRouteReplay) {
                                    R.string.overlay_route_active_cd
                                } else {
                                    R.string.overlay_open_routes_cd
                                },
                            ),
                        containerColor = if (isRouteReplay) LjSuccess else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isRouteReplay) LjBg else MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = {
                            if (isRouteReplay) {
                                onRouteControlsExpandedChange(!isRouteControlsExpanded)
                            } else {
                                onOpenRoutes()
                            }
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.animation.AnimatedVisibility(visible = isRoaming) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LjMapIconButton(
                            icon = LjIcons.Stop,
                            contentDescription = stringResource(R.string.overlay_stop_roaming_cd),
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            onClick = { onStopRoaming() },
                        )
                        LjMapIconButton(
                            icon = if (isRoamingPaused) LjIcons.PlayArrow else LjIcons.Pause,
                            contentDescription =
                                stringResource(
                                    if (isRoamingPaused) {
                                        R.string.overlay_resume_roaming_cd
                                    } else {
                                        R.string.overlay_pause_roaming_cd
                                    },
                                ),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { if (isRoamingPaused) onResumeRoaming() else onPauseRoaming() },
                        )
                    }
                }
                LjMapIconButton(
                    icon = LjIcons.Explore,
                    contentDescription =
                        stringResource(
                            if (isRoaming) {
                                R.string.overlay_roaming_active_cd
                            } else {
                                R.string.overlay_start_roaming_cd
                            },
                        ),
                    containerColor = if (isRoaming) LjSuccess else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (isRoaming) LjBg else MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { if (!isRoaming) showRoamingSheet = true },
                )
            }
            LjMapIconButton(
                icon = LjIcons.Search,
                contentDescription = stringResource(R.string.overlay_search_location_cd),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = { showSearch = !showSearch },
            )
        }

        if (showRoamingSheet) {
            OverlayRoamingSheet(
                currentPosition = currentPosition,
                roamingDefaults = roamingDefaults,
                speedUnit = speedUnit,
                mockLocationState = mockLocationState,
                hasPreview = roamingPreviewWaypoints != null,
                onGeneratePreviewRoute = onGeneratePreviewRoute,
                onPreviewGenerated = { roamingPreviewWaypoints = it },
                onStart = { draft ->
                    onStartRoaming(draft)
                    showRoamingSheet = false
                    roamingPreviewWaypoints = null
                },
                onDismiss = {
                    showRoamingSheet = false
                    roamingPreviewWaypoints = null
                },
            )
        }

        val tap = pendingTap
        if (tap != null) {
            TapActionPanel(
                tap = tap,
                isRouteReplay = isRouteReplay,
                isEphemeralReplay = isEphemeralReplayActive,
                isWalkActive = isWalkActive,
                walkStart = walkStart,
                walkTarget = walkTarget,
                routeWaypoints = routeWaypoints,
                cooldownForPosition = cooldownForPosition,
                onTeleport = onTeleport,
                onWalkTo = onWalkTo,
                onWalkViaRoads = onWalkViaRoads,
                onStopRouteAndTeleport = onStopRouteAndTeleport,
                onStopRouteAndWalkTo = onStopRouteAndWalkTo,
                onFinishRouteAndWalkTo = onFinishRouteAndWalkTo,
                onAddEphemeralWaypoint = onAddEphemeralWaypoint,
                onDismiss = { pendingTap = null },
                hideTeleportFeatures = hideTeleportFeatures,
            )
        }

        if (showFavoritesPicker) {
            FavoritesFloatingView(
                favorites = favorites,
                onDismiss = { showFavoritesPicker = false },
                onTeleport = { fav ->
                    onTeleport(fav.position)
                    onDismiss()
                },
                onWalk = { fav ->
                    onWalkTo(fav.position)
                    onDismiss()
                },
                onWalkViaRoads = { fav ->
                    onWalkViaRoads(fav.position)
                    onDismiss()
                },
                onAddFromHere = onSaveCurrentLocation,
                hideTeleport = hideTeleportFeatures,
            )
        }
    }
}

@Composable
private fun BoxScope.OverlayRoamingSheet(
    currentPosition: LatLng?,
    roamingDefaults: RoamingDefaults,
    speedUnit: SpeedUnit,
    mockLocationState: MockLocationState,
    hasPreview: Boolean,
    onGeneratePreviewRoute: suspend (center: LatLng, radiusMeters: Double, followRoads: Boolean, speedProfileId: String) -> List<LatLng>?,
    onPreviewGenerated: (List<LatLng>?) -> Unit,
    onStart: (RoamingDefaults) -> Unit,
    onDismiss: () -> Unit,
) {
    val isSpoofing = mockLocationState == MockLocationState.RUNNING
    var draft by remember(roamingDefaults) { mutableStateOf(roamingDefaults) }
    var isPreviewLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Scrim — tapping outside the sheet dismisses it. ModalBottomSheet is not used because it
    // creates a Dialog internally, which requires an Activity window token unavailable in a
    // service overlay.
    Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() })
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .background(LjBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable {},
    ) {
        // Drag handle — matches ModalBottomSheet visual
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp),
                    ),
        )
        CompositionLocalProvider(LocalContentColor provides LjText) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
                RoamingSheetContent(
                    draft = draft,
                    speedUnit = speedUnit,
                    hasCurrentPosition = currentPosition != null,
                    isSpoofingActive = isSpoofing,
                    hasPreview = hasPreview,
                    isPreviewLoading = isPreviewLoading,
                    onDraftChange = { draft = it },
                    onGenerate = {
                        scope.launch {
                            val pos = currentPosition ?: return@launch
                            isPreviewLoading = true
                            try {
                                onPreviewGenerated(
                                    onGeneratePreviewRoute(pos, draft.radiusMeters, draft.followRoads, draft.speedProfileId),
                                )
                            } finally {
                                isPreviewLoading = false
                            }
                        }
                    },
                    onStart = { onStart(draft) },
                    onViewOnMap = { onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.TapActionPanel(
    tap: LatLng,
    isRouteReplay: Boolean,
    isEphemeralReplay: Boolean,
    isWalkActive: Boolean,
    walkStart: LatLng?,
    walkTarget: LatLng?,
    routeWaypoints: List<LatLng>?,
    cooldownForPosition: ((LatLng) -> Flow<CooldownState>)?,
    onTeleport: (LatLng) -> Unit,
    onWalkTo: (LatLng) -> Unit,
    onWalkViaRoads: (LatLng) -> Unit,
    onStopRouteAndTeleport: (LatLng) -> Unit,
    onStopRouteAndWalkTo: (LatLng) -> Unit,
    onFinishRouteAndWalkTo: (LatLng) -> Unit,
    onAddEphemeralWaypoint: (LatLng, Boolean) -> Unit,
    onDismiss: () -> Unit,
    hideTeleportFeatures: Boolean = false,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(LjBg)
                .clickable {}
                .padding(16.dp),
    ) {
        if (isRouteReplay && !isEphemeralReplay) {
            Text(stringResource(R.string.map_floating_route_in_progress), style = MaterialTheme.typography.titleMedium, color = LjText)
            Spacer(Modifier.height(16.dp))
            if (!hideTeleportFeatures) {
                LjButton(
                    onClick = {
                        onStopRouteAndTeleport(tap)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.map_floating_stop_route_and_teleport)) }
                Spacer(Modifier.height(8.dp))
            }
            LjOutlinedButton(
                onClick = {
                    onStopRouteAndWalkTo(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.map_floating_stop_route_and_walk_here)) }
            Spacer(Modifier.height(8.dp))
            LjOutlinedButton(
                onClick = {
                    onFinishRouteAndWalkTo(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.map_floating_finish_route_and_walk_here)) }
        } else {
            Text(stringResource(R.string.map_floating_move_to_this_location), style = MaterialTheme.typography.titleMedium, color = LjText)
            val cooldownState by remember(tap) {
                cooldownForPosition?.invoke(tap) ?: flowOf(CooldownState.Ready)
            }.collectAsStateWithLifecycle(initialValue = CooldownState.Ready)
            Spacer(Modifier.height(8.dp))
            CooldownAdvisoryBadge(
                (cooldownState as? CooldownState.Cooling)?.toAdvisoryLabel()
                    ?: stringResource(R.string.map_floating_no_wait_needed),
            )
            Spacer(Modifier.height(16.dp))
            if (!hideTeleportFeatures) {
                LjButton(
                    onClick = {
                        onTeleport(tap)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.map_floating_teleport_here)) }
                Spacer(Modifier.height(8.dp))
            }
            LjOutlinedButton(
                onClick = {
                    onWalkTo(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.map_floating_walk_here)) }
            Spacer(Modifier.height(8.dp))
            LjOutlinedButton(
                onClick = {
                    onWalkViaRoads(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.map_floating_walk_here_via_roads)) }
            if (isWalkActive) {
                Spacer(Modifier.height(8.dp))
                LjOutlinedButton(
                    onClick = {
                        onAddEphemeralWaypoint(tap, false)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.map_floating_add_next_point)) }
                Spacer(Modifier.height(8.dp))
                LjOutlinedButton(
                    onClick = {
                        onAddEphemeralWaypoint(tap, true)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.map_floating_add_next_point_via_roads)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        LjTextButton(
            onClick = { onDismiss() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.map_floating_do_nothing)) }
    }
}
