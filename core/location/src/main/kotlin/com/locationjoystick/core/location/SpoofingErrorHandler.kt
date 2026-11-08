package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.model.MockMode

/** Moves the service into ERROR and stops every movement mode. Reports back through [send]. */
internal class SpoofingErrorHandler(
    private val send: (SpoofingEvent) -> Boolean,
    private val locationRepository: LocationRepository,
    private val wakeLock: SpoofingWakeLockHolder,
    private val walkCoordinator: WalkCoordinator,
    private val replayOrchestrator: ReplayOrchestrator,
    private val roamingRepository: RoamingRepository,
) {
    fun setError() {
        send(SpoofingEvent.FAIL)
        locationRepository.reportError()
        wakeLock.release()
    }

    suspend fun stopMovementOnError() {
        walkCoordinator.cancel()
        replayOrchestrator.handleCancel()
        roamingRepository.stopRoaming()
        locationRepository.setMockMode(MockMode.TELEPORT)
    }
}
