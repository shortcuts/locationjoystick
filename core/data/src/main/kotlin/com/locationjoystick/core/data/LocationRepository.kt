package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.ActiveMode
import com.locationjoystick.core.model.CompletionKind
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.afterEntering
import com.locationjoystick.core.model.pausedOrNull
import com.locationjoystick.core.model.pausesReplay
import com.locationjoystick.core.model.resumedOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocationRepository"

/** The OSRM road fetch for one movement action. A walk fetch carries its job so a stop can cancel it. */
sealed interface RoadRouteFetch {
    data object Idle : RoadRouteFetch

    data class WalkFetch(
        val job: Job,
    ) : RoadRouteFetch

    data object ReplayFetch : RoadRouteFetch
}

/** Live per-tick snapshot for the debug overlay. [tickIntervalMs] is wall-clock time since the previous tick. */
data class DebugStats(
    val latitude: Double,
    val longitude: Double,
    val speedMs: Float,
    val altitudeMeters: Double,
    val accuracyMeters: Float,
    val bearing: Float,
    val hasBearing: Boolean,
    val tickIntervalMs: Long,
    val jitterRadiusMeters: Double,
)

/**
 * In-process state holder for the current spoofed position and mock location state.
 *
 * The actual GPS injection is performed by MockLocationService (core:location).
 * This repository acts as the shared state bus between the service and all feature modules.
 * MockLocationService reads from and writes to this repository; feature ViewModels observe it.
 *
 * ## Movement state
 * Read [movement] to know the current mode and whether it is paused. It is the single source of
 * truth — do NOT re-derive it from [activeRouteId], [walkTarget], or roaming flags. Every pause
 * lives there; [mockLocationState] derives PAUSED from it while a replay is paused.
 */
