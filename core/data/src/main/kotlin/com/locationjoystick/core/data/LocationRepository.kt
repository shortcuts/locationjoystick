package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocationRepository"

/**
 * In-process state holder for the current spoofed position and mock location state.
 *
 * The actual GPS injection is performed by MockLocationService (core:location).
 * This repository acts as the shared state bus between the service and all feature modules.
 * MockLocationService reads from and writes to this repository; feature ViewModels observe it.
 *
 * ## Unified activity state
 * Use [isActivityActive] to know whether any movement mode is currently running
 * (route replay, roaming, or walk-to). This is the single source of truth — do NOT
 * re-derive this by combining [activeRouteId], [walkTarget], and roaming flags individually.
 *
 * Use [isActivityPausable] to know whether the current active mode supports pause/resume.
 */
@Singleton
class LocationRepository
    @Inject
    constructor() {
        private val _currentPosition = MutableStateFlow<LatLng?>(null)
        val currentPosition: StateFlow<LatLng?> = _currentPosition.asStateFlow()

        private val _mockLocationState = MutableStateFlow(MockLocationState.IDLE)
        val mockLocationState: StateFlow<MockLocationState> = _mockLocationState.asStateFlow()

        private val _activeRouteId = MutableStateFlow<String?>(null)
        val activeRouteId: StateFlow<String?> = _activeRouteId.asStateFlow()

        private val _isReplayBackward = MutableStateFlow(false)
        val isReplayBackward: StateFlow<Boolean> = _isReplayBackward.asStateFlow()

        /** Non-null while a walk-to-target movement is in progress. Cleared on arrival or cancel. */
        private val _walkTarget = MutableStateFlow<LatLng?>(null)
        val walkTarget: StateFlow<LatLng?> = _walkTarget.asStateFlow()

        /** Whether walk-to movement is currently paused (walkTarget set but ticks suspended). */
        private val _isWalkPaused = MutableStateFlow(false)
        val isWalkPaused: StateFlow<Boolean> = _isWalkPaused.asStateFlow()

        /** Waypoints of the currently replaying route, null when no route is active. */
        private val _routeWaypoints = MutableStateFlow<List<LatLng>?>(null)
        val routeWaypoints: StateFlow<List<LatLng>?> = _routeWaypoints.asStateFlow()

        private val _currentMode = MutableStateFlow(MockMode.TELEPORT)
        val currentMode: StateFlow<MockMode> = _currentMode.asStateFlow()

        private val _completionEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val completionEvents: SharedFlow<String> = _completionEvents.asSharedFlow()

        fun emitCompletion(message: String) {
            _completionEvents.tryEmit(message)
        }

        /**
         * True whenever any active movement mode is running:
         * [MockMode.ROUTE_REPLAY], [MockMode.ROAMING], or [MockMode.WALK_TO].
         */
        val isActivityActive: Flow<Boolean> =
            _currentMode.map { mode ->
                mode == MockMode.ROUTE_REPLAY || mode == MockMode.ROAMING || mode == MockMode.WALK_TO
            }

        /**
         * True when the current active mode supports pause/resume.
         */
        val isActivityPausable: Flow<Boolean> =
            _currentMode.map { mode ->
                mode == MockMode.ROUTE_REPLAY || mode == MockMode.WALK_TO || mode == MockMode.ROAMING
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
        fun observeState(): Flow<MockLocationState> = _mockLocationState.asStateFlow()

        fun startSpoofing() {
            Log.d(TAG, "startSpoofing requested")
            if (_currentPosition.value == null) {
                _currentPosition.value =
                    LatLng(latitude = AppConstants.MapConstants.DEFAULT_LAT, longitude = AppConstants.MapConstants.DEFAULT_LON)
            }
            _mockLocationState.value = MockLocationState.RUNNING
        }

        fun stopSpoofing() {
            Log.d(TAG, "stopSpoofing requested")
            _mockLocationState.value = MockLocationState.IDLE
        }

        fun pauseSpoofing() {
            Log.d(TAG, "pauseSpoofing requested")
            _mockLocationState.value = MockLocationState.PAUSED
        }

        fun reportError() {
            Log.e(TAG, "MockLocationService reported an error")
            _mockLocationState.value = MockLocationState.ERROR
        }

        fun setActiveRouteId(id: String?) {
            _activeRouteId.value = id
        }

        fun setIsReplayBackward(backward: Boolean) {
            _isReplayBackward.value = backward
        }

        fun setWalkTarget(target: LatLng?) {
            _walkTarget.value = target
            if (target == null) {
                _isWalkPaused.value = false
                // Only reset mode if we are currently in WALK_TO to avoid clobbering
                // a mode set by another subsystem (e.g. ROUTE_REPLAY stopping after walk).
                if (_currentMode.value == MockMode.WALK_TO) {
                    _currentMode.value = MockMode.TELEPORT
                }
            } else {
                _currentMode.value = MockMode.WALK_TO
            }
        }

        fun setWalkPaused(paused: Boolean) {
            _isWalkPaused.value = paused
        }

        fun setMockMode(mode: MockMode) {
            _currentMode.value = mode
        }

        fun setRouteWaypoints(waypoints: List<LatLng>?) {
            _routeWaypoints.value = waypoints
        }
    }
