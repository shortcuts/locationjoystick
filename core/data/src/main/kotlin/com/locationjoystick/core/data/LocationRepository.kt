package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocationRepository"

/**
 * In-process state holder for the current spoofed position and mock location state.
 *
 * The actual GPS injection is performed by MockLocationService (core:location).
 * This repository acts as the shared state bus between the service and all feature modules.
 * MockLocationService reads from and writes to this repository; feature ViewModels observe it.
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

        fun observePosition(): Flow<LatLng?> = _currentPosition.asStateFlow()

        fun observeState(): Flow<MockLocationState> = _mockLocationState.asStateFlow()

        suspend fun updatePosition(
            lat: Double,
            lon: Double,
        ) {
            _currentPosition.value = LatLng(latitude = lat, longitude = lon)
        }

        suspend fun updatePosition(position: LatLng) {
            _currentPosition.value = position
        }

        fun setPositionInternal(position: LatLng) {
            _currentPosition.value = position
        }

        suspend fun startSpoofing() {
            Log.d(TAG, "startSpoofing requested")
            if (_currentPosition.value == null) {
                _currentPosition.value = LatLng(latitude = 48.8566, longitude = 2.3522)
            }
            _mockLocationState.value = MockLocationState.RUNNING
        }

        suspend fun stopSpoofing() {
            Log.d(TAG, "stopSpoofing requested")
            _mockLocationState.value = MockLocationState.IDLE
        }

        suspend fun pauseSpoofing() {
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
    }
