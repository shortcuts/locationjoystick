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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.locationjoystick.core.designsystem.UiConstants
import com.locationjoystick.core.designsystem.component.CooldownAdvisoryBadge
import com.locationjoystick.core.designsystem.component.LjMapIconButton
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
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
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
    onAddEphemeralWaypoint: (LatLng) -> Unit,
    onStartRoaming: (RoamingDefaults) -> Unit,
    onStopRoaming: () -> Unit,
    enabledMapFabFeatures: Set<AppFeature> = AppFeature.DEFAULT_MAP_ENABLED,
    onStopRouteReplay: () -> Unit,
    onPauseRouteReplay: () -> Unit,
    onResumeRouteReplay: () -> Unit,
    onDismiss: () -> Unit,
    ephemeralWaypoints: List<LatLng>? = null,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
    cooldownForPosition: ((LatLng) -> Flow<CooldownState>)? = null,
    onSaveCurrentLocation: ((String) -> Unit)? = null,
    quickWalk: Boolean = false,
) {
    val isRoaming = mockMode == MockMode.ROAMING
    val isRouteReplay = mockMode == MockMode.ROUTE_REPLAY
    val isRoutePaused = isRouteReplay && mockLocationState == MockLocationState.PAUSED
    val isWalkActive = walkTarget != null || isRouteReplay
    val context = LocalContext.current
    var roamingPreviewWaypoints by remember { mutableStateOf<List<com.locationjoystick.core.model.LatLng>?>(null) }
    var showRoamingSheet by remember { mutableStateOf(false) }
    var walkStart by remember { mutableStateOf<LatLng?>(null) }
    var pendingTap by remember { mutableStateOf<LatLng?>(null) }
    val quickWalkState = rememberUpdatedState(quickWalk)
    val onWalkToState = rememberUpdatedState(onWalkTo)
    var showSearch by remember { mutableStateOf(false) }
    var showFavoritesPicker by remember { mutableStateOf(false) }
    val isFollowingCamera = remember { mutableStateOf(true) }

    LaunchedEffect(walkTarget) {
        walkStart = if (walkTarget != null) currentPosition else null
    }

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

    LaunchedEffect(roamingPreviewWaypoints) {
        val src = ephemeralRouteSource.value ?: return@LaunchedEffect
        val endSrc = ephemeralEndpointsSource.value ?: return@LaunchedEffect
        val pts = roamingPreviewWaypoints
        if (pts != null && pts.size >= 2) {
            src.setGeoJson(buildLineGeoJson(pts))
            endSrc.setGeoJson(buildPointsGeoJson(pts))
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
                                    if (initialPosition != null) {
                                        MapLatLng(initialPosition.latitude, initialPosition.longitude)
                                    } else {
                                        MapLatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON)
                                    },
                                ).zoom(AppConstants.MapConstants.DEFAULT_ZOOM)
                                .build()

                        map.setStyle(Style.Builder().fromUri("asset://empty.json")) { style ->
                            val layers =
                                style.addLocationLayers(
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

                        map.addOnMapClickListener { latLng ->
                            val pos = LatLng(latLng.latitude, latLng.longitude)
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

                src.setGeoJson(buildPositionGeoJson(position))

                val waypoints = routeWaypoints
                val walkStartSnap = walkStart
                val target = walkTarget
                if (waypoints != null && position != null) {
                    val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(waypoints, position)
                    tracedSrc.setGeoJson(tracedGeoJson)
                    remainingSrc.setGeoJson(remainingGeoJson)
                    endpointsSrc.setGeoJson(buildPointsGeoJson(waypoints))
                } else if (ephemeralWaypoints != null && position != null) {
                    val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(ephemeralWaypoints, position)
                    tracedSrc.setGeoJson(tracedGeoJson)
                    remainingSrc.setGeoJson(remainingGeoJson)
                    endpointsSrc.setGeoJson(buildPointsGeoJson(ephemeralWaypoints))
                } else if (walkStartSnap != null && target != null && position != null) {
                    val walkPoints = listOf(walkStartSnap, target)
                    val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(walkPoints, position)
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

                pendingTapSource.value?.setGeoJson(buildPositionGeoJson(pendingTap))

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
                    val position = LatLng(latitude = lat, longitude = lon)
                    mapRef.value?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(MapLatLng(lat, lon), AppConstants.MapConstants.DEFAULT_ZOOM),
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
            Icon(LjIcons.Close, contentDescription = "Close", tint = LjText)
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
                    contentDescription = "Re-center on location",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        isFollowingCamera.value = true
                        if (currentPosition != null) {
                            mapRef.value?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    MapLatLng(currentPosition.latitude, currentPosition.longitude),
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
                contentDescription = "Open favorites",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { showFavoritesPicker = true },
            )
            if (AppFeature.ROUTES in enabledMapFabFeatures || isRouteReplay) {
                var isRouteControlsExpanded by remember { mutableStateOf(false) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(visible = isRouteReplay && isRouteControlsExpanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            LjMapIconButton(
                                icon = LjIcons.Stop,
                                contentDescription = "Stop route",
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                onClick = {
                                    isRouteControlsExpanded = false
                                    onStopRouteReplay()
                                },
                            )
                            LjMapIconButton(
                                icon = if (isRoutePaused) LjIcons.PlayArrow else LjIcons.Pause,
                                contentDescription = if (isRoutePaused) "Resume route" else "Pause route",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { if (isRoutePaused) onResumeRouteReplay() else onPauseRouteReplay() },
                            )
                        }
                    }
                    LjMapIconButton(
                        icon = LjIcons.Route,
                        contentDescription = if (isRouteReplay) "Route active" else "Open routes",
                        containerColor = if (isRouteReplay) LjSuccess else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isRouteReplay) LjBg else MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { if (isRouteReplay) isRouteControlsExpanded = !isRouteControlsExpanded },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.animation.AnimatedVisibility(visible = isRoaming) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LjMapIconButton(
                            icon = LjIcons.Stop,
                            contentDescription = "Stop roaming",
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            onClick = { onStopRoaming() },
                        )
                        LjMapIconButton(
                            icon = if (isRoamingPaused) LjIcons.PlayArrow else LjIcons.Pause,
                            contentDescription = if (isRoamingPaused) "Resume roaming" else "Pause roaming",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { if (isRoamingPaused) onResumeRoaming() else onPauseRoaming() },
                        )
                    }
                }
                LjMapIconButton(
                    icon = LjIcons.Explore,
                    contentDescription = if (isRoaming) "Roaming active" else "Start roaming",
                    containerColor = if (isRoaming) LjSuccess else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (isRoaming) LjBg else MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { if (!isRoaming) showRoamingSheet = true },
                )
            }
            LjMapIconButton(
                icon = LjIcons.Search,
                contentDescription = "Search location",
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
                isEphemeralReplay = ephemeralWaypoints?.isNotEmpty() == true,
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
    onAddEphemeralWaypoint: (LatLng) -> Unit,
    onDismiss: () -> Unit,
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
            Text("Route in progress", style = MaterialTheme.typography.titleMedium, color = LjText)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onStopRouteAndTeleport(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop route and teleport") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onStopRouteAndWalkTo(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop route and walk here") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onFinishRouteAndWalkTo(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Finish route and walk here") }
        } else {
            Text("Move to this location?", style = MaterialTheme.typography.titleMedium, color = LjText)
            val cooldownState by remember(tap) {
                cooldownForPosition?.invoke(tap) ?: flowOf(CooldownState.Ready)
            }.collectAsStateWithLifecycle(initialValue = CooldownState.Ready)
            Spacer(Modifier.height(8.dp))
            CooldownAdvisoryBadge(
                (cooldownState as? CooldownState.Cooling)?.toAdvisoryLabel() ?: "No wait needed",
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onTeleport(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Teleport here") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onWalkTo(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Walk here") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onWalkViaRoads(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Walk here via roads") }
            if (isWalkActive) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onAddEphemeralWaypoint(tap)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add next point") }
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { onDismiss() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Do nothing") }
    }
}
