package com.locationjoystick.core.location

import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.MockLocationState
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

/** Leaving a group while idle must end the service the group kept alive; leaving while spoofing must not. */
class LeaveGroupStopsIdleServiceTest {
    private fun newService(state: MockLocationState): MockLocationService {
        val service =
            spyk(MockLocationService()).apply {
                locationRepository = LocationRepository()
                leaderSyncServer = mockk(relaxed = true)
                groupNsdManager = mockk(relaxed = true)
                followerSyncClient = mockk(relaxed = true)
                groupRepository = mockk(relaxed = true)
            }

        @Suppress("UNCHECKED_CAST")
        val field = MockLocationService::class.java.getDeclaredField("_state").apply { isAccessible = true }
        (field.get(service) as MutableStateFlow<MockLocationState>).value = state
        return service
    }

    @Test
    fun `exit leader while idle stops the service`() {
        val service = newService(MockLocationState.IDLE)
        service.exitLeaderMode()
        verify { service.stopSelf() }
    }

    @Test
    fun `exit follower while idle stops the service`() {
        val service = newService(MockLocationState.IDLE)
        service.exitFollowerMode()
        verify { service.stopSelf() }
    }

    @Test
    fun `leaving while spoofing keeps spoofing`() {
        val service = newService(MockLocationState.RUNNING)
        service.exitLeaderMode()
        service.exitFollowerMode()
        verify(exactly = 0) { service.stopSelf() }
    }
}
