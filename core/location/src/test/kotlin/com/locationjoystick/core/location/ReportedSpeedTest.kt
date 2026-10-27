package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.MockMode
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportedSpeedTest {
    private fun newService(): MockLocationService =
        MockLocationService().apply {
            locationRepository = LocationRepository()
            altitudeAnchor =
                AltitudeAnchorCoordinator(
                    elevationRepository = mockk(relaxed = true),
                    settingsRepository = mockk(relaxed = true),
                    locationRepository = locationRepository,
                )
        }

    @Test
    fun `walk-to speed reaches reportedSpeedMps on publish`() {
        val service = newService()
        service.locationRepository.startSpoofing()
        service.locationRepository.setMockMode(MockMode.WALK_TO)
        service.updatePositionWithVector(1.0, 2.0, speedMs = 3.5f, bearing = 90f)

        service.publishReportedSpeed(service.captureSnapshot(nowMs = 0L).speedMs)

        assertEquals(3.5f, service.locationRepository.reportedSpeedMps.value)
    }

    @Test
    fun `paused spoofing publishes zero speed`() {
        val service = newService()
        service.locationRepository.startSpoofing()
        service.locationRepository.setMockMode(MockMode.WALK_TO)
        service.updatePositionWithVector(1.0, 2.0, speedMs = 3.5f, bearing = 90f)
        service.locationRepository.pauseSpoofing()

        service.publishReportedSpeed(service.captureSnapshot(nowMs = 0L).speedMs)

        assertEquals(0f, service.locationRepository.reportedSpeedMps.value)
    }

    @Test
    fun `roaming input alone does not change reportedSpeedMps`() {
        val service = newService()
        service.locationRepository.startSpoofing()

        service.locationRepository.setSpeedInternal(9f)

        assertEquals(null, service.locationRepository.reportedSpeedMps.value)
    }
}
