package com.locationjoystick.core.location

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.di.ApplicationScope
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RealLocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.RouteStartConfig
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
import kotlinx.coroutines.delay
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
        private val realLocationRepository: RealLocationRepository,
        private val startRouteReplayUseCase: StartRouteReplayUseCase,
        private val ephemeralReplayController: EphemeralReplayController,
        private val osrmClient: OsrmClient,
        private val routingErrorReporter: RoutingErrorReporter,
        private val groupRepository: GroupRepository,
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
        private var restoreJob: Job? = null

        /** Deduplicates requests to turn Follow leader off while the setting is being persisted. */
        private var followerExitJob: Job? = null
        private var followerResumeJob: Job? = null

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
            observeFollowerDeparture()
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
                    settingsRepository.getRoutesManualOrder(),
                ) { routes, sortMode, manualOrder -> routes.sortedBySavedItemMode(sortMode, manualOrder, { it.id }) { it.name } }
                    .collect { sorted -> _sharedState.update { it.copy(routes = sorted) } }
            }
        }

        private fun observeFavorites() {
            appScope.launch {
                combine(
                    favoriteRepository.getFavorites(),
                    settingsRepository.getFavoritesSortMode(),
                    settingsRepository.getFavoritesManualOrder(),
                ) { favorites, sortMode, manualOrder -> favorites.sortedBySavedItemMode(sortMode, manualOrder, { it.id }) { it.name } }
                    .collect { sorted -> _sharedState.update { it.copy(favorites = sorted) } }
            }
            appScope.launch {
                settingsRepository.getHomeFavoriteId().collect { id -> _sharedState.update { it.copy(homeFavoriteId = id) } }
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

        /**
         * Resolves the initial map position on startup:
         * 1. Uses the Home favorite's position if one is set (beats the remember toggle).
         * 2. Else the remembered last location if enabled.
         * 3. Falls back to the real device location (last-known fix, then a fresh fix), excluding mock
         *    providers, only when location permission is granted.
         * 4. Falls back to the app default location when permission is granted but no fix arrives, so the
         *    map always shows a point.
         *
         * Single-flight: a call while a previous restore is still running is a no-op. A restore that ran
         * without permission leaves the position unset so the next call retries.
         */
        @Synchronized
        fun restoreLastLocationIfNeeded() {
            if (restoreJob?.isActive == true) return
            restoreJob =
                appScope.launch {
                    if (locationRepository.currentPosition.value == null) {
                        val remember = settingsRepository.getRememberLastLocation().first()
                        val savedLocation = if (remember) settingsRepository.getLastLocation().first() else null
                        val known = homePosition() ?: savedLocation ?: realLocationRepository.lastKnownRealPosition()
                        val initialPos =
                            known
                                ?: if (realLocationRepository.hasFinePermission()) {
                                    LatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON)
                                } else {
                                    null
                                }
                        if (initialPos == null) return@launch
                        locationRepository.setPositionInternal(initialPos)
                        if (known == null) {
                            // The default shows at once; the slow fresh fix then moves the map, unless the user
                            // already moved the position (teleport, start) while it was pending.
                            realLocationRepository.getCurrentPosition().getOrNull()?.let { fix ->
                                if (locationRepository.currentPosition.value == initialPos) {
                                    locationRepository.setPositionInternal(fix)
                                }
                            }
                        }
                    }
                }
        }

        /** Home favorite's position, or null when none is set or the favorite no longer exists. */
        private suspend fun homePosition(): LatLng? {
            val id = settingsRepository.getHomeFavoriteId().first() ?: return null
            return favoriteRepository
                .getFavorites()
                .first()
                .find { it.id == id }
                ?.position
        }

        /** Marks [id] as Home, moving the flag from any other favorite; clears it if [id] is already Home. */
        fun toggleHomeFavorite(id: String) {
            appScope.launch {
                val current = settingsRepository.getHomeFavoriteId().first()
                settingsRepository.setHomeFavoriteId(if (current == id) null else id)
            }
        }

        // ── Actions ──────────────────────────────────────────────────────────────

        fun startSpoofing() {
            appScope.launch {
                val startPos =
                    locationRepository.currentPosition.value
                        ?: homePosition()
                        ?: settingsRepository.getLastLocation().first()
                        // No position at all yet: start where the map opens, so the first fix is
                        // inside the selected tile provider's coverage (Beijing for Amap, Paris for OSM).
                        ?: settingsRepository.getMapTileSource().first().defaultCenter
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
            startStraightWalk(position, walkCoordinator.currentGeneration())
        }

        /**
         * Starts a straight walk unless [expectedGeneration] went stale — i.e. a teleport, stop
         * or newer walk cancelled the request that asked for it (issue #99).
         *
         * @return `true` if the walk started.
         */
        private fun startStraightWalk(
            position: LatLng,
            expectedGeneration: Long,
        ): Boolean {
            val walking = WalkMode.Walking(target = position, start = _sharedState.value.currentPosition)
            _sharedState.update { it.copy(walkMode = walking, routeTrace = null) }
            val started =
                walkCoordinator.startWalk(position, appScope, expectedGeneration) { newPos, speedMs, bearing ->
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
            if (!started) {
                _sharedState.update { if (it.walkMode == walking) it.copy(walkMode = WalkMode.Idle) else it }
            }
            return started
        }

        fun walkViaRoads(position: LatLng) {
            cancelAnyActiveMovement()
            // Teleports from other screens (Favorites, Routes, Capture jump) cancel through
            // WalkCoordinator, not pendingRoadWalkJob, so the late OSRM result is gated on this
            // token instead — otherwise it would start walking from the teleport spot back
            // toward the old target (issue #99).
            val generation = walkCoordinator.currentGeneration()
            pendingRoadWalkJob =
                appScope.launch {
                    val current = locationRepository.currentPosition.value
                    if (current == null) {
                        Log.w(TAG, "walkViaRoads: no current position, straight walk")
                        startStraightWalk(position, generation)
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
                        if (!startStraightWalk(position, generation)) return@launch
                        val prefix = osrmFailureMessage(context, reason ?: OsrmFailureReason.Unknown)
                        routingErrorReporter.report(context.getString(R.string.walk_via_roads_fallback_message, prefix))
                        return@launch
                    }
                    val walking = WalkMode.Walking(target = position, start = current, isViaRoads = true)
                    locationRepository.setRouteWaypoints(waypoints)
                    _sharedState.update { it.copy(walkMode = walking) }
                    val started =
                        walkCoordinator.startWalkAlongRoute(waypoints, appScope, generation) { newPos, speed, bearing ->
                            context.startService(
                                MockLocationIntentBuilder.updatePosition(
                                    context,
                                    newPos.latitude,
                                    newPos.longitude,
                                    speed,
                                    bearing,
                                ),
                            )
                        }
                    if (!started) {
                        Log.d(TAG, "walkViaRoads: cancelled while routing; dropping result")
                        if (locationRepository.routeWaypoints.value == waypoints) {
                            locationRepository.setRouteWaypoints(null)
                        }
                        _sharedState.update { if (it.walkMode == walking) it.copy(walkMode = WalkMode.Idle) else it }
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

        /**
         * Joystick takeover for every mode: pauses the current activity in place for manual steering,
         * retaining its destination and progress. A follower leaves Follow leader instead (same as
         * turning it off on the Group Sync screen; the device stays in the group).
         */
        fun pauseAutomatedMovement() {
            val mode = locationRepository.currentMode.value
            when {
                mode == MockMode.FOLLOWER -> {
                    if (followerExitJob?.isActive != true) {
                        followerExitJob =
                            appScope.launch {
                                groupRepository.setFollowerModeEnabled(false)
                                context.startService(MockLocationIntentBuilder.exitFollower(context))
                                resumeFollowAfterIdle()
                            }
                    }
                }
                mode == MockMode.WALK_TO || pendingRoadWalkJob?.isActive == true -> {
                    if (!locationRepository.isWalkPaused.value) pauseWalk()
                }
                mode == MockMode.ROAMING || roamingRepository.isRoaming.value -> {
                    if (!roamingRepository.isRoamingPaused.value) roamingRepository.pauseRoaming()
                }
                mode == MockMode.ROUTE_REPLAY || locationRepository.isRoadRouteFetchInFlight.value -> {
                    if (locationRepository.mockLocationState.value != MockLocationState.PAUSED) {
                        // Mark this synchronously so repeated touch events do not queue more pause commands.
                        locationRepository.pauseSpoofing()
                        pauseRouteReplay()
                    }
                }
            }
        }

        /**
         * A follower that left FOLLOWER mode by hand (joystick, teleport, walk, route, roam) goes
         * back to Follow leader once the position has been still for
         * [AppConstants.SyncConstants.FOLLOWER_RESUME_IDLE_MS] with no activity running. A paused or
         * playing activity just keeps the wait going. Ends when spoofing stops, the user turns
         * following back on, or the device leaves the group.
         */
        private fun resumeFollowAfterIdle() {
            followerResumeJob?.cancel()
            followerResumeJob =
                appScope.launch {
                    while (true) {
                        val before = locationRepository.currentPosition.value
                        delay(AppConstants.SyncConstants.FOLLOWER_RESUME_IDLE_MS)
                        val mode = locationRepository.currentMode.value
                        if (mode == MockMode.FOLLOWER ||
                            locationRepository.mockLocationState.value == MockLocationState.IDLE ||
                            groupRepository.groupState.first().role != GroupRole.FOLLOWER
                        ) {
                            return@launch
                        }
                        val manual = mode == MockMode.JOYSTICK || mode == MockMode.TELEPORT
                        if (manual && locationRepository.currentPosition.value == before) {
                            groupRepository.setFollowerModeEnabled(true)
                            context.startService(MockLocationIntentBuilder.resumeFollower(context))
                            return@launch
                        }
                    }
                }
        }

        /**
         * Teleports, walks and route starts leave FOLLOWER mode without touching the Follow leader
         * flag, so nothing would ever re-enter it. The joystick takeover clears the flag itself and
         * starts the same wait directly.
         */
        private fun observeFollowerDeparture() {
            appScope.launch {
                var previous = locationRepository.currentMode.value
                locationRepository.currentMode.collect { mode ->
                    val left = previous == MockMode.FOLLOWER && mode != MockMode.FOLLOWER
                    previous = mode
                    if (left) {
                        val gs = groupRepository.groupState.first()
                        if (gs.role == GroupRole.FOLLOWER && gs.followerModeEnabled) resumeFollowAfterIdle()
                    }
                }
            }
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
            bypassHideTeleport: Boolean = false,
        ) {
            appScope.launch {
                startRouteReplayUseCase.execute(routeId = routeId, config = config, bypassHideTeleport = bypassHideTeleport)
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
