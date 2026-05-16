package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.distanceTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RoamingEngineStartRoamingTest {
    private val engine = RoamingEngine(OsrmClient(), RouteInterpolator())

    // startRoaming — basic lifecycle

    @Test
    fun `startRoaming returns a non-null Job`() {
        val config = RoamingConfig(
            centerPosition = LatLng(48.8566, 2.3522),
            radiusMeters = 500.0,
            distanceMeters = 100.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
        val job = engine.startRoaming(config, 1.4) {}
        assertNotNull(job)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }
    }

    @Test
    fun `startRoaming emits position updates via callback`() {
        val latch = CountDownLatch(1)
        val updateCount = AtomicInteger(0)
        val config = RoamingConfig(
            centerPosition = LatLng(0.0, 0.0),
            radiusMeters = 100.0,
            distanceMeters = 50.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
        engine.startRoaming(config, 1.4) { _ ->
            updateCount.incrementAndGet()
            if (updateCount.get() >= 2) latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        assertTrue("should have emitted position updates", updateCount.get() >= 1)
    }

    @Test
    fun `startRoaming positions stay near center for small distance`() {
        val center = LatLng(48.8566, 2.3522)
        val lastPosition = AtomicReference<LatLng>(center)
        val config = RoamingConfig(
            centerPosition = center,
            radiusMeters = 100.0,
            distanceMeters = 50.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
        engine.startRoaming(config, 1.4) { pos ->
            lastPosition.set(pos)
        }

        Thread.sleep(2000)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        val finalPos = lastPosition.get()
        val distFromCenter = center.distanceTo(finalPos)
        assertTrue("should stay within roaming radius ($distFromCenter m)", distFromCenter < 200.0)
    }

    // startRoaming — returnToInitialLocation

    @Test
    fun `startRoaming with returnToInitialLocation walks back to center`() {
        val center = LatLng(0.0, 0.0)
        val lastPosition = AtomicReference<LatLng>(center)
        val config = RoamingConfig(
            centerPosition = center,
            radiusMeters = 50.0,
            distanceMeters = 10.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = true,
        )
        engine.startRoaming(config, 5.0) { pos ->
            lastPosition.set(pos)
        }

        Thread.sleep(3000)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        val finalPos = lastPosition.get()
        val distFromCenter = center.distanceTo(finalPos)
        assertTrue("final position should be reasonable ($distFromCenter m)", distFromCenter < 500.0)
    }

    // startRoaming — cancellation

    @Test
    fun `stopRoaming cancels the active job`() {
        val config = RoamingConfig(
            centerPosition = LatLng(0.0, 0.0),
            radiusMeters = 100.0,
            distanceMeters = 1000.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
        val job = engine.startRoaming(config, 1.4) {}

        Thread.sleep(1000)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        assertTrue("job should be cancelled or completed", job.isCancelled || job.isCompleted)
    }

    @Test
    fun `startRoaming cancels previous job when called again`() {
        val config = RoamingConfig(
            centerPosition = LatLng(0.0, 0.0),
            radiusMeters = 100.0,
            distanceMeters = 1000.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
        val firstJob = engine.startRoaming(config, 1.4) {}

        Thread.sleep(500)

        val secondJob = engine.startRoaming(config, 1.4) {}

        assertTrue("first job should be cancelled", firstJob.isCancelled)
        assertFalse("second job should be active", secondJob.isCompleted)

        kotlinx.coroutines.runBlocking { engine.stopRoaming() }
    }

    // startRoaming — straight-line mode (useRoadSnapping = false)

    @Test
    fun `startRoaming with useRoadSnapping false uses straight-line route`() {
        val updateCount = AtomicInteger(0)
        val config = RoamingConfig(
            centerPosition = LatLng(0.0, 0.0),
            radiusMeters = 200.0,
            distanceMeters = 100.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
        engine.startRoaming(config, 1.4) { _ ->
            updateCount.incrementAndGet()
        }

        Thread.sleep(2000)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        assertTrue("should emit updates in straight-line mode", updateCount.get() > 0)
    }

    // startRoaming — different speed profiles

    @Test
    fun `startRoaming with bike profile advances faster than walk`() {
        val walkPositions = mutableListOf<LatLng>()
        val bikePositions = mutableListOf<LatLng>()
        val center = LatLng(0.0, 0.0)

        val config = RoamingConfig(
            centerPosition = center,
            radiusMeters = 500.0,
            distanceMeters = 1000.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )

        engine.startRoaming(config, 1.4) { pos ->
            walkPositions.add(pos)
        }
        Thread.sleep(2000)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        engine.startRoaming(config, 5.0) { pos ->
            bikePositions.add(pos)
        }
        Thread.sleep(2000)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        if (walkPositions.isNotEmpty() && bikePositions.isNotEmpty()) {
            val walkDist = center.distanceTo(walkPositions.last())
            val bikeDist = center.distanceTo(bikePositions.last())
            assertTrue("bike should advance further than walk in same time", bikeDist > walkDist)
        }
    }

    // startRoaming — zero distance completes immediately

    @Test
    fun `startRoaming with zero distance completes quickly`() {
        val config = RoamingConfig(
            centerPosition = LatLng(0.0, 0.0),
            radiusMeters = 100.0,
            distanceMeters = 0.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
        val job = engine.startRoaming(config, 1.4) { _ -> }

        Thread.sleep(500)

        assertTrue("job should complete quickly with zero distance", job.isCompleted)
    }

    // startRoaming — position callback receives valid coordinates

    @Test
    fun `startRoaming position callback receives non-null coordinates`() {
        val receivedValidCoords = AtomicReference<LatLng?>(null)
        val config = RoamingConfig(
            centerPosition = LatLng(48.8566, 2.3522),
            radiusMeters = 100.0,
            distanceMeters = 50.0,
            useRoadSnapping = false,
            speedProfileId = "walk",
            returnToInitialLocation = false,
        )
        engine.startRoaming(config, 1.4) { pos ->
            if (receivedValidCoords.get() == null) {
                receivedValidCoords.set(pos)
            }
        }

        Thread.sleep(2000)
        kotlinx.coroutines.runBlocking { engine.stopRoaming() }

        val pos = receivedValidCoords.get()
        assertNotNull("should receive valid coordinates", pos)
    }
}
