package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.data.WalkToEngine
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoutingErrorReporter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EphemeralReplayControllerTest {
    private val tick = AppConstants.LocationConstants.UPDATE_INTERVAL_MS
    private val a = LatLng(48.8566, 2.3522)
    private val b = LatLng(48.8576, 2.3522)
    private val far = LatLng(48.9000, 2.3522)

    private val context = mockk<Context>(relaxed = true)
    private val osrm = mockk<OsrmClient>()
    private val gate = CompletableDeferred<Unit>()
    private val routeFroms = mutableListOf<LatLng>()
    private val replays = mutableListOf<List<LatLng>>()
    private lateinit var locationRepository: LocationRepository
    private lateinit var walkCoordinator: WalkCoordinator
    private lateinit var controller: EphemeralReplayController

    @Before
    fun setUp() {
        mockkObject(MockLocationIntentBuilder)
        every { MockLocationIntentBuilder.startEphemeralReplay(any(), any(), any()) } answers {
            replays.add(secondArg())
            mockk(relaxed = true)
        }
        every { MockLocationIntentBuilder.appendWaypoint(any(), any()) } returns mockk(relaxed = true)
        coEvery { osrm.resolveRoute(any(), any(), any(), any(), any()) } coAnswers {
            routeFroms.add(secondArg())
            gate.await()
            listOf(secondArg(), thirdArg())
        }
        val settings = mockk<SettingsRepository>()
        every { settings.getActiveSpeedProfile() } returns
            flowOf(SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = AppConstants.ProfileConstants.WALK_SPEED_MPS))
        locationRepository = LocationRepository()
        walkCoordinator = WalkCoordinator(locationRepository, WalkToEngine(settings, locationRepository))
        controller =
            EphemeralReplayController(locationRepository, settings, walkCoordinator, osrm, mockk<RoutingErrorReporter>(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkObject(MockLocationIntentBuilder)
    }

    @Test
    fun `replay starts where the walk stopped, not where it was during the lookup`() =
        runTest {
            locationRepository.setPositionInternal(a)
            walkCoordinator.startWalk(far, backgroundScope)
            advanceTimeBy(tick * 2)
            val job = launch { controller.addWaypoint(b, far, false, context) {} }
            runCurrent()
            advanceTimeBy(tick * 5)
            gate.complete(Unit)
            job.join()

            assertEquals(locationRepository.currentPosition.value, replays.single().first())
        }

    @Test
    fun `two concurrent taps chain the second leg from the first and start one replay`() =
        runTest {
            locationRepository.setPositionInternal(a)
            walkCoordinator.startWalk(far, backgroundScope)
            val first = LatLng(48.87, 2.36)
            val second = LatLng(48.88, 2.37)
            val j1 = launch { controller.addWaypoint(first, far, true, context) {} }
            val j2 = launch { controller.addWaypoint(second, far, true, context) {} }
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            j1.join()
            j2.join()

            assertEquals(1, replays.size)
            assertEquals(first, routeFroms.last())
            assertEquals(listOf(a, far, first, second), controller.pendingWaypoints.value)
        }

    @Test
    fun `first leg of a via-roads walk restarts from the pass the walk is on`() =
        runTest {
            gate.complete(Unit)
            val route = listOf(a, b, a, b)
            locationRepository.setPositionInternal(a)
            locationRepository.setRouteWaypoints(route)
            walkCoordinator.startWalkAlongRoute(route, backgroundScope)
            while (walkCoordinator.walkWaypointIndex() < 3) advanceTimeBy(tick)
            advanceTimeBy(tick * 10)

            controller.addWaypoint(far, b, false, context) {}

            val position = locationRepository.currentPosition.value!!
            assertEquals(listOf(position, b, far), replays.single())
        }
}
