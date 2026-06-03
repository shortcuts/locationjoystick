package com.locationjoystick.core.data

import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkRepository
    @Inject
    constructor() {
        private val _pendingCoords = Channel<LatLng>(Channel.CONFLATED)
        val pendingCoords: Flow<LatLng> = _pendingCoords.receiveAsFlow()

        fun setPendingCoords(
            lat: Double,
            lon: Double,
        ) {
            _pendingCoords.trySend(LatLng(lat, lon))
        }
    }