@Singleton
class LocationRepository
    @Inject
    constructor() {
        private val _currentPosition = MutableStateFlow<LatLng?>(null)
        val currentPosition: StateFlow<LatLng?> = _currentPosition.asStateFlow()

        private val _currentBearing = MutableStateFlow<Float?>(null)

        /**
         * Bearing published by movement engines that don't go through
         * MockLocationService's updatePositionWithVector directly (route replay, roaming).
         * Null until the first real movement of a session. Consumed by
         * MockLocationService's reactive collector to update its own `currentBearing` field.
         */
        val currentBearing: StateFlow<Float?> = _currentBearing.asStateFlow()

        fun setBearingInternal(bearing: Float) {
            _currentBearing.value = bearing
        }

        private val _currentSpeedMps = MutableStateFlow<Float?>(null)

        /**
         * Input from roaming. Consumed by MockLocationService's reactive collector to update its own
         * `currentSpeedMs` field. The UI reads [reportedSpeedMps].
         */
        val currentSpeedMps: StateFlow<Float?> = _currentSpeedMps.asStateFlow()

        fun setSpeedInternal(speedMps: Float) {
            _currentSpeedMps.value = speedMps
        }

        private val _reportedSpeedMps = MutableStateFlow<Float?>(null)

        /** Speed the service reports this tick, in every movement mode. Zero while paused or stopped. */
        val reportedSpeedMps: StateFlow<Float?> = _reportedSpeedMps.asStateFlow()

        fun setReportedSpeed(speedMps: Float) {
            _reportedSpeedMps.value = speedMps
        }

        private val _reportedAltitudeMeters = MutableStateFlow<Double?>(null)

        /** Actual altitude being reported this tick — the altitude-override widget button prefills from this. */
        val reportedAltitudeMeters: StateFlow<Double?> = _reportedAltitudeMeters.asStateFlow()

        fun setReportedAltitude(meters: Double) {
            _reportedAltitudeMeters.value = meters
        }

        private val _debugStats = MutableStateFlow<DebugStats?>(null)

        /** Live per-tick stats for the debug overlay (Settings → "Debug stats"). Null when never populated. */
        val debugStats: StateFlow<DebugStats?> = _debugStats.asStateFlow()

        fun setDebugStats(stats: DebugStats) {
            _debugStats.value = stats
        }

        private val spoofingMachine = SpoofingStateMachine()

        private val _activeRouteId = MutableStateFlow<String?>(null)
        val activeRouteId: StateFlow<String?> = _activeRouteId.asStateFlow()

        private val _isReplayBackward = MutableStateFlow(false)
        val isReplayBackward: StateFlow<Boolean> = _isReplayBackward.asStateFlow()

        /** Non-null while a walk-to-target movement is in progress. Cleared on arrival or cancel. */
        private val _walkTarget = MutableStateFlow<LatLng?>(null)
        val walkTarget: StateFlow<LatLng?> = _walkTarget.asStateFlow()

        /** Waypoints of the currently replaying route, null when no route is active. */
        private val _routeWaypoints = MutableStateFlow<List<LatLng>?>(null)
        val routeWaypoints: StateFlow<List<LatLng>?> = _routeWaypoints.asStateFlow()

        private val _roadRouteFetch = MutableStateFlow<RoadRouteFetch>(RoadRouteFetch.Idle)

        /**
         * True while an OSRM road-following fetch is in flight for a movement action initiated from
         * the UI. Lets any surface show a loading/disabled state instead of going silent for up to
         * [AppConstants.OsrmConstants.TOTAL_TIME_BUDGET_MS].
         */
        val isRoadRouteFetchInFlight: StateFlow<Boolean> = _roadRouteFetch.mapState { it !is RoadRouteFetch.Idle }

        val roadRouteFetch: StateFlow<RoadRouteFetch> = _roadRouteFetch.asStateFlow()

        /** Starts a fetch. A [walkJob] makes it a walk fetch, cancellable through [cancelWalkRoadRouteFetch]. */
        fun beginRoadRouteFetch(walkJob: Job? = null) {
            _roadRouteFetch.value = if (walkJob != null) RoadRouteFetch.WalkFetch(walkJob) else RoadRouteFetch.ReplayFetch
        }

        /** Releases the fetch started with the same [walkJob]. Any other owner's fetch stays, so this is safe to call twice. */
        fun endRoadRouteFetch(walkJob: Job? = null) {
            var released = false
            _roadRouteFetch.update {
                val owned =
                    when (it) {
                        is RoadRouteFetch.WalkFetch -> it.job == walkJob
                        RoadRouteFetch.ReplayFetch -> walkJob == null
                        RoadRouteFetch.Idle -> false
                    }
                released = owned
                if (owned) RoadRouteFetch.Idle else it
            }
            if (released) clearPendingPause(if (walkJob != null) ActiveMode.WALK_TO else ActiveMode.ROUTE_REPLAY)
        }

        /** Cancels the in-flight walk fetch. Ignored when idle or when the fetch belongs to a replay. */
        fun cancelWalkRoadRouteFetch() {
            _roadRouteFetch.update { current ->
                if (current is RoadRouteFetch.WalkFetch) {
                    current.job.cancel()
                    RoadRouteFetch.Idle
                } else {
                    Log.w(TAG, "ignored walk fetch cancel from $current")
                    current
                }
            }
            if (_roadRouteFetch.value is RoadRouteFetch.Idle) clearPendingPause(ActiveMode.WALK_TO)
        }

        /** Holds a pause for the walk this fetch will start. Ignored when no walk fetch is pending. */
        fun pauseWalkFetch(): Boolean {
            var changed = false
            _movement.update { current ->
                if (current is MovementState.Manual && _roadRouteFetch.value is RoadRouteFetch.WalkFetch) {
                    changed = true
                    MovementState.PausePending(current.mode, ActiveMode.WALK_TO)
                } else {
                    Log.w(TAG, "ignored walk fetch pause from $current")
                    current
                }
            }
            return changed
        }

        fun resumeWalkFetch(): Boolean {
            var changed = false
            _movement.update { current ->
                if (current is MovementState.PausePending && current.pending == ActiveMode.WALK_TO) {
                    changed = true
                    MovementState.Manual(current.manual)
                } else {
                    Log.w(TAG, "ignored walk fetch resume from $current")
                    current
                }
            }
            return changed
        }

        /** Pauses a replay, or holds the pause while its road fetch runs. Repeating it is a quiet no-op. */
        fun pauseReplay() {
            _movement.update { current ->
                when {
                    current.pausesReplay -> current
                    current == MovementState.Running(ActiveMode.ROUTE_REPLAY) -> MovementState.Paused(ActiveMode.ROUTE_REPLAY)
                    current is MovementState.Manual && _roadRouteFetch.value is RoadRouteFetch.ReplayFetch ->
                        MovementState.PausePending(current.mode, ActiveMode.ROUTE_REPLAY)
                    else -> {
                        Log.w(TAG, "ignored replay pause from $current")
                        current
                    }
                }
            }
        }

        private fun clearPendingPause(active: ActiveMode) {
            _movement.update { if (it is MovementState.PausePending && it.pending == active) MovementState.Manual(it.manual) else it }
        }

        private fun clearReplayPause() {
            _movement.update {
                when {
                    it == MovementState.Paused(ActiveMode.ROUTE_REPLAY) -> MovementState.Running(ActiveMode.ROUTE_REPLAY)
                    it is MovementState.PausePending && it.pending == ActiveMode.ROUTE_REPLAY -> MovementState.Manual(it.manual)
                    else -> it
                }
            }
        }

        private val _movement = MutableStateFlow<MovementState>(MovementState.Manual(MockMode.TELEPORT))

        val currentMode: StateFlow<MockMode> = _movement.mapState { it.mode }

        /** Mode and pause state. */
        val movement: Flow<MovementState> = _movement.asStateFlow()

        fun currentMovement(): MovementState = _movement.value

        /** The spoofing lifecycle, with PAUSED while a replay is paused. */
        val mockLocationState: StateFlow<MockLocationState> =
            combineState(spoofingMachine.state, _movement) { spoofing, movement ->
                if (spoofing == MockLocationState.RUNNING && movement.pausesReplay) MockLocationState.PAUSED else spoofing
            }

        private inline fun transition(next: (MovementState) -> MovementState?) {
            _movement.update { current -> next(current) ?: current.also { Log.w(TAG, "ignored transition from $it") } }
        }

        private val _completionEvents = MutableSharedFlow<CompletionKind>(extraBufferCapacity = 1)
        val completionEvents: SharedFlow<CompletionKind> = _completionEvents.asSharedFlow()

        fun emitCompletion(kind: CompletionKind) {
            _completionEvents.tryEmit(kind)
        }

        fun updatePosition(
            lat: Double,
            lon: Double,
        ) {
            _currentPosition.value = LatLng(latitude = lat, longitude = lon)
        }

        fun updatePosition(position: LatLng) {
            _currentPosition.value = position
        }

        fun setPositionInternal(position: LatLng) {
            _currentPosition.value = position
        }

        /** Returns the current position as a [Flow]. Prefer [currentPosition] StateFlow for new code. */
        fun observePosition(): Flow<LatLng?> = _currentPosition.asStateFlow()

        /** Returns the mock location state as a [Flow]. Prefer [mockLocationState] StateFlow for new code. */
        fun observeState(): Flow<MockLocationState> = mockLocationState

        /** The only way to change the spoofing state. Returns false when the transition table rejects [event]. */
        fun sendSpoofingEvent(event: SpoofingEvent): Boolean {
            val accepted = spoofingMachine.send(event)
            if (!accepted) return false
            when (event) {
                SpoofingEvent.START ->
                    if (_currentPosition.value == null) {
                        _currentPosition.value =
                            LatLng(latitude = AppConstants.MapConstants.DEFAULT_LAT, longitude = AppConstants.MapConstants.DEFAULT_LON)
                    }
                SpoofingEvent.STOP -> {
                    _reportedSpeedMps.value = 0f
                    clearReplayPause()
                }
                SpoofingEvent.FAIL -> clearReplayPause()
                SpoofingEvent.CLEAR_ERROR -> Unit
            }
            return true
        }

        fun startSpoofing() {
            sendSpoofingEvent(SpoofingEvent.START)
        }

        fun stopSpoofing() {
            sendSpoofingEvent(SpoofingEvent.STOP)
        }

        fun setActiveRouteId(id: String?) {
            _activeRouteId.value = id
        }

        fun setIsReplayBackward(backward: Boolean) {
            _isReplayBackward.value = backward
        }

        fun setWalkTarget(target: LatLng?) {
            _walkTarget.value = target
            if (target != null) {
                setMockMode(MockMode.WALK_TO)
            } else if (_movement.value.mode == MockMode.WALK_TO) {
                // Only reset a walk, so a mode set by another subsystem survives (e.g. replay after a walk).
                setMockMode(MockMode.TELEPORT)
            } else {
                clearPendingPause(ActiveMode.WALK_TO)
            }
        }

        fun setMockMode(mode: MockMode) {
            transition { it.afterEntering(mode) }
        }

        /** Pauses a walk or roam. Ignored in any other state. */
        fun pauseMovement() {
            transition { it.pausedOrNull() }
        }

        fun resumeMovement() {
            transition { it.resumedOrNull() }
        }

        fun setRouteWaypoints(waypoints: List<LatLng>?) {
            _routeWaypoints.value = waypoints
        }

        private val _routeProgress = MutableStateFlow<RouteProgress?>(null)

        /** Named-stop progress while a route replay is loaded. Null when no replay is active. */
        val routeProgress: StateFlow<RouteProgress?> = _routeProgress.asStateFlow()

        fun setRouteProgress(progress: RouteProgress?) {
            _routeProgress.value = progress
        }
    }

/** A [StateFlow] view whose value and emissions follow [transform] of two flows. */
private fun <A, B, R> combineState(
    a: StateFlow<A>,
    b: StateFlow<B>,
    transform: (A, B) -> R,
): StateFlow<R> =
    object : StateFlow<R> {
        override val value: R get() = transform(a.value, b.value)
        override val replayCache: List<R> get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<R>): Nothing {
            combine(a, b, transform).distinctUntilChanged().collect(collector)
            error("StateFlow collection never completes")
        }
    }

/** A [StateFlow] view whose value and emissions follow [transform] of this flow. */
private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> =
    object : StateFlow<R> {
        private val source = this@mapState
        override val value: R get() = transform(source.value)
        override val replayCache: List<R> get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<R>): Nothing {
            source.map(transform).distinctUntilChanged().collect(collector)
            error("StateFlow collection never completes")
        }
    }
