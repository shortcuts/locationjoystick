package com.locationjoystick.core.data

import app.cash.turbine.test
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.MovementState
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.isPaused
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationRepositoryTest {
    private val repository = LocationRepository()

    @Test
    fun `setPositionInternal emits new position via observePosition`() =
        runTest {
            val position = LatLng(10.0, 20.0)

            repository.observePosition().test {
                // consume initial null emission
                awaitItem()

                repository.setPositionInternal(position)

                assertEquals(position, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setPositionInternal preserves exact coordinates`() =
        runTest {
            val position = LatLng(48.8566, 2.3522)

            repository.observePosition().test {
                awaitItem() // initial
                repository.setPositionInternal(position)
                val emitted = awaitItem()
                org.junit.Assert.assertNotNull(emitted)

                assertEquals(position.latitude, emitted!!.latitude, 0.00001)
                assertEquals(position.longitude, emitted.longitude, 0.00001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setPositionInternal multiple updates emit all values`() =
        runTest {
            val pos1 = LatLng(0.0, 0.0)
            val pos2 = LatLng(1.0, 1.0)
            val pos3 = LatLng(2.0, 2.0)

            repository.observePosition().test {
                awaitItem() // initial
                repository.setPositionInternal(pos1)
                assertEquals(pos1, awaitItem())

                repository.setPositionInternal(pos2)
                assertEquals(pos2, awaitItem())

                repository.setPositionInternal(pos3)
                assertEquals(pos3, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setPositionInternal different positions are distinct`() =
        runTest {
            val pos1 = LatLng(0.0, 0.0)
            val pos2 = LatLng(0.00001, 0.0) // ~1.1m away

            repository.observePosition().test {
                awaitItem()
                repository.setPositionInternal(pos1)
                val first = awaitItem()

                repository.setPositionInternal(pos2)
                val second = awaitItem()

                assertEquals(first, pos1)
                assertEquals(second, pos2)
                assertTrue("should be different positions", first != second)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setBearingInternal

    @Test
    fun `setBearingInternal emits new bearing via currentBearing`() =
        runTest {
            repository.currentBearing.test {
                assertNull(awaitItem())

                repository.setBearingInternal(90.0f)

                assertEquals(90.0f, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setSpeedInternal emits new speed via currentSpeedMps`() =
        runTest {
            repository.currentSpeedMps.test {
                assertNull(awaitItem())

                repository.setSpeedInternal(1.4f)

                assertEquals(1.4f, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setReportedSpeed

    @Test
    fun `setReportedSpeed emits new speed via reportedSpeedMps`() =
        runTest {
            repository.reportedSpeedMps.test {
                assertNull(awaitItem())

                repository.setReportedSpeed(2.5f)

                assertEquals(2.5f, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stopSpoofing zeroes reportedSpeedMps`() {
        repository.setReportedSpeed(2f)

        repository.stopSpoofing()

        assertEquals(0f, repository.reportedSpeedMps.value)
    }

    // setReportedAltitude

    @Test
    fun `setReportedAltitude emits new altitude via reportedAltitudeMeters`() =
        runTest {
            repository.reportedAltitudeMeters.test {
                assertNull(awaitItem())

                repository.setReportedAltitude(123.4)

                assertEquals(123.4, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // updatePosition

    @Test
    fun `updatePosition with lat and lon sets position`() =
        runTest {
            repository.observePosition().test {
                awaitItem() // initial null
                repository.updatePosition(48.8566, 2.3522)
                val emitted = awaitItem()
                assertNotNull(emitted)
                assertEquals(48.8566, emitted!!.latitude, 0.00001)
                assertEquals(2.3522, emitted.longitude, 0.00001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updatePosition with LatLng sets position`() =
        runTest {
            val pos = LatLng(51.5, -0.1)
            repository.observePosition().test {
                awaitItem()
                repository.updatePosition(pos)
                assertEquals(pos, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // startSpoofing

    @Test
    fun `startSpoofing with null position sets default`() =
        runTest {
            repository.observeState().test {
                assertEquals(MockLocationState.IDLE, awaitItem())

                repository.startSpoofing()

                assertEquals(MockLocationState.RUNNING, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `startSpoofing with null position sets default coordinates`() =
        runTest {
            repository.observePosition().test {
                awaitItem() // initial null

                repository.startSpoofing()

                val pos = awaitItem()
                assertNotNull(pos)
                assertEquals(AppConstants.MapConstants.DEFAULT_LAT, pos!!.latitude, 0.00001)
                assertEquals(AppConstants.MapConstants.DEFAULT_LON, pos.longitude, 0.00001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `startSpoofing with existing position does not overwrite`() =
        runTest {
            val existing = LatLng(10.0, 20.0)
            repository.setPositionInternal(existing)

            repository.observePosition().test {
                assertEquals(existing, awaitItem())

                repository.startSpoofing()

                // Position should remain unchanged (no new emission since position wasn't modified)
                // Just verify the current value is still the existing position
                assertEquals(existing, repository.currentPosition.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // stopSpoofing

    @Test
    fun `stopSpoofing sets state to IDLE`() =
        runTest {
            repository.startSpoofing()

            repository.observeState().test {
                assertEquals(MockLocationState.RUNNING, awaitItem())

                repository.stopSpoofing()

                assertEquals(MockLocationState.IDLE, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stopSpoofing is idempotent when called multiple times`() =
        runTest {
            repository.startSpoofing()

            // Double-call mirrors the onDestroy + onComplete race fixed in MockLocationService.
            repository.stopSpoofing()
            repository.stopSpoofing()

            assertEquals(MockLocationState.IDLE, repository.mockLocationState.value)
        }

    @Test
    fun `stopSpoofing can be called without a coroutine scope`() {
        // Regression guard: stopSpoofing must NOT be suspend. If re-added as suspend,
        // the serviceScope.cancel race in MockLocationService.onDestroy() leaves
        // mockLocationState stuck at RUNNING after service destruction.
        val repo = LocationRepository()
        repo.stopSpoofing() // direct synchronous call — would not compile if suspend
        assertEquals(MockLocationState.IDLE, repo.mockLocationState.value)
    }

    // pauseSpoofing

    @Test
    fun `pauseSpoofing sets state to PAUSED`() =
        runTest {
            repository.startSpoofing()

            repository.observeState().test {
                assertEquals(MockLocationState.RUNNING, awaitItem())

                repository.pauseSpoofing()

                assertEquals(MockLocationState.PAUSED, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `pauseSpoofing from IDLE is rejected`() {
        repository.pauseSpoofing()

        assertEquals(MockLocationState.IDLE, repository.mockLocationState.value)
    }

    @Test
    fun `pauseSpoofing in ERROR keeps ERROR`() {
        repository.sendSpoofingEvent(SpoofingEvent.FAIL)

        repository.pauseSpoofing()

        assertEquals(MockLocationState.ERROR, repository.mockLocationState.value)
    }

    @Test
    fun `START sets the default position when none is set`() {
        assertTrue(repository.sendSpoofingEvent(SpoofingEvent.START))

        assertEquals(
            LatLng(AppConstants.MapConstants.DEFAULT_LAT, AppConstants.MapConstants.DEFAULT_LON),
            repository.currentPosition.value,
        )
    }

    @Test
    fun `STOP zeroes the reported speed`() {
        repository.setReportedSpeed(3f)

        repository.sendSpoofingEvent(SpoofingEvent.STOP)

        assertEquals(0f, repository.reportedSpeedMps.value)
    }

    // sendSpoofingEvent FAIL

    @Test
    fun `reportError sets state to ERROR`() =
        runTest {
            repository.startSpoofing()

            repository.observeState().test {
                assertEquals(MockLocationState.RUNNING, awaitItem())

                repository.sendSpoofingEvent(SpoofingEvent.FAIL)

                assertEquals(MockLocationState.ERROR, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setWalkTarget

    @Test
    fun `setWalkTarget emits walk target`() =
        runTest {
            val target = LatLng(48.8566, 2.3522)

            repository.walkTarget.test {
                assertNull(awaitItem())

                repository.setWalkTarget(target)

                assertEquals(target, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setWalkTarget with null clears walk target and resets pause`() =
        runTest {
            val target = LatLng(48.8566, 2.3522)
            repository.setWalkTarget(target)
            repository.pauseMovement()

            repository.setWalkTarget(null)

            assertNull(repository.walkTarget.value)
            assertEquals(MovementState.Manual(MockMode.TELEPORT), repository.currentMovement())
        }

    // movement transitions

    @Test
    fun `pauseMovement pauses walk and roam`() {
        listOf(MockMode.WALK_TO, MockMode.ROAMING).forEach {
            repository.setMockMode(it)
            repository.pauseMovement()
            assertEquals(MovementState.Active(it, true), repository.currentMovement())
        }
    }

    @Test
    fun `resumeMovement resumes a paused walk and roam`() {
        listOf(MockMode.WALK_TO, MockMode.ROAMING).forEach {
            repository.setMockMode(it)
            repository.pauseMovement()
            repository.resumeMovement()
            assertEquals(MovementState.Active(it, false), repository.currentMovement())
        }
    }

    @Test
    fun `pauseMovement is rejected in manual modes and leaves state unchanged`() {
        listOf(MockMode.TELEPORT, MockMode.JOYSTICK, MockMode.FOLLOWER).forEach {
            repository.setMockMode(it)
            repository.pauseMovement()
            assertEquals(MovementState.Manual(it), repository.currentMovement())
        }
    }

    @Test
    fun `pauseMovement is rejected for route replay`() {
        repository.setMockMode(MockMode.ROUTE_REPLAY)
        repository.pauseMovement()
        assertEquals(MovementState.Active(MockMode.ROUTE_REPLAY, false), repository.currentMovement())
    }

    @Test
    fun `resumeMovement is rejected when not paused`() {
        repository.setMockMode(MockMode.WALK_TO)
        repository.resumeMovement()
        assertEquals(MovementState.Active(MockMode.WALK_TO, false), repository.currentMovement())
    }

    @Test
    fun `setMockMode resets pause`() {
        repository.setMockMode(MockMode.ROAMING)
        repository.pauseMovement()
        repository.setMockMode(MockMode.ROAMING)
        assertEquals(MovementState.Active(MockMode.ROAMING, false), repository.currentMovement())
    }

    @Test
    fun `route replay pause follows mockLocationState`() =
        runTest {
            repository.startSpoofing()
            repository.pauseSpoofing()
            repository.setMockMode(MockMode.ROUTE_REPLAY)
            assertEquals(MovementState.Active(MockMode.ROUTE_REPLAY, true), repository.movement.first())

            repository.startSpoofing()
            assertEquals(MovementState.Active(MockMode.ROUTE_REPLAY, false), repository.movement.first())
        }

    // setMockMode

    @Test
    fun `setMockMode emits current mode`() =
        runTest {
            repository.currentMode.test {
                // Initial value is TELEPORT
                assertEquals(MockMode.TELEPORT, awaitItem())

                repository.setMockMode(MockMode.JOYSTICK)

                assertEquals(MockMode.JOYSTICK, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setMockMode route replay mode`() =
        runTest {
            repository.setMockMode(MockMode.ROUTE_REPLAY)

            repository.currentMode.test {
                assertEquals(MockMode.ROUTE_REPLAY, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setMockMode roaming mode`() =
        runTest {
            repository.setMockMode(MockMode.ROAMING)

            repository.currentMode.test {
                assertEquals(MockMode.ROAMING, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setActiveRouteId

    @Test
    fun `setActiveRouteId emits route id`() =
        runTest {
            repository.activeRouteId.test {
                assertNull(awaitItem())

                repository.setActiveRouteId("route-123")

                assertEquals("route-123", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setActiveRouteId with null clears route id`() =
        runTest {
            repository.setActiveRouteId("route-123")

            repository.activeRouteId.test {
                assertEquals("route-123", awaitItem())

                repository.setActiveRouteId(null)

                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setIsReplayBackward

    @Test
    fun `setIsReplayBackward emits backward state`() =
        runTest {
            repository.isReplayBackward.test {
                assertEquals(false, awaitItem())

                repository.setIsReplayBackward(true)

                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setRouteWaypoints

    @Test
    fun `setRouteWaypoints emits waypoints`() =
        runTest {
            val waypoints = listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))

            repository.routeWaypoints.test {
                assertNull(awaitItem())

                repository.setRouteWaypoints(waypoints)

                assertEquals(waypoints, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRouteWaypoints with null clears waypoints`() =
        runTest {
            val waypoints = listOf(LatLng(0.0, 0.0))
            repository.setRouteWaypoints(waypoints)

            repository.routeWaypoints.test {
                assertEquals(waypoints, awaitItem())

                repository.setRouteWaypoints(null)

                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setRouteProgress

    @Test
    fun `setRouteProgress emits progress`() =
        runTest {
            val progress = RouteProgress(current = 2, total = 10)

            repository.routeProgress.test {
                assertNull(awaitItem())

                repository.setRouteProgress(progress)

                assertEquals(progress, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRouteProgress with null clears progress`() =
        runTest {
            repository.setRouteProgress(RouteProgress(1, 5))

            repository.routeProgress.test {
                assertEquals(RouteProgress(1, 5), awaitItem())

                repository.setRouteProgress(null)

                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // observeState

    @Test
    fun `observeState emits initial IDLE state`() =
        runTest {
            repository.observeState().test {
                assertEquals(MockLocationState.IDLE, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeState emits all state transitions`() =
        runTest {
            repository.observeState().test {
                assertEquals(MockLocationState.IDLE, awaitItem())

                repository.startSpoofing()
                assertEquals(MockLocationState.RUNNING, awaitItem())

                repository.pauseSpoofing()
                assertEquals(MockLocationState.PAUSED, awaitItem())

                repository.startSpoofing()
                assertEquals(MockLocationState.RUNNING, awaitItem())

                repository.sendSpoofingEvent(SpoofingEvent.FAIL)
                assertEquals(MockLocationState.ERROR, awaitItem())

                repository.stopSpoofing()
                assertEquals(MockLocationState.IDLE, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setWalkTarget non-null sets mode to WALK_TO`() =
        runTest {
            repository.setWalkTarget(LatLng(48.0, 2.0))
            assertEquals(MockMode.WALK_TO, repository.currentMode.value)
        }

    @Test
    fun `setWalkTarget null resets mode to TELEPORT and clears pause`() =
        runTest {
            repository.setWalkTarget(LatLng(1.0, 2.0))
            repository.pauseMovement()
            repository.setWalkTarget(null)
            assertEquals(MockMode.TELEPORT, repository.currentMode.value)
            assertFalse(repository.currentMovement().isPaused)
        }

    @Test
    fun `setWalkTarget null does not clobber non-WALK_TO mode`() =
        runTest {
            // Simulate: route replay was started and somehow walkTarget is being cleared
            repository.setMockMode(MockMode.ROUTE_REPLAY)
            // Clearing a null target that was never set should not change mode
            repository.setWalkTarget(null)
            assertEquals(MockMode.ROUTE_REPLAY, repository.currentMode.value)
        }

    @Test
    fun `beginRoadRouteFetch moves idle to fetching and end returns to idle`() {
        assertEquals(RoadRouteFetch.Idle, repository.roadRouteFetch.value)
        repository.beginRoadRouteFetch()
        assertEquals(RoadRouteFetch.ReplayFetch, repository.roadRouteFetch.value)
        assertTrue(repository.isRoadRouteFetchInFlight.value)
        repository.endRoadRouteFetch()
        assertEquals(RoadRouteFetch.Idle, repository.roadRouteFetch.value)
        assertFalse(repository.isRoadRouteFetchInFlight.value)
    }

    @Test
    fun `cancelWalkRoadRouteFetch cancels the walk job and goes idle`() {
        val job = Job()
        repository.beginRoadRouteFetch(job)
        assertEquals(RoadRouteFetch.WalkFetch(job), repository.roadRouteFetch.value)
        repository.cancelWalkRoadRouteFetch()
        assertTrue(job.isCancelled)
        assertEquals(RoadRouteFetch.Idle, repository.roadRouteFetch.value)
    }

    @Test
    fun `cancelWalkRoadRouteFetch is rejected for a replay fetch`() {
        repository.beginRoadRouteFetch()
        repository.cancelWalkRoadRouteFetch()
        assertEquals(RoadRouteFetch.ReplayFetch, repository.roadRouteFetch.value)
    }

    @Test
    fun `endRoadRouteFetch keeps a fetch owned by another job`() {
        val job = Job()
        repository.beginRoadRouteFetch(job)
        repository.endRoadRouteFetch()
        assertEquals(RoadRouteFetch.WalkFetch(job), repository.roadRouteFetch.value)
        repository.endRoadRouteFetch(job)
        assertEquals(RoadRouteFetch.Idle, repository.roadRouteFetch.value)
    }

    @Test
    fun `endRoadRouteFetch with a job keeps a replay fetch`() {
        repository.beginRoadRouteFetch()
        repository.endRoadRouteFetch(Job())
        assertEquals(RoadRouteFetch.ReplayFetch, repository.roadRouteFetch.value)
    }
}
