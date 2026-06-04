package com.locationjoystick.core.data

import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkRepository
    @Inject
    constructor() {
        // replay=1 buffers the latest coord so cold-start deep links (emitted before
        // MapViewModel starts collecting) are not silently dropped. consume() resets the
        // replay cache after processing to prevent redelivery on ViewModel recreation.
        private val _pendingCoords = MutableSharedFlow<LatLng>(replay = 1)
        val pendingCoords: Flow<LatLng> = _pendingCoords.asSharedFlow()

        fun setPendingCoords(
            lat: Double,
            lon: Double,
        ) {
            _pendingCoords.tryEmit(LatLng(lat, lon))
        }

        fun consume() {
            _pendingCoords.resetReplayCache()
        }
    }
