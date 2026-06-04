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
        // replay=0 ensures one-shot event semantics: each emitted coord is delivered exactly
        // once to current subscribers. No buffering of the last value means resubscription
        // (ViewModel recreation, process death) will not replay a stale coord.
        // Deep links are always received while the app is running (Android won't launch
        // a background app via deep link), so we don't need cold-start buffering.
        private val _pendingCoords = MutableSharedFlow<LatLng>(replay = 0)
        val pendingCoords: Flow<LatLng> = _pendingCoords.asSharedFlow()

        fun setPendingCoords(
            lat: Double,
            lon: Double,
        ) {
            _pendingCoords.tryEmit(LatLng(lat, lon))
        }

        fun consume() {
            // No-op with replay=0, but kept for API compatibility and clarity.
            // Explicit call documents intent: "this coord has been processed".
        }
    }
