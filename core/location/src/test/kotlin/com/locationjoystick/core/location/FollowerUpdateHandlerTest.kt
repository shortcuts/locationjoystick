package com.locationjoystick.core.location

import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowerUpdateHandlerTest {
    private val catchUp = FollowerCatchUpCoordinator()
    private val groupRepository = mockk<GroupRepository>(relaxed = true)
    private val locationRepository = mockk<LocationRepository>(relaxed = true)
    private val started = mutableListOf<Pair<Double, Double>>()
    private val snapped = mutableListOf<LatLng>()
    private var pauses = 0
    private var follow = true
    private var hide = false

    private fun handler(state: MockLocationState) =
        FollowerUpdateHandler(
            scope = CoroutineScope(Dispatchers.Unconfined),
            followerCatchUp = catchUp,
            groupRepository = groupRepository,
            settingsRepository = mockk<SettingsRepository>(relaxed = true),
            locationRepository = locationRepository,
            serviceState = { state },
            followLeaderTeleports = { follow },
            hideTeleportFeatures = { hide },
            currentPosition = { LatLng(9.0, 9.0) },
            startSpoofing = { lat, lon -> started += lat to lon },
            snapToLeader = { snapped += it },
            pauseForInactiveLeader = { pauses++ },
        )

    private fun update(
        active: Boolean,
        teleportSeq: Long = 0,
    ) = FollowerPositionUpdate(lat = 1.0, lon = 2.0, speedMs = 1f, bearing = 0f, active = active, teleportSeq = teleportSeq)

    @Test
    fun `active update bootstraps spoofing and sets follower mode`() {
        handler(MockLocationState.IDLE).onUpdate(update(active = true))

        assertEquals(listOf(1.0 to 2.0), started)
        verify { locationRepository.setMockMode(MockMode.FOLLOWER) }
        assertEquals(LatLng(1.0, 2.0), catchUp.currentTarget())
    }

    @Test
    fun `inactive update after bootstrap clears target and pauses`() {
        handler(MockLocationState.IDLE).onUpdate(update(active = true))
        handler(MockLocationState.RUNNING).onUpdate(update(active = false))

        assertNull(catchUp.currentTarget())
        assertEquals(1, pauses)
        verify { groupRepository.setLeaderPosition(null) }
    }

    @Test
    fun `leader teleport snaps when following teleports`() {
        val h = handler(MockLocationState.RUNNING)
        h.onUpdate(update(active = true, teleportSeq = 1))
        h.onUpdate(update(active = true, teleportSeq = 2))

        assertEquals(listOf(LatLng(1.0, 2.0)), snapped)
    }

    @Test
    fun `leader teleport does not snap when teleports are hidden`() {
        hide = true
        val h = handler(MockLocationState.RUNNING)
        h.onUpdate(update(active = true, teleportSeq = 1))
        h.onUpdate(update(active = true, teleportSeq = 2))

        assertEquals(emptyList<LatLng>(), snapped)
    }
}
