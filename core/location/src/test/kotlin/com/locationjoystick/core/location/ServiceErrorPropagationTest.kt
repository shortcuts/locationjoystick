package com.locationjoystick.core.location

import android.location.LocationManager
import android.os.PowerManager
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceErrorPropagationTest {
    private val wakeLock = mockk<PowerManager.WakeLock>(relaxed = true) { every { isHeld } returns true }
    private val powerManager =
        mockk<PowerManager> { every { newWakeLock(any(), any()) } returns wakeLock }
    private val locationManager = mockk<LocationManager>(relaxed = true)
    private val locationRepository = LocationRepository()
    private val holder = SpoofingWakeLockHolder { powerManager }
    private var state = MockLocationState.IDLE
    private val walk = mockk<WalkCoordinator>(relaxed = true)
    private val orchestrator = mockk<ReplayOrchestrator>(relaxed = true)
    private val roaming = mockk<RoamingRepository>(relaxed = true)
    private val handler =
        SpoofingErrorHandler(
            setState = { state = it },
            locationRepository = locationRepository,
            wakeLock = holder,
            walkCoordinator = walk,
            replayOrchestrator = orchestrator,
            roamingRepository = roaming,
        )
    private val provider = TestProviderController(locationManager, onError = handler::setError)

    private val fix =
        LocationFix(1.0, 2.0, 3.0, 0f, 0f, false, 5f, 3f, 0f, 1f, null, null)

    @Test
    fun `addTestProvider SecurityException reaches the repository and releases the wake lock`() {
        every { locationManager.addTestProvider(any(), any<android.location.provider.ProviderProperties>()) } throws SecurityException()
        every { locationManager.addTestProvider(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            SecurityException()
        holder.acquire()

        provider.setup()

        assertEquals(MockLocationState.ERROR, state)
        assertEquals(MockLocationState.ERROR, locationRepository.mockLocationState.value)
        verify(exactly = 1) { wakeLock.release() }
    }

    @Test
    fun `revoked mock app on a tick reaches ERROR and a retry re-registers`() {
        provider.setup()
        every { locationManager.setTestProviderLocation(any(), any()) } throws SecurityException()

        provider.applyFix(fix, 1L)

        assertEquals(MockLocationState.ERROR, state)
        assertEquals(MockLocationState.ERROR, locationRepository.mockLocationState.value)
        provider.setup()
        verify(exactly = 2) { locationManager.setTestProviderEnabled(any(), true) }
    }

    @Test
    fun `ERROR cancels walk, replay and roaming and resets mode to TELEPORT`() =
        runBlocking {
            locationRepository.setMockMode(MockMode.WALK_TO)

            handler.stopMovementOnError()

            verify { walk.cancel() }
            coVerify { orchestrator.handleCancel() }
            coVerify { roaming.stopRoaming() }
            assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
        }
}
