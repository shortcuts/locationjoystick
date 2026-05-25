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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import kotlin.math.roundToInt
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.component.NominatimSearchBar
import com.locationjoystick.core.map.geojson.buildLineGeoJson
import com.locationjoystick.core.map.geojson.buildPointsGeoJson
import com.locationjoystick.core.map.geojson.buildPositionGeoJson
import com.locationjoystick.core.map.geojson.buildRouteTraceGeoJson
import com.locationjoystick.core.map.geojson.emptyGeoJson
import com.locationjoystick.core.map.maplibre.MapLibreLayerIds
import com.locationjoystick.core.map.maplibre.MapLibreSourceIds
import com.locationjoystick.core.map.maplibre.addEphemeralRouteLayers
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
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

private val MAP_FLOATING_VIEW_OSM_SOURCE = AppConstants.MapConstants.PANEL_OSM_SOURCE_ID
private val MAP_FLOATING_VIEW_OSM_LAYER = AppConstants.MapConstants.PANEL_OSM_LAYER_ID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapFloatingView(
    locationRepository: LocationRepository,
    favoriteRepository: FavoriteRepository,
    settingsRepository: SettingsRepository,
    onTeleport: (LatLng) -> Unit,
    onWalkTo: (LatLng) -> Unit,
    onStopRouteAndTeleport: (LatLng) -> Unit,
    onStopRouteAndWalkTo: (LatLng) -> Unit,
    onFinishRouteAndWalkTo: (LatLng) -> Unit,
    onAddEphemeralWaypoint: (LatLng) -> Unit,
    onStartRoaming: (RoamingDefaults) -> Unit,
    onStopRoaming: () -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    recentSearches: List<RecentSearch> = emptyList(),
    onSearchCommitted: ((String, Double, Double) -> Unit)? = null,
) {
    val currentPosition by locationRepository.currentPosition.collectAsStateWithLifecycle()
    val walkTarget by locationRepository.walkTarget.collectAsStateWithLifecycle()
    val routeWaypoints by locationRepository.routeWaypoints.collectAsStateWithLifecycle()
    val mockMode by locationRepository.currentMode.collectAsStateWithLifecycle()
    val isRoaming = mockMode == com.locationjoystick.core.model.MockMode.ROAMING
    val isRouteReplay = mockMode == com.locationjoystick.core.model.MockMode.ROUTE_REPLAY
    val isWalkActive = walkTarget != null || isRouteReplay
    val favoritesFlow = remember { favoriteRepository.getFavorites() }
    val favorites by favoritesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val spoofState by locationRepository.mockLocationState.collectAsStateWithLifecycle()

    val initialPosition = remember { locationRepository.currentPosition.value }

    val roamingDefaults by remember { settingsRepository.getRoamingDefaults() }.collectAsStateWithLifecycle(initialValue = RoamingDefaults(radiusMeters = 1000.0, distanceMeters = 200.0, speedProfileId = "walk", followRoads = false, returnToInitialLocation = false))
    val speedUnit by remember { settingsRepository.getSpeedUnit() }.collectAsStateWithLifecycle(initialValue = SpeedUnit.KMH)
    var showRoamingSheet by remember { mutableStateOf(false) }
    var walkStart by remember { mutableStateOf<LatLng?>(null) }
    var pendingTap by remember { mutableStateOf<LatLng?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showFavoritesPicker by remember { mutableStateOf(false) }
    val isFollowingCamera = remember { mutableStateOf(true) }
    // Holds locally-computed waypoints during the walk→replay transition so the line doesn't flash.
    var ephemeralHint by remember { mutableStateOf<List<LatLng>?>(null) }

    LaunchedEffect(walkTarget) {
        walkStart = if (walkTarget != null) currentPosition else null
    }

    LaunchedEffect(routeWaypoints) {
        if (routeWaypoints != null) ephemeralHint = null
    }

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
                                        MapLatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON)
                                    },
                                ).zoom(AppConstants.MapConstants.DEFAULT_ZOOM)
                                .build()

                        map.setStyle(Style.Builder().fromUri("asset://empty.json")) { style ->
                            style.addSource(
                                RasterSource(
                                    MAP_FLOATING_VIEW_OSM_SOURCE,
                                    TileSet(AppConstants.MapConstants.TILESET_VERSION, AppConstants.MapConstants.OSM_TILE_URL).apply {
                                        maxZoom =
                                            AppConstants.MapConstants.OSM_MAX_ZOOM
                                    },
                                    256,
                                ),
                            )
                            style.addLayer(RasterLayer(MAP_FLOATING_VIEW_OSM_LAYER, MAP_FLOATING_VIEW_OSM_SOURCE))

                            val tracedSrc =
                                GeoJsonSource(MapLibreSourceIds.TRACE_TRACED, emptyGeoJson())
                            style.addSource(tracedSrc)
                            style.addLayer(
                                LineLayer(MapLibreLayerIds.TRACE_TRACED, MapLibreSourceIds.TRACE_TRACED)
                                    .withProperties(
                                        PropertyFactory.lineColor(Color(AppConstants.MapColorConstants.ROUTE_LINE_COLOR).toArgb()),
                                        PropertyFactory.lineWidth(3f),
                                        PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                                    ),
                            )
                            tracedSource.value = tracedSrc

                            val remainingSrc =
                                GeoJsonSource(MapLibreSourceIds.TRACE_REMAINING, emptyGeoJson())
                            style.addSource(remainingSrc)
                            style.addLayer(
                                LineLayer(MapLibreLayerIds.TRACE_REMAINING, MapLibreSourceIds.TRACE_REMAINING)
                                    .withProperties(
                                        PropertyFactory.lineColor(Color(AppConstants.MapColorConstants.ROUTE_LINE_COLOR).toArgb()),
                                        PropertyFactory.lineWidth(3f),
                                    ),
                            )
                            remainingSource.value = remainingSrc

                            val endpointsSrc =
                                GeoJsonSource(MapLibreSourceIds.ENDPOINTS, emptyGeoJson())
                            style.addSource(endpointsSrc)
                            style.addLayer(
                                CircleLayer(MapLibreLayerIds.ENDPOINTS, MapLibreSourceIds.ENDPOINTS)
                                    .withProperties(
                                        PropertyFactory.circleRadius(8f),
                                        PropertyFactory.circleColor(Color(0xFF1E88E5).toArgb()),
                                        PropertyFactory.circleStrokeColor(Color(0xFFFFFFFF).toArgb()),
                                        PropertyFactory.circleStrokeWidth(2f),
                                    ),
                            )
                            endpointsSource.value = endpointsSrc

                            val ephemeralSrcs = style.addEphemeralRouteLayers()
                            ephemeralRouteSource.value = ephemeralSrcs.routeSource
                            ephemeralEndpointsSource.value = ephemeralSrcs.endpointsSource

                            val src =
                                GeoJsonSource(MapLibreSourceIds.POSITION, emptyGeoJson())
                            style.addSource(src)
                            style.addLayer(
                                CircleLayer(MapLibreLayerIds.POSITION, MapLibreSourceIds.POSITION)
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
                val endpointsSrc = endpointsSource.value ?: return@AndroidView
                val ephemeralRouteSrc = ephemeralRouteSource.value ?: return@AndroidView
                val ephemeralEndpointsSrc = ephemeralEndpointsSource.value ?: return@AndroidView
                val position = currentPosition

                src.setGeoJson(buildPositionGeoJson(position))

                val waypoints = routeWaypoints
                val walkStartSnap = walkStart
                val target = walkTarget
                val hint = ephemeralHint
                if (waypoints != null && position != null) {
                    val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(waypoints, position)
                    tracedSrc.setGeoJson(tracedGeoJson)
                    remainingSrc.setGeoJson(remainingGeoJson)
                    endpointsSrc.setGeoJson(buildPointsGeoJson(waypoints))
                } else if (hint != null && position != null) {
                    val (tracedGeoJson, remainingGeoJson) = buildRouteTraceGeoJson(hint, position)
                    tracedSrc.setGeoJson(tracedGeoJson)
                    remainingSrc.setGeoJson(remainingGeoJson)
                    endpointsSrc.setGeoJson(buildPointsGeoJson(hint))
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
                        CameraUpdateFactory.newLatLng(MapLatLng(lat, lon)),
                        500,
                    )
                    showSearch = false
                    onTeleport(position)
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
                onClick = {
                    if (isRoaming) {
                        onStopRoaming()
                    } else {
                        showRoamingSheet = true
                    }
                },
                containerColor = if (isRoaming) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (isRoaming) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Icon(
                    imageVector = if (isRoaming) LjIcons.Stop else LjIcons.Explore,
                    contentDescription = if (isRoaming) "Stop roaming" else "Start roaming",
                )
            }
            FloatingActionButton(
                onClick = { showSearch = !showSearch },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Search location")
            }
            val isSpoofing = spoofState == com.locationjoystick.core.model.MockLocationState.RUNNING
            FloatingActionButton(
                onClick = {
                    if (isSpoofing) locationRepository.stopSpoofing() else locationRepository.startSpoofing()
                },
                containerColor =
                    if (isSpoofing) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(AppConstants.MapColorConstants.ACTIVE_BUTTON_COLOR)
                    },
                contentColor =
                    if (isSpoofing) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        Color.White
                    },
            ) {
                Icon(
                    imageVector = if (isSpoofing) LjIcons.Stop else LjIcons.PlayArrow,
                    contentDescription = if (isSpoofing) "Stop spoofing" else "Start spoofing",
                )
            }
        }

        if (showRoamingSheet) {
            val isMph = speedUnit == SpeedUnit.MPH
            val isSpoofing = spoofState == com.locationjoystick.core.model.MockLocationState.RUNNING
            var draft by remember(roamingDefaults) {
                mutableStateOf(roamingDefaults)
            }
            ModalBottomSheet(onDismissRequest = { showRoamingSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                ) {
                    Text("Roaming", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    var radiusText by remember(isMph) {
                        mutableStateOf(
                            if (isMph) String.format("%.2f", draft.radiusMeters / 1609.344)
                            else draft.radiusMeters.roundToInt().toString()
                        )
                    }
                    OutlinedTextField(
                        value = radiusText,
                        onValueChange = { text ->
                            radiusText = text
                            text.toDoubleOrNull()?.let { v ->
                                val meters = if (isMph) v * 1609.344 else v
                                draft = draft.copy(radiusMeters = meters.coerceIn(AppConstants.RoamingConstants.RADIUS_MIN_METERS, AppConstants.RoamingConstants.RADIUS_MAX_METERS))
                            }
                        },
                        label = { Text(if (isMph) "Radius (mi)" else "Radius (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    var distanceText by remember(isMph) {
                        mutableStateOf(
                            if (isMph) String.format("%.2f", draft.distanceMeters / 1609.344)
                            else draft.distanceMeters.roundToInt().toString()
                        )
                    }
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { text ->
                            distanceText = text
                            text.toDoubleOrNull()?.let { v ->
                                val meters = if (isMph) v * 1609.344 else v
                                draft = draft.copy(distanceMeters = meters.coerceIn(AppConstants.RoamingConstants.DISTANCE_MIN_METERS, AppConstants.RoamingConstants.DISTANCE_MAX_METERS))
                            }
                        },
                        label = { Text(if (isMph) "Route distance (mi)" else "Route distance (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Speed profile", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(AppConstants.ProfileConstants.PROFILE_ID_WALK, AppConstants.ProfileConstants.PROFILE_ID_RUN, AppConstants.ProfileConstants.PROFILE_ID_BIKE).forEach { id ->
                            val label = mapOf("walk" to "Walk", "run" to "Run", "bike" to "Bike")[id] ?: id
                            if (draft.speedProfileId == id) {
                                OutlinedButton(onClick = { draft = draft.copy(speedProfileId = id) }, modifier = Modifier.padding(end = 4.dp)) { Text(label) }
                            } else {
                                FilledTonalButton(onClick = { draft = draft.copy(speedProfileId = id) }, modifier = Modifier.padding(end = 4.dp)) { Text(label) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = draft.followRoads, onCheckedChange = { draft = draft.copy(followRoads = it) })
                        Text("Follow roads", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = draft.returnToInitialLocation, onCheckedChange = { draft = draft.copy(returnToInitialLocation = it) })
                        Text("Return to start", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onStartRoaming(draft)
                            showRoamingSheet = false
                        },
                        enabled = isSpoofing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start")
                    }
                    if (!isSpoofing) {
                        Text(
                            "Start location spoofing first to enable roaming",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
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
                    if (isWalkActive) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val wStart = walkStart
                                val wTarget = walkTarget
                                val existingWaypoints = routeWaypoints
                                ephemeralHint =
                                    when {
                                        existingWaypoints != null -> existingWaypoints + tap
                                        wStart != null && wTarget != null -> listOf(wStart, wTarget, tap)
                                        else -> null
                                    }
                                onAddEphemeralWaypoint(tap)
                                pendingTap = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add next point") }
                    }
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
