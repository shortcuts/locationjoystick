package com.locationjoystick.core.routing

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoamingEnginePauseStartTest {
    private val first = LatLng(0.0, 0.0)
    private val last = LatLng(0.01, 0.0)
    private val interval = AppConstants.LocationConstants.UPDATE_INTERVAL_MS

    private fun config(): RoamingConfig =
        RoamingConfig(
            centerPosition = first,
            radiusMeters = 100.0,
            distanceMeters = 100.0,
            returnToInitialLocation = false,
            plannedWaypoints = listOf(first, last),
        )

    @Test
    fun `pause after start but before its coroutine runs stays paused until resume`() =
        runTest {
            val engine = RoamingEngine(mockk(), RouteInterpolator(), mockk(relaxed = true), StandardTestDispatcher(testScheduler))
            val positions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4, onPositionUpdate = positions::add)
                engine.pauseRoaming()
                runCurrent()
                advanceTimeBy(3 * interval)
                runCurrent()

                assertTrue("queued startup must not undo the pause", positions.isEmpty())

                engine.resumeRoaming()
                advanceTimeBy(interval)
                runCurrent()
                assertTrue("explicit resume must start movement", positions.isNotEmpty())
            } finally {
                engine.close()
            }
        }

    @Test
    fun `pause while road planning stays paused when the result arrives`() =
        runTest {
            val roads: OsrmClient = mockk()
            val planned = CompletableDeferred<Result<OsrmRouteResult>>()
            coEvery { roads.getRouteWithDistance(any(), any()) } coAnswers { planned.await() }
            val engine = RoamingEngine(roads, RouteInterpolator(), mockk(relaxed = true), StandardTestDispatcher(testScheduler))
            val positions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config().copy(plannedWaypoints = null, useRoadSnapping = true), 1.4, onPositionUpdate = positions::add)
                runCurrent()
                engine.pauseRoaming()
                planned.complete(Result.success(OsrmRouteResult(listOf(first, last), 100.0)))
                runCurrent()
                advanceTimeBy(2 * interval)
                runCurrent()

                assertTrue("a road result must not silently resume movement", positions.isEmpty())

                engine.resumeRoaming()
                advanceTimeBy(interval)
                runCurrent()
                assertTrue(positions.isNotEmpty())
            } finally {
                engine.close()
            }
        }

    @Test
    fun `new start clears the old pause without releasing old ticks`() =
        runTest {
            val engine = RoamingEngine(mockk(), RouteInterpolator(), mockk(relaxed = true), StandardTestDispatcher(testScheduler))
            val oldPositions = mutableListOf<LatLng>()
            val newPositions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4, onPositionUpdate = oldPositions::add)
                runCurrent()
                engine.pauseRoaming()
                val oldCount = oldPositions.size
                // Put the old delayed tick at the front of the queue for the new start.
                advanceTimeBy(interval)
                engine.startRoaming(config(), 1.4, onPositionUpdate = newPositions::add)
                runCurrent()

                assertEquals("resetting the pause must not revive the replaced job", oldCount, oldPositions.size)
                assertTrue("a new start should not inherit the old pause", newPositions.isNotEmpty())
            } finally {
                engine.close()
            }
        }

    @Test
    fun `a pause after replacement start survives joining the previous job`() =
        runTest {
            val engine = RoamingEngine(mockk(), RouteInterpolator(), mockk(relaxed = true), StandardTestDispatcher(testScheduler))
            val positions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4) {}
                runCurrent()
                engine.startRoaming(config(), 1.4, onPositionUpdate = positions::add)
                engine.pauseRoaming()
                runCurrent()
                advanceTimeBy(2 * interval)
                runCurrent()

                assertTrue("cleanup of the replaced job must not overwrite the new pause", positions.isEmpty())

                engine.resumeRoaming()
                advanceTimeBy(interval)
                runCurrent()
                assertTrue(positions.isNotEmpty())
            } finally {
                engine.close()
            }
        }

    @Test
    fun `resume from a moved position walks instead of snapping`() =
        runTest {
            val engine = RoamingEngine(mockk(), RouteInterpolator(), mockk(relaxed = true), StandardTestDispatcher(testScheduler))
            val positions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4, onPositionUpdate = positions::add)
                runCurrent()
                engine.pauseRoaming()
                advanceTimeBy(3 * interval)
                runCurrent()
                positions.clear()

                val steered = LatLng(0.0, 0.001)
                engine.resumeRoaming(from = steered)
                advanceTimeBy(interval)
                runCurrent()

                val moved =
                    kotlin.math.hypot(positions.first().latitude - steered.latitude, positions.first().longitude - steered.longitude) *
                        111_320.0
                assertTrue("first tick moved $moved m from steered position", moved <= 1.4 * interval / 1000.0 * 1.5)
            } finally {
                engine.close()
            }
        }
}
