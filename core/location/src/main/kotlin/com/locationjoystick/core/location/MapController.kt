package com.locationjoystick.core.location

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.di.ApplicationScope
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.isRoutePlaying
import com.locationjoystick.core.model.sortedBySavedItemMode
import com.locationjoystick.core.model.speedProfileIdForKind
import com.locationjoystick.core.model.toConfig
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.OsrmFailureReason
import com.locationjoystick.core.routing.RoutingErrorReporter
import com.locationjoystick.core.routing.classifyOsrmFailure
import com.locationjoystick.core.routing.osrmFailureMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MapController"

private data class LocationStateSnapshot(
    val position: LatLng?,
    val state: MockLocationState,
    val walkPaused: Boolean,
    val mode: MockMode,
    val routeProgress: RouteProgress?,
)

/**
 * Application-scoped owner of all shared map state. Both [MapScreen] and [MapFloatingView] read
 * from [sharedState] — there is no per-surface copy of routes, favorites, position, or walk mode.
 *
 * [MapViewModel] and [WidgetPanelPresenter] are thin adapters: they collect [sharedState] and
 * layer their own per-surface UI state (sheet visibility, camera position, etc.) on top.
 */
@Singleton
class MapController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val locationRepository: LocationRepository,
        private val routeRepository: RouteRepository,
        private val favoriteRepository: FavoriteRepository,
        private val settingsRepository: SettingsRepository,
        private val roamingRepository: RoamingRepository,
        private val walkCoordinator: WalkCoordinator,
        private val teleportUseCase: TeleportUseCase,
        private val startRouteReplayUseCase: StartRouteReplayUseCase,
        private val ephemeralReplayController: EphemeralReplayController,
        private val osrmClient: OsrmClient,
        private val routingErrorReporter: RoutingErrorReporter,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) {
        @Suppress("ktlint:standard:property-naming")
        private val _sharedState = MutableStateFlow(MapSharedState())
        val sharedState: StateFlow<MapSharedState> = _sharedState.asStateFlow()

        val completionMessages = locationRepository.completionEvents

        val isSpoofing: StateFlow<Boolean> =
            locationRepository.mockLocationState
                .map { it != MockLocationState.IDLE }
                .stateIn(appScope, SharingStarted.Eagerly, false)

        val routingErrors: SharedFlow<String> = routingErrorReporter.errors

        private var pendingRoadWalkJob: Job? = null

        init {
            observeLocationState()
            observeRoutes()
            observeFavorites()
            observeRouteWaypoints()
            observeEphemeralWaypoints()
            observeRoaming()
            observeRoadRouteFetchInFlight()
            observeSpeedUnit()
            observeJitterRadiusOverlay()
            observeFavoriteCooldowns()
            observeRecentSearches()
            observeRoamingDefaults()
            observeMapFabFeatures()
            observeModeCompletions()
            restoreLastLocationIfNeeded()
        }

        // ── Observations ─────────────────────────────────────────────────────────

        private fun observeLocationState() {
            appScope.launch {
                combine(
                    locationRepository.currentPosition,
                    locationRepository.mockLocationState,
                    locationRepository.isWalkPaused,
                    locationRepository.currentMode,
                    locationRepository.routeProgress,
                ) { position, state, walkPaused, mode, progress ->
                    LocationStateSnapshot(position, state, walkPaused, mode, progress)
                }.collect { snap ->
                    _sharedState.update {
                        it.copy(
                            currentPosition = snap.position,
                            mockLocationState = snap.state,
                            isWalkPaused = snap.walkPaused,
                            mockMode = snap.mode,
                            routeProgress = snap.routeProgress,
                        )
                    }
                }
            }
        }

        private fun observeRoutes() {
            appScope.launch {
                combine(
                    routeRepository.getRoutes(),
                    settingsRepository.getRoutesSortMode(),
                ) { routes, sortMode -> routes.sortedBySavedItemMode(sortMode) { it.name } }
                    .collect { sorted -> _sharedState.update { it.copy(routes = sorted) } }
            }
        }

        private fun observeFavorites() {
            appScope.launch {
                combine(
                    favoriteRepository.getFavorites(),
                    settingsRepository.getFavoritesSortMode(),
                ) { favorites, sortMode -> favorites.sortedBySavedItemMode(sortMode) { it.name } }
                    .collect { sorted -> _sharedState.update { it.copy(favorites = sorted) } }
            }
        }

        private fun observeRouteWaypoints() {
            appScope.launch {
                locationRepository.routeWaypoints.collect { waypoints ->
                    if (waypoints != null) ephemeralReplayController.clearPendingWaypoints()
                    _sharedState.update { it.copy(routeTrace = waypoints) }
                }
            }
        }

        private fun observeEphemeralWaypoints() {
            appScope.launch {
                ephemeralReplayController.pendingWaypoints.collect { waypoints ->
                    _sharedState.update { current ->
                        when {
                            waypoints.isNotEmpty() && current.ephemeralWaypoints != waypoints -> {
                                val followRoads = (current.walkMode as? WalkMode.EphemeralReplay)?.followRoads ?: false
                                current.copy(walkMode = WalkMode.EphemeralReplay(waypoints, followRoads))
                            }

                            waypoints.isEmpty() && current.walkMode is WalkMode.EphemeralReplay -> {
                                current.copy(walkMode = WalkMode.Idle)
                            }

                            else -> {
                                current
                            }
                        }
                    }
                }
            }
        }

        private fun observeRoadRouteFetchInFlight() {
            appScope.launch {
                locationRepository.isRoadRouteFetchInFlight.collect { inFlight ->
                    _sharedState.update { it.copy(isRoadRouteFetchInFlight = inFlight) }
                }
            }
        }

        private fun observeRoaming() {
            appScope.launch {
                combine(roamingRepository.isRoaming, roamingRepository.isRoamingPaused) { r, p -> r to p }
                    .collect { (roaming, paused) ->
                        _sharedState.update { it.copy(isRoaming = roaming, isRoamingPaused = paused) }
                    }
            }
        }

        private fun observeSpeedUnit() {
            appScope.launch {
                settingsRepository.getSpeedUnit().collect { unit ->
                    _sharedState.update { it.copy(speedUnit = unit) }
                }
            }
        }

        private fun observeJitterRadiusOverlay() {
            appScope.launch {
                combine(
                    locationRepository.debugStats,
                    settingsRepository.getDebugStatsEnabled(),
                ) { stats, enabled -> (stats?.jitterRadiusMeters ?: 0.0) to enabled }
                    .distinctUntilChanged()
                    .collect { (radius, enabled) ->
                        _sharedState.update { it.copy(jitterRadiusMeters = radius, debugStatsEnabled = enabled) }
                    }
            }
        }

        private fun observeFavoriteCooldowns() {
            appScope.launch {
                teleportUseCase.cooldownsFor(favoriteRepository.getFavorites()).collect { states ->
                    _sharedState.update { it.copy(favoriteCooldownStates = states) }
                }
            }
        }

        private fun observeRecentSearches() {
            appScope.launch {
                settingsRepository.getRecentSearches().collect { searches ->
                    _sharedState.update { it.copy(recentSearches = searches) }
                }
            }
        }

        private fun observeRoamingDefaults() {
            appScope.launch {
                settingsRepository.getRoamingDefaults().collect { defaults ->
                    _sharedState.update { it.copy(roamingDefaults = defaults) }
                }
            }
        }

        private fun observeMapFabFeatures() {
            appScope.launch {
                settingsRepository
                    .getMapFeatureOrder()
                    .distinctUntilChanged()
                    .collect { order ->
                        _sharedState.update { it.copy(mapFeatureOrder = order) }
                    }
            }
            appScope.launch {
                settingsRepository
                    .getEnabledMapFeatures()
                    .distinctUntilChanged()
                    .collect { enabled ->
                        _sharedState.update { it.copy(enabledMapFeatures = enabled) }
                    }
            }
        }

        private fun observeModeCompletions() {
            appScope.launch {
                var prevMode: MockMode? = null
                locationRepository.currentMode.collect { mode ->
                    val prev = prevMode
                    prevMode = mode
                    if (mode == MockMode.TELEPORT) {
                        when (prev) {
                            MockMode.WALK_TO -> {
                                locationRepository.setRouteWaypoints(null)
                                _sharedState.update { it.copy(walkMode = WalkMode.Idle) }
                            }

                            MockMode.ROUTE_REPLAY -> {
                                if (_sharedState.value.walkMode is WalkMode.EphemeralReplay) {
                                    ephemeralReplayController.clearPendingWaypoints()
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }

        private fun restoreLastLocationIfNeeded() {
            appScope.launch {
                if (locationRepository.currentPosition.value == null) {
                    val remember = settingsRepository.getRememberLastLocation().first()
                    if (remember) {
                        val last = settingsRepository.getLastLocation().first()
                        if (last != null) locationRepository.setPositionInternal(last)
                    }
                }
            }
        }

        // ── Actions ──────────────────────────────────────────────────────────────

        fun startSpoofing() {
            appScope.launch {
                val startPos =
                    locationRepository.currentPosition.value
                        ?: settingsRepository.getLastLocation().first()
                        ?: LatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON)
                ContextCompat.startForegroundService(
                    context,
                    MockLocationIntentBuilder.startSpoofing(context, startPos.latitude, startPos.longitude),
                )
            }
        }

        fun stopSpoofing() {
            ContextCompat.startForegroundService(context, MockLocationIntentBuilder.stopSpoofing(context))
            ephemeralReplayController.clearPendingWaypoints()
            _sharedState.update { it.copy(walkMode = WalkMode.Idle, routeTrace = null) }
        }

        /**
         * Stops mock GPS the same way as [stopSpoofing], but leaves the floating widget on screen.
         * Joystick overlay still closes. Next Start (widget or app) resumes spoofing.
         */
        fun parkSpoofingKeepWidget() {
            cancelAnyActiveMovement()
            ContextCompat.startForegroundService(context, MockLocationIntentBuilder.parkSpoofingKeepWidget(context))
            ephemeralReplayController.clearPendingWaypoints()
            _sharedState.update { it.copy(walkMode = WalkMode.Idle, routeTrace = null) }
        }

        fun toggleSpoofing() {
            if (isSpoofing.value) stopSpoofing() else startSpoofing()
        }

        fun teleportTo(position: LatLng) {
            cancelAnyActiveMovement()
            appScope.launch { teleportUseCase.execute(position) }
        }

        private fun cancelAnyActiveMovement() {
            pendingRoadWalkJob?.cancel()
            pendingRoadWalkJob = null
            walkCoordinator.cancel()
            if (_sharedState.value.ephemeralWaypoints.isNotEmpty()) {
                context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
                ephemeralReplayController.clearPendingWaypoints()
                _sharedState.update { it.copy(walkMode = WalkMode.Idle) }
            }
        }

        fun walkTo(position: LatLng) {
            cancelAnyActiveMovement()
            _sharedState.update {
                it.copy(
                    walkMode = WalkMode.Walking(target = position, start = it.currentPosition),
                    routeTrace = null,
                )
            }
            walkCoordinator.startWalk(position, appScope) { newPos, speedMs, bearing ->
                context.startService(
                    MockLocationIntentBuilder.updatePosition(
                        context,
                        newPos.latitude,
                        newPos.longitude,
                        speedMs,
                        bearing,
                    ),
                )
            }
        }

        fun walkViaRoads(position: LatLng) {
            cancelAnyActiveMovement()
            pendingRoadWalkJob =
                appScope.launch {
                    val current = locationRepository.currentPosition.value
                    if (current == null) {
                        Log.w(TAG, "walkViaRoads: no current position, straight walk")
                        walkTo(position)
                        return@launch
                    }
                    val routeResult =
                        try {
                            locationRepository.setRoadRouteFetchInFlight(true)
                            osrmClient.getRoute(OsrmClient.PROFILE_FOOT, listOf(current, position))
                        } finally {
                            locationRepository.setRoadRouteFetchInFlight(false)
                        }
                    ensureActive()
                    val waypoints = routeResult.getOrNull()
                    if (waypoints.isNullOrEmpty()) {
                        val reason = routeResult.exceptionOrNull()?.let(::classifyOsrmFailure)
                        Log.w(TAG, "OSRM road-following failed ($reason); falling back to straight walk")
                        val prefix = osrmFailureMessage(context, reason ?: OsrmFailureReason.Unknown)
                        routingErrorReporter.report(context.getString(R.string.walk_via_roads_fallback_message, prefix))
                        walkTo(position)
                        return@launch
                    }
                    locationRepository.setRouteWaypoints(waypoints)
                    _sharedState.update {
                        it.copy(walkMode = WalkMode.Walking(target = position, start = it.currentPosition, isViaRoads = true))
                    }
                    walkCoordinator.startWalkAlongRoute(waypoints, appScope) { newPos, speedMs, bearing ->
                        context.startService(
                            MockLocationIntentBuilder.updatePosition(context, newPos.latitude, newPos.longitude, speedMs, bearing),
                        )
                    }
                }
        }

        fun pauseWalk() {
            locationRepository.setWalkPaused(true)
        }

        fun resumeWalk() {
            locationRepository.setWalkPaused(false)
        }

        fun stopWalk() {
            pendingRoadWalkJob?.cancel()
            pendingRoadWalkJob = null
            walkCoordinator.cancel()
            if (_sharedState.value.ephemeralWaypoints.isNotEmpty()) {
                context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
            }
            ephemeralReplayController.clearPendingWaypoints()
            _sharedState.update { it.copy(walkMode = WalkMode.Idle, isWalkPaused = false, routeTrace = null) }
        }

        fun addEphemeralWaypoint(
            position: LatLng,
            followRoads: Boolean = false,
        ) {
            val current = _sharedState.value
            appScope.launch {
                ephemeralReplayController.addWaypoint(
                    newPoint = position,
                    currentWaypoints = ephemeralReplayController.pendingWaypoints.value,
                    walkStart = current.walkStart,
                    walkTarget = current.walkTarget,
                    followRoads = followRoads,
                    context = context,
                    launchIntent = { context.startService(it) },
                ) ?: return@launch
                _sharedState.update {
                    it.copy(
                        walkMode =
                            WalkMode.EphemeralReplay(
                                ephemeralReplayController.pendingWaypoints.value,
                                followRoads,
                            ),
                    )
                }
            }
        }

        fun appendWaypointToRoute(position: LatLng) {
            context.startService(MockLocationIntentBuilder.appendWaypoint(context, position))
        }

        fun startRouteReplay(
            routeId: String,
            config: RouteStartConfig = RouteStartConfig(),
        ) {
            appScope.launch {
                startRouteReplayUseCase.execute(routeId = routeId, config = config)
            }
        }

        fun savePastedRoute(
            name: String,
            points: List<LatLng>,
        ) {
            appScope.launch {
                routeRepository.insertNamedPastedRoute(name, points).onFailure { e ->
                    Log.e(TAG, "Failed to save pasted route", e)
                }
            }
        }

        fun startPastedRouteReplay(
            points: List<LatLng>,
            config: RouteStartConfig = RouteStartConfig(),
        ) {
            appScope.launch {
                val result = routeRepository.upsertPasteTempRoute(points)
                result.exceptionOrNull()?.let { e ->
                    Log.e(TAG, "Failed to upsert paste temp route", e)
                    return@launch
                }
                startRouteReplayUseCase.execute(
                    routeId = AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID,
                    config = config,
                )
            }
        }

        fun pauseRouteReplay() {
            context.startService(MockLocationIntentBuilder.pauseRouteReplay(context))
        }

        fun resumeRouteReplay() {
            appScope.launch {
                val s = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                context.startService(MockLocationIntentBuilder.resumeRouteReplay(context, s))
            }
        }

        fun stopRouteReplay() {
            context.startService(MockLocationIntentBuilder.stopRouteReplay(context))
        }

        fun jumpToNextWaypoint() {
            context.startService(MockLocationIntentBuilder.jumpToNextWaypoint(context))
        }

        fun jumpToPreviousWaypoint() {
            context.startService(MockLocationIntentBuilder.jumpToPreviousWaypoint(context))
        }

        fun stopRouteOnly() {
            context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
        }

        fun startRoaming(
            draft: RoamingDefaults,
            position: LatLng,
            plannedWaypoints: List<LatLng>? = null,
        ) {
            appScope.launch {
                val mode = locationRepository.currentMode.value
                val state = locationRepository.mockLocationState.value
                if (isRoutePlaying(mode, state)) return@launch
                pendingRoadWalkJob?.cancel()
                pendingRoadWalkJob = null
                walkCoordinator.cancel()
                val wasReplay = mode == MockMode.ROUTE_REPLAY
                val speedMs = settingsRepository.activateSessionSpeed(draft.speedProfileIdForKind())
                val config = draft.toConfig(position).copy(plannedWaypoints = plannedWaypoints)
                val started = roamingRepository.startRoaming(config, speedMs)
                if (started && wasReplay) {
                    ephemeralReplayController.clearPendingWaypoints()
                    _sharedState.update { it.copy(walkMode = WalkMode.Idle) }
                    context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
                }
            }
        }

        fun stopRoaming() {
            appScope.launch { roamingRepository.stopRoaming() }
        }

        fun pauseRoaming() {
            roamingRepository.pauseRoaming()
        }

        fun resumeRoaming() {
            roamingRepository.resumeRoaming()
        }

        fun saveFavorite(
            name: String,
            position: LatLng,
        ) {
            appScope.launch {
                try {
                    favoriteRepository.addFavorite(
                        id =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        name = name,
                        position = position,
                        createdAt = System.currentTimeMillis(),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save favorite", e)
                }
            }
        }

        fun saveCurrentLocation(name: String) {
            val position = _sharedState.value.currentPosition ?: return
            saveFavorite(name, position)
        }

        fun addRecentSearch(
            displayName: String,
            lat: Double,
            lon: Double,
        ) {
            appScope.launch { settingsRepository.addRecentSearch(displayName, lat, lon) }
        }

        /** Generates a roaming preview route, usable from any surface. */
        suspend fun generateRoamingPreview(config: RoamingConfig): List<LatLng>? = roamingRepository.planRoute(config)

        /** Per-position cooldown state flow, usable from any surface. */
        fun cooldownForPosition(pos: LatLng): Flow<CooldownState> = teleportUseCase.cooldownFor(pos)
    }
