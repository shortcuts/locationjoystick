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
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.sortedByAge
import com.locationjoystick.core.model.toConfig
import com.locationjoystick.core.routing.OsrmClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
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
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) {
        @Suppress("ktlint:standard:backing-property-naming")
        private val _state = MutableStateFlow(MapSharedState())
        val sharedState: StateFlow<MapSharedState> = _state.asStateFlow()

        val completionMessages = locationRepository.completionEvents

        private val _routingErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val routingErrors: SharedFlow<String> = _routingErrors.asSharedFlow()

        init {
            observeLocationState()
            observeRoutes()
            observeFavorites()
            observeRouteWaypoints()
            observeEphemeralWaypoints()
            observeRoaming()
            observeSpeedUnit()
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
                ) { position, state, walkPaused, mode ->
                    LocationStateSnapshot(position, state, walkPaused, mode)
                }.collect { snap ->
                    _state.update {
                        it.copy(
                            currentPosition = snap.position,
                            mockLocationState = snap.state,
                            isWalkPaused = snap.walkPaused,
                            mockMode = snap.mode,
                        )
                    }
                }
            }
        }

        private fun observeRoutes() {
            appScope.launch {
                combine(
                    routeRepository.getRoutes(),
                    settingsRepository.getRoutesSortNewestFirst(),
                ) { routes, newestFirst -> routes.sortedByAge(newestFirst) }
                    .collect { sorted -> _state.update { it.copy(routes = sorted) } }
            }
        }

        private fun observeFavorites() {
            appScope.launch {
                combine(
                    favoriteRepository.getFavorites(),
                    settingsRepository.getFavoritesSortNewestFirst(),
                ) { favorites, newestFirst -> favorites.sortedByAge(newestFirst) }
                    .collect { sorted -> _state.update { it.copy(favorites = sorted) } }
            }
        }

        private fun observeRouteWaypoints() {
            appScope.launch {
                locationRepository.routeWaypoints.collect { waypoints ->
                    if (waypoints != null) ephemeralReplayController.clearPendingWaypoints()
                    _state.update { it.copy(routeTrace = waypoints) }
                }
            }
        }

        private fun observeEphemeralWaypoints() {
            appScope.launch {
                ephemeralReplayController.pendingWaypoints.collect { waypoints ->
                    _state.update { current ->
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

        private fun observeRoaming() {
            appScope.launch {
                combine(roamingRepository.isRoaming, roamingRepository.isRoamingPaused) { r, p -> r to p }
                    .collect { (roaming, paused) ->
                        _state.update { it.copy(isRoaming = roaming, isRoamingPaused = paused) }
                    }
            }
        }

        private fun observeSpeedUnit() {
            appScope.launch {
                settingsRepository.getSpeedUnit().collect { unit ->
                    _state.update { it.copy(speedUnit = unit) }
                }
            }
        }

        private fun observeFavoriteCooldowns() {
            appScope.launch {
                teleportUseCase.cooldownsFor(favoriteRepository.getFavorites()).collect { states ->
                    _state.update { it.copy(favoriteCooldownStates = states) }
                }
            }
        }

        private fun observeRecentSearches() {
            appScope.launch {
                settingsRepository.getRecentSearches().collect { searches ->
                    _state.update { it.copy(recentSearches = searches) }
                }
            }
        }

        private fun observeRoamingDefaults() {
            appScope.launch {
                settingsRepository.getRoamingDefaults().collect { defaults ->
                    _state.update { it.copy(roamingDefaults = defaults) }
                }
            }
        }

        private fun observeMapFabFeatures() {
            appScope.launch {
                settingsRepository.getSettingsSnapshot()
                    .map { it.mapFabFeatures }
                    .distinctUntilChanged()
                    .collect { features ->
                        _state.update { it.copy(enabledMapFabFeatures = features) }
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
                                _state.update { it.copy(walkMode = WalkMode.Idle) }
                            }

                            MockMode.ROUTE_REPLAY -> {
                                if (_state.value.walkMode is WalkMode.EphemeralReplay) {
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
            _state.update { it.copy(walkMode = WalkMode.Idle, routeTrace = null) }
        }

        fun teleportTo(position: LatLng) {
            appScope.launch { teleportUseCase.execute(position) }
        }

        fun walkTo(position: LatLng) {
            _state.update {
                it.copy(
                    walkMode = WalkMode.Walking(target = position, start = it.currentPosition),
                    routeTrace = null,
                )
            }
            walkCoordinator.startWalk(position, appScope) { newPos, speedMs, bearing ->
                context.startService(
                    MockLocationIntentBuilder.updatePosition(context, newPos.latitude, newPos.longitude, speedMs, bearing),
                )
            }
        }

        fun walkViaRoads(position: LatLng) {
            appScope.launch {
                val current = locationRepository.currentPosition.value
                if (current == null) {
                    Log.w(TAG, "walkViaRoads: no current position, straight walk")
                    walkTo(position)
                    return@launch
                }
                val waypoints =
                    osrmClient
                        .getRoute(OsrmClient.PROFILE_FOOT, listOf(current, position))
                        .getOrNull()
                if (waypoints.isNullOrEmpty()) {
                    Log.w(TAG, "OSRM road-following failed; falling back to straight walk")
                    _routingErrors.tryEmit("Road routing unavailable — using straight walk")
                    walkTo(position)
                    return@launch
                }
                locationRepository.setRouteWaypoints(waypoints)
                _state.update {
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
            walkCoordinator.cancel()
            if (_state.value.ephemeralWaypoints.isNotEmpty()) {
                context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
            }
            ephemeralReplayController.clearPendingWaypoints()
            _state.update { it.copy(walkMode = WalkMode.Idle, isWalkPaused = false, routeTrace = null) }
        }

        fun addEphemeralWaypoint(position: LatLng) {
            val current = _state.value
            val followRoads =
                (current.walkMode as? WalkMode.Walking)?.isViaRoads
                    ?: (current.walkMode as? WalkMode.EphemeralReplay)?.followRoads
                    ?: false
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
                _state.update {
                    it.copy(
                        walkMode = WalkMode.EphemeralReplay(ephemeralReplayController.pendingWaypoints.value, followRoads),
                    )
                }
            }
        }

        fun appendWaypointToRoute(position: LatLng) {
            context.startService(MockLocationIntentBuilder.appendWaypoint(context, position))
        }

        fun startRouteReplay(
            routeId: String,
            isLooping: Boolean = false,
            isReverse: Boolean = false,
            isReturnToLocation: Boolean = false,
            teleportToStart: Boolean = false,
        ) {
            appScope.launch {
                startRouteReplayUseCase.execute(
                    routeId = routeId,
                    isLooping = isLooping,
                    isReverse = isReverse,
                    isReturnToLocation = isReturnToLocation,
                    teleportToStart = teleportToStart,
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

        fun stopRouteOnly() {
            context.startService(MockLocationIntentBuilder.cancelRouteReplay(context))
        }

        fun startRoaming(
            draft: RoamingDefaults,
            position: LatLng,
            previewWaypoints: List<LatLng>? = null,
        ) {
            appScope.launch {
                val speedMs =
                    settingsRepository
                        .getSpeedProfiles()
                        .first()
                        .firstOrNull { it.id == draft.speedProfileId }
                        ?.speedMetersPerSecond
                        ?: settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                val config = draft.toConfig(position).copy(previewWaypoints = previewWaypoints)
                roamingRepository.startRoaming(config, speedMs)
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

        fun saveCurrentLocation(name: String) {
            val position = _state.value.currentPosition ?: return
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
                    Log.e(TAG, "Failed to save current location", e)
                }
            }
        }

        fun addRecentSearch(
            displayName: String,
            lat: Double,
            lon: Double,
        ) {
            appScope.launch { settingsRepository.addRecentSearch(displayName, lat, lon) }
        }

        /** Generates a roaming preview route, usable from any surface. */
        suspend fun generateRoamingPreview(
            center: LatLng,
            radiusMeters: Double,
            followRoads: Boolean,
            speedProfileId: String,
        ): List<LatLng>? =
            roamingRepository.generatePreviewRoute(
                center = center,
                radiusMeters = radiusMeters,
                followRoads = followRoads,
                speedProfileId = speedProfileId,
            )

        /** Per-position cooldown state flow, usable from any surface. */
        fun cooldownForPosition(pos: LatLng): Flow<CooldownState> = teleportUseCase.cooldownFor(pos)
    }
