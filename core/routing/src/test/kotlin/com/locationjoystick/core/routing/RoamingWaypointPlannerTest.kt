package com.locationjoystick.core.routing

import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.distanceTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class RoamingWaypointPlannerTest {
    private val mockOsrmClient = mockk<OsrmClient>(relaxed = true)
    private val fakeContext: android.content.Context =
        mockk<android.content.Context>(relaxed = true).also { ctx ->
            every {
                ctx.getString(R.string.routing_road_following_partial_fallback, any(), any())
            } answers {
                val args = it.invocation.args[1] as Array<*>
                "Road-following partially unavailable — ${args[0]} of ${args[1]} legs used straight-line paths"
            }
        }
    private val routingErrorReporter = RoutingErrorReporter(fakeContext)
    private val engine = RoamingEngine(mockOsrmClient, RouteInterpolator(), routingErrorReporter, kotlinx.coroutines.Dispatchers.Unconfined)
    private val center = LatLng(48.8566, 2.3522)

    @Test
    fun `planRoute straight-line returns correct waypoint count`() =
        runBlocking {
            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    useRoadSnapping = false,
                    returnToInitialLocation = false,
                )
            val route = engine.planRoute(config)

            // numPoints = round(1000 * 30 / 1000) = 30; route = center + 30 points
            val expectedNumPoints = (1000.0 * 30 / 1000.0).roundToInt()
            assertEquals(expectedNumPoints + 1, route.size)
            route.drop(1).forEach { point ->
                assertTrue("point outside radius", center.distanceTo(point) <= 500.0 + 10.0)
            }
        }

    @Test
    fun `planRoute straight-line with returnToStart ends at center`() =
        runBlocking {
            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    useRoadSnapping = false,
                    returnToInitialLocation = true,
                )
            val route = engine.planRoute(config)

            assertEquals(center.latitude, route.last().latitude, 0.0001)
            assertEquals(center.longitude, route.last().longitude, 0.0001)
        }

    @Test
    fun `planRoute zero distance returns single center point`() =
        runBlocking {
            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 0.0,
                    useRoadSnapping = false,
                )
            val route = engine.planRoute(config)

            assertEquals(1, route.size)
            assertEquals(center, route.first())
        }

    @Test
    fun `startRoaming with zero distance emits no position updates`() {
        val config =
            RoamingConfig(
                centerPosition = center,
                radiusMeters = 500.0,
                distanceMeters = 0.0,
                useRoadSnapping = false,
            )
        var updateCount = 0
        val latch = CountDownLatch(1)
        engine.startRoaming(config, 1.4, onComplete = { latch.countDown() }) { updateCount++ }
        latch.await(5, TimeUnit.SECONDS)

        assertEquals(0, updateCount)
    }

    @Test
    fun `planRoute road-following accumulates distance until target`() =
        runBlocking {
            val dest = LatLng(48.86, 2.36)
            val segmentResult = OsrmRouteResult(waypoints = listOf(center, dest), distanceMeters = 400.0)
            coEvery { mockOsrmClient.getRouteWithDistance(any(), any()) } returns Result.success(segmentResult)

            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    useRoadSnapping = true,
                    returnToInitialLocation = false,
                )
            val route = engine.planRoute(config)

            // 400m/segment, target 1000m → 3 calls (400+400+400 >= 1000)
            coVerify(exactly = 3) { mockOsrmClient.getRouteWithDistance(any(), any()) }
            assertTrue("route should have multiple waypoints", route.size >= 2)
        }

    @Test
    fun `planRoute road-following with returnToStart fetches final OSRM leg to center`() =
        runBlocking {
            val dest = LatLng(48.86, 2.36)
            val segmentResult = OsrmRouteResult(waypoints = listOf(center, dest), distanceMeters = 600.0)
            coEvery { mockOsrmClient.getRouteWithDistance(any(), any()) } returns Result.success(segmentResult)

            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    useRoadSnapping = true,
                    returnToInitialLocation = true,
                )
            engine.planRoute(config)

            // target/2 = 500m, each segment = 600m → 1 outbound + 1 return + 1 final leg = 3 calls
            coVerify(atLeast = 3) { mockOsrmClient.getRouteWithDistance(any(), any()) }
        }

    @Test
    fun `planRoute road-following OSRM failure falls back to straight-line`() =
        runBlocking {
            coEvery { mockOsrmClient.getRouteWithDistance(any(), any()) } returns Result.failure(Exception("Network error"))

            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    useRoadSnapping = true,
                    returnToInitialLocation = false,
                )
            val route = engine.planRoute(config)

            // Fallback: straight-line with 30 random points + center
            assertTrue("fallback route should have waypoints", route.size >= 2)
            route.drop(1).forEach { point ->
                assertTrue("fallback point outside radius", center.distanceTo(point) <= 500.0 + 10.0)
            }
        }

    @Test
    fun `planRoute road-following emits a fallback summary only when segments fell back`() =
        runTest(UnconfinedTestDispatcher()) {
            val received = mutableListOf<String>()
            backgroundScope.launch { routingErrorReporter.errors.collect { received.add(it) } }

            coEvery { mockOsrmClient.getRouteWithDistance(any(), any()) } returns Result.failure(Exception("Network error"))
            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    useRoadSnapping = true,
                    returnToInitialLocation = false,
                )
            engine.planRoute(config)

            assertEquals(1, received.size)
            assertTrue(
                "expected a fallback-count summary, got: ${received.first()}",
                received.first().contains("partially unavailable"),
            )
        }

    @Test
    fun `planRoute road-following emits no summary when every segment succeeds`() =
        runTest(UnconfinedTestDispatcher()) {
            val received = mutableListOf<String>()
            backgroundScope.launch { routingErrorReporter.errors.collect { received.add(it) } }

            val dest = LatLng(48.86, 2.36)
            val segmentResult = OsrmRouteResult(waypoints = listOf(center, dest), distanceMeters = 400.0)
            coEvery { mockOsrmClient.getRouteWithDistance(any(), any()) } returns Result.success(segmentResult)
            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    useRoadSnapping = true,
                    returnToInitialLocation = false,
                )
            engine.planRoute(config)

            assertNull(received.firstOrNull())
        }

    @Test
    fun `planRoute planting returns a closed spiral around center`() =
        runBlocking {
            val config =
                RoamingConfig(
                    centerPosition = center,
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    kind = com.locationjoystick.core.model.RoamingKind.PLANTING,
                    plantingStartRadiusMeters = 5.0,
                    plantingEndRadiusMeters = 39.0,
                    useRoadSnapping = true,
                )
            val route = engine.planRoute(config)

            coVerify(exactly = 0) { mockOsrmClient.getRouteWithDistance(any(), any()) }
            assertTrue(route.size >= 4)
            assertTrue(center.distanceTo(route.first()) < 6.0)
            assertTrue(route.first().distanceTo(route.last()) < 1.0)
            val peak = route.maxOf { center.distanceTo(it) }
            assertTrue("peak $peak should near 39m", peak in 35.0..42.0)
        }
}
