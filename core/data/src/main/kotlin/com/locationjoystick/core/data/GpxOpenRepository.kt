package com.locationjoystick.core.data

import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class PendingGpxOpen(
    val points: List<LatLng>,
    val suggestedName: String,
    val id: Long = 0L,
)

@Singleton
class GpxOpenRepository
    @Inject
    constructor() {
        // Held until the paste sheet is dismissed or used. Collecting must not consume: ON_STOP
        // redirects the map to Idle, and the dying ViewModel would otherwise wipe the pending
        // open before the next map starts (cold start works; a second open often would not).
        private val nextId = AtomicLong(0)
        private val _pending = MutableStateFlow<PendingGpxOpen?>(null)
        val pending: Flow<PendingGpxOpen> = _pending.filterNotNull()

        fun setPending(
            points: List<LatLng>,
            suggestedName: String,
        ) {
            _pending.value = PendingGpxOpen(points, suggestedName, nextId.incrementAndGet())
        }

        fun consume() {
            _pending.value = null
        }
    }
