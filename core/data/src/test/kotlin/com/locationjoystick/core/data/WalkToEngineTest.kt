package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.SpeedProfile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalkToEngineTest {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var engine: WalkToEngine

    // A walk profile fast enough to cover test distances in one tick
    private val walkProfile =
        SpeedProfile(
            id = "walk",
            name = "Walk",
            speedMetersPerSecond = AppConstants.ProfileConstants.WALK_SPEED_MPS,
        )

    @Before
    fun setUp() {
        settingsRepository = mockk()
        locationRepository = LocationRepository()
        every { settingsRepository.getActiveSpeedProfile() } returns flowOf(walkProfile)
        engine = WalkToEngine(settingsRepository, locationRepository)
    }

    @Test
    fun `speed reported equals actualSpeedMs formula`() =
        runTest {
            // Place target far enough that advanceDistance = profileSpeed * intervalSeconds
            // At WALK_SPEED_MPS over UPDATE_INTERVAL_MS seconds = WALK_SPEED_MPS * 1.0 m
            // We need distance >> advanceDistance so minOf clamps to speed*interval, not distance.
            val current = LatLng(48.8566, 2.3522)
            val target = LatLng(48.9000, 2.3522) // ~4800m away — well beyond one tick advance
            locationRepository.setPositionInternal(current)
            locationRepository.setWalkTarget(target)

            val expectedSpeedMs =
                (
                    AppConstants.ProfileConstants.WALK_SPEED_MPS *
                        (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0)
                ).let {
                    // advanceDistance = minOf(speed*interval, distance) ≈ speed*interval since dist >> advance
                    (it / (AppConstants.LocationConstants.UPDATE_INTERVAL_MS / 1000.0)).toFloat()
                }

            var reportedSpeed: Float? = null
            var callCount = 0

            with(engine) {
                backgroundScope.launchWalkTo(
                    target = target,
                    onPositionUpdate = { newPos, speedMs, _ ->
                        if (callCount == 0) {
                            reportedSpeed = speedMs
                            locationRepository.updatePosition(newPos)
                        }
                        callCount++
                    },
                    onArrival = {},
                )
            }

            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertTrue("onPositionUpdate should have been called", callCount > 0)
            assertEquals(
                "Reported speed should equal profileSpeed",
                expectedSpeedMs,
                reportedSpeed!!,
                0.001f,
            )
        }

    @Test
    fun `bearing reported equals calculateBearing result`() =
        runTest {
            // Target due north: same longitude, higher latitude
            val current = LatLng(48.8566, 2.3522)
            val target = LatLng(49.0000, 2.3522) // due north
            locationRepository.setPositionInternal(current)
            locationRepository.setWalkTarget(target)

            val expectedBearing =
                calculateBearing(
                    current.latitude,
                    current.longitude,
                    target.latitude,
                    target.longitude,
                ).toFloat()

            var reportedBearing: Float? = null
            var callCount = 0

            with(engine) {
                backgroundScope.launchWalkTo(
                    target = target,
                    onPositionUpdate = { newPos, _, bearing ->
                        if (callCount == 0) {
                            reportedBearing = bearing
                            locationRepository.updatePosition(newPos)
                        }
                        callCount++
                    },
                    onArrival = {},
                )
            }

            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertTrue("onPositionUpdate should have been called", callCount > 0)
            assertEquals(
                "Bearing should match calculateBearing result",
                expectedBearing,
                reportedBearing!!,
                0.5f,
            )
        }

    @Test
    fun `paused walk skips position update`() =
        runTest {
            val current = LatLng(48.8566, 2.3522)
            val target = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(current)
            locationRepository.setWalkTarget(target)
            locationRepository.setWalkPaused(true)

            var callCount = 0

            with(engine) {
                backgroundScope.launchWalkTo(
                    target = target,
                    onPositionUpdate = { newPos, _, _ ->
                        callCount++
                        locationRepository.updatePosition(newPos)
                    },
                    onArrival = {},
                )
            }

            // Advance past one full interval — the paused branch should delay and continue
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertFalse("onPositionUpdate should NOT be called while paused", callCount > 0)
        }

    @Test
    fun `launchWalkAlongRoute advances toward first waypoint before reaching it`() =
        runTest {
            val start = LatLng(48.8566, 2.3522)
            val mid = LatLng(48.9000, 2.3522) // ~4800m north — won't snap in one tick
            val end = LatLng(48.9434, 2.3522)
            locationRepository.setPositionInternal(start)
            locationRepository.setWalkTarget(end)

            val positions = mutableListOf<LatLng>()

            with(engine) {
                backgroundScope.launchWalkAlongRoute(
                    waypoints = listOf(start, mid, end),
                    onPositionUpdate = { pos, _, _ ->
                        positions.add(pos)
                        locationRepository.updatePosition(pos)
                    },
                    onArrival = {},
                )
            }

            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertTrue("should produce position updates", positions.isNotEmpty())
            val lat = positions.last().latitude
            assertTrue("should advance north past start", lat > start.latitude)
            assertTrue("should not reach mid in one tick at walk speed", lat < mid.latitude)
        }

    @Test
    fun `launchWalkAlongRoute empty waypoints throws`() =
        runTest {
            var threw = false
            try {
                with(engine) {
                    backgroundScope.launchWalkAlongRoute(
                        waypoints = emptyList(),
                        onPositionUpdate = { _, _, _ -> },
                        onArrival = {},
                    )
                }
            } catch (e: IllegalArgumentException) {
                threw = true
            }
            assertTrue("empty waypoints should throw IllegalArgumentException", threw)
        }

    @Test
    fun `arrives when within threshold`() =
        runTest {
            // Place target within WALK_ARRIVAL_THRESHOLD_METERS of current position
            val current = LatLng(48.8566, 2.3522)
            // 0.5m north — well within 1.0m threshold
            val target = LatLng(48.856604498, 2.3522)
            locationRepository.setPositionInternal(current)
            locationRepository.setWalkTarget(target)

            var arrivalCalled = false

            with(engine) {
                backgroundScope.launchWalkTo(
                    target = target,
                    onPositionUpdate = { newPos, _, _ ->
                        locationRepository.updatePosition(newPos)
                    },
                    onArrival = {
                        arrivalCalled = true
                    },
                )
            }

            // Distance is already < threshold, so arrival fires on first tick check
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertTrue("onArrival should be called when within threshold", arrivalCalled)
            assertNull("Standalone callers retain target cleanup on arrival", locationRepository.walkTarget.value)
        }

    @Test
    fun `standalone cancellation clears its target by default`() =
        runTest {
            val target = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))
            locationRepository.setWalkTarget(target)
            val job =
                with(engine) {
                    backgroundScope.launchWalkAlongRoute(
                        waypoints = listOf(target),
                        onPositionUpdate = { pos, _, _ -> locationRepository.updatePosition(pos) },
                        onArrival = {},
                    )
                }
            runCurrent()

            job.cancel()
            runCurrent()

            assertNull(locationRepository.walkTarget.value)
        }

    @Test
    fun `owner cleanup replaces default target cleanup on cancellation`() =
        runTest {
            val target = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(LatLng(48.8566, 2.3522))
            locationRepository.setWalkTarget(target)
            var finishedCalls = 0
            val job =
                with(engine) {
                    backgroundScope.launchWalkAlongRoute(
                        waypoints = listOf(target),
                        onPositionUpdate = { pos, _, _ -> locationRepository.updatePosition(pos) },
                        onArrival = {},
                        onFinished = { finishedCalls++ },
                    )
                }
            runCurrent()

            job.cancel()
            runCurrent()

            assertEquals(1, finishedCalls)
            assertEquals("Only the session owner may clear this target", target, locationRepository.walkTarget.value)
        }

    @Test
    fun `launchWalkAlongRoute stops when currentPosition is null`() =
        runTest {
            val target = LatLng(48.9000, 2.3522)
            // No current position set — repository.currentPosition.value is null
            locationRepository.setWalkTarget(target)

            var callCount = 0
            with(engine) {
                backgroundScope.launchWalkAlongRoute(
                    waypoints = listOf(target),
                    onPositionUpdate = { _, _, _ -> callCount++ },
                    onArrival = {},
                )
            }

            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS + 1)

            assertEquals("should not call onPositionUpdate with null position", 0, callCount)
        }

    @Test
    fun `onArrival is not invoked once the walk job has been cancelled`() =
        runTest {
            val current = LatLng(48.8566, 2.3522)
            val target = LatLng(48.9000, 2.3522)
            locationRepository.setPositionInternal(current)
            locationRepository.setWalkTarget(target)

            var arrivalCalled = false
            lateinit var job: Job
            with(engine) {
                job =
                    backgroundScope.launchWalkAlongRoute(
                        waypoints = listOf(target),
                        onPositionUpdate = { newPos, _, _ -> locationRepository.updatePosition(newPos) },
                        onArrival = { arrivalCalled = true },
                    )
            }

            job.cancel()
            advanceUntilIdle()

            assertFalse("onArrival should never run once the walk job has been cancelled", arrivalCalled)
        }

    @Test
    fun `walkToOnce continues from a position moved while it ran`() =
        runTest {
            val from = LatLng(48.8566, 2.3522)
            val target = LatLng(48.9000, 2.3522)
            val steered = LatLng(48.8566, 2.3552)
            val ticks = mutableListOf<LatLng>()

            val job =
                backgroundScope.launch {
                    engine.walkToOnce(from, target, 1.4) { pos ->
                        ticks.add(pos)
                        locationRepository.setPositionInternal(if (ticks.size == 1) steered else pos)
                    }
                }
            runCurrent()
            advanceTimeBy(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
            runCurrent()
            job.cancel()

            val moved = haversineDistance(steered, ticks[1])
            assertTrue("second tick moved $moved m from steered position", moved <= 1.4 * 1.5)
        }
}
