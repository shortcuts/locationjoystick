package com.locationjoystick.core.routing

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoamingEngineStateTest {
    private val first = LatLng(0.0, 0.0)
    private val last = LatLng(0.01, 0.0)
    private val steered = LatLng(0.0, 0.001)
    private val interval = AppConstants.LocationConstants.UPDATE_INTERVAL_MS
    private val maxStepMeters = 1.4 * interval / 1000.0 * 1.5

    private fun config(): RoamingConfig =
        RoamingConfig(
            centerPosition = first,
            radiusMeters = 100.0,
            distanceMeters = 100.0,
            returnToInitialLocation = false,
            plannedWaypoints = listOf(first, last),
        )

    private fun engine(scope: kotlinx.coroutines.test.TestScope): RoamingEngine =
        RoamingEngine(mockk(), RouteInterpolator(), mockk(relaxed = true), StandardTestDispatcher(scope.testScheduler))

    private fun metersBetween(
        a: LatLng,
        b: LatLng,
    ): Double = kotlin.math.hypot(a.latitude - b.latitude, a.longitude - b.longitude) * 111_320.0

    @Test
    fun `pause while idle is ignored by the next start`() =
        runTest {
            val engine = engine(this)
            val positions = mutableListOf<LatLng>()
            try {
                engine.pauseRoaming()
                engine.startRoaming(config(), 1.4, onPositionUpdate = positions::add)
                runCurrent()
                advanceTimeBy(interval)
                runCurrent()

                assertTrue("a pause before any start must not block the start", positions.isNotEmpty())
            } finally {
                engine.close()
            }
        }

    @Test
    fun `resume with a point while running moves from that point without a pause`() =
        runTest {
            val engine = engine(this)
            val positions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4, onPositionUpdate = positions::add)
                runCurrent()
                positions.clear()

                engine.resumeRoaming(from = steered)
                advanceTimeBy(interval)
                runCurrent()

                assertTrue("first tick moved too far from steered point", metersBetween(positions.first(), steered) <= maxStepMeters)
            } finally {
                engine.close()
            }
        }

    @Test
    fun `pause after a resume with a point drops that point`() =
        runTest {
            val engine = engine(this)
            val positions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4, onPositionUpdate = positions::add)
                runCurrent()
                engine.pauseRoaming()
                engine.resumeRoaming(from = steered)
                engine.pauseRoaming()
                val before = positions.last()
                positions.clear()
                advanceTimeBy(3 * interval)
                runCurrent()

                assertTrue("a paused engine must not move", positions.isEmpty())

                engine.resumeRoaming()
                advanceTimeBy(interval)
                runCurrent()

                assertTrue(
                    "resume without a point must continue from the last position",
                    metersBetween(positions.first(), before) <= maxStepMeters,
                )
            } finally {
                engine.close()
            }
        }

    @Test
    fun `pause while paused keeps the engine paused`() =
        runTest {
            val engine = engine(this)
            val positions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4, onPositionUpdate = positions::add)
                runCurrent()
                engine.pauseRoaming()
                engine.pauseRoaming()
                positions.clear()
                advanceTimeBy(2 * interval)
                runCurrent()

                assertEquals(0, positions.size)
            } finally {
                engine.close()
            }
        }

    @Test
    fun `stop while paused leaves the engine idle`() =
        runTest {
            val engine = engine(this)
            val oldPositions = mutableListOf<LatLng>()
            val newPositions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4, onPositionUpdate = oldPositions::add)
                runCurrent()
                engine.pauseRoaming()
                engine.stop()
                val oldCount = oldPositions.size
                advanceTimeBy(2 * interval)
                runCurrent()

                assertEquals("a stopped session must not move", oldCount, oldPositions.size)

                engine.startRoaming(config(), 1.4, onPositionUpdate = newPositions::add)
                runCurrent()

                assertTrue("a start after stop must move", newPositions.isNotEmpty())
            } finally {
                engine.close()
            }
        }

    @Test
    fun `stopRoaming while running ends movement`() =
        runTest {
            val engine = engine(this)
            val positions = mutableListOf<LatLng>()
            try {
                engine.startRoaming(config(), 1.4, onPositionUpdate = positions::add)
                runCurrent()
                engine.stopRoaming()
                val count = positions.size
                advanceTimeBy(2 * interval)
                runCurrent()

                assertEquals(count, positions.size)
            } finally {
                engine.close()
            }
        }
}
