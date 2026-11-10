package com.locationjoystick.app.smoke

import android.app.ActivityManager
import androidx.test.platform.app.InstrumentationRegistry
import com.locationjoystick.app.di.FakeRoad
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.ControlApiRoutes
import com.locationjoystick.core.location.LeaderSyncServer
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject

/**
 * Drives every movement mode at the 15 m/s speed cap so each test ends in seconds, with road lookups
 * answered by [FakeRoad]. Asserts arrival, no backtrack and the published stop counter.
 */
@HiltAndroidTest
class MovementSmokeTest : BaseSmokeTest() {
    @Inject lateinit var mapController: MapController

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var routeRepository: RouteRepository

    @Inject lateinit var controlApiRoutes: ControlApiRoutes

    @Inject lateinit var leaderSyncServer: LeaderSyncServer

    private lateinit var recorder: MovementRecorder

    @Before
    override fun setup() {
        super.setup()
        FakeRoad.reset()
        recorder = MovementRecorder(locationRepository)
        runBlocking {
            settingsRepository.setDriveSpeed(STEP_M)
            settingsRepository.setActiveProfileId("drive")
        }
        // A service left running by an earlier class made the first route start do nothing.
        mapController.stopSpoofing()
        recorder.waitUntil(what = "service stopped") { !isServiceRunning() }
        mapController.startSpoofing()
        recorder.start()
        recorder.waitUntil(what = "first position") { recorder.positions.isNotEmpty() }
        // The teleport stops any replay when it runs, so a route started before it finishes would be cancelled.
        val teleportStart = System.currentTimeMillis()
        mapController.teleportTo(ORIGIN)
        recorder.waitUntil(what = "teleport request done") {
            runBlocking { settingsRepository.getLastTeleportTime().first() } >=
                teleportStart
        }
        recorder.waitUntil(
            what = "teleport to origin",
        ) { recorder.positions.lastOrNull()?.let { distance(it, ORIGIN) <= ARRIVE_M } == true }
        recorder.clear()
    }

    @After
    fun teardown() {
        if (::recorder.isInitialized) recorder.stop()
        leaderSyncServer.stop()
        mapController.stopSpoofing()
    }

    @Test
    fun walk_straight_arrives_without_backtrack() {
        mapController.walkTo(STOP_1)
        recorder.waitForArrival(STOP_1)

        assertNoBacktrack(recorder.positions, listOf(STOP_1))
        assertTrue("a single walk has no stop counter", recorder.progress.isEmpty())
    }

    @Test
    fun walk_via_roads_arrives() {
        mapController.walkViaRoads(STOP_1)
        recorder.waitForArrival(STOP_1)

        assertTrue("walk never asked the fake road", FakeRoad.requestCount >= 1)
        assertNoBacktrack(recorder.positions, listOf(STOP_1))
    }

    @Test
    fun walk_via_roads_over_self_overlapping_road_arrives() {
        FakeRoad.shape = ::selfOverlappingRoad
        mapController.walkViaRoads(STOP_1)
        recorder.waitForArrival(STOP_1)

        assertTrue("walk never asked the fake road", FakeRoad.requestCount >= 1)
        assertPassedNear(detourPoint(ORIGIN, STOP_1))
        assertNoBacktrack(recorder.positions, listOf(STOP_1))
    }

    @Test
    fun add_next_point_straight_chain() {
        mapController.walkTo(STOP_1)
        recorder.waitUntil(what = "two steps") { recorder.positions.last().let { distance(it, ORIGIN) >= 2 * STEP_M } }
        mapController.addEphemeralWaypoint(STOP_2)
        recorder.waitUntil(what = "counter 2") { recorder.progress.any { it.total == 2 } }
        recorder.waitUntil(what = "first leg done") { recorder.positions.last().let { distance(it, STOP_1) <= PASS_M } }
        mapController.addEphemeralWaypoint(STOP_3)
        recorder.waitForArrival(STOP_3)
        recorder.waitForCounterEnd()

        assertNoBacktrack(recorder.positions, listOf(STOP_1, STOP_2, STOP_3))
        assertEquals(listOf(2, 3), recorder.progress.map { it.total }.distinct())
        assertCounterCompletes(recorder.progress)
    }

    @Test
    fun add_next_point_via_roads_chain() {
        FakeRoad.shape = ::selfOverlappingRoad
        mapController.walkViaRoads(STOP_1)
        recorder.waitUntil(what = "two steps") { recorder.positions.last().let { distance(it, ORIGIN) >= 2 * STEP_M } }
        mapController.addEphemeralWaypoint(STOP_2, followRoads = true)
        recorder.waitUntil(what = "counter 2") { recorder.progress.any { it.total == 2 } }
        recorder.waitUntil(what = "first leg done") { recorder.positions.last().let { distance(it, STOP_1) <= PASS_M } }
        mapController.addEphemeralWaypoint(STOP_3, followRoads = true)
        recorder.waitForArrival(STOP_3)
        recorder.waitForCounterEnd()

        assertTrue("chain never asked the fake road", FakeRoad.requestCount >= 1)
        assertPassedNear(detourPoint(STOP_1, STOP_2))
        assertNoBacktrack(recorder.positions, listOf(STOP_1, STOP_2, STOP_3))
        assertEquals(listOf(2, 3), recorder.progress.map { it.total }.distinct())
        assertCounterCompletes(recorder.progress)
    }

    @Test
    fun named_walking_route_arrives() {
        val id = insertRoute(RouteType.STRAIGHT, waitSeconds = 0)
        mapController.startRouteReplay(id)
        recorder.waitForArrival(STOP_3)
        recorder.waitForCounterEnd()

        assertNoBacktrack(recorder.positions, listOf(STOP_1, STOP_2, STOP_3))
        assertEquals(3, recorder.progress.first().total)
        assertCounterCompletes(recorder.progress)
    }

    @Test
    fun teleport_route_visits_every_stop() {
        val id = insertRoute(RouteType.TELEPORT, waitSeconds = 1)
        mapController.startRouteReplay(id)
        recorder.waitUntil(what = "last stop") { recorder.positions.last().let { distance(it, STOP_3) <= ARRIVE_M } }

        assertVisitedInOrder(recorder.positions, listOf(STOP_1, STOP_2, STOP_3), radius = ARRIVE_M)
        assertEquals(3, recorder.progress.first().total)
        assertCounterCompletes(recorder.progress)
    }

    @Test
    fun roaming_moves_in_bounded_steps() {
        mapController.startRoaming(
            RoamingDefaults(speedProfileId = "drive", followRoads = false, radiusMeters = LEG_M, distanceMeters = 2 * LEG_M),
            ORIGIN,
        )
        Thread.sleep(ROAMING_SAMPLE_MS)
        mapController.stopRoaming()

        val steps = recorder.positions.zipWithNext { a, b -> distance(a, b) }
        assertTrue("jump of ${steps.max()} m", steps.all { it <= BACKTRACK_TOLERANCE_M })
        assertTrue("roaming moved only ${steps.sum()} m", steps.sum() >= 5 * STEP_M)
        assertTrue("roaming has no stop counter", recorder.progress.isEmpty())
    }

    @Test
    fun joystick_holds_bearing() {
        controlApiRoutes.install()
        val port = leaderSyncServer.start("smoke")
        leaderSyncServer.enableApi("k")
        val start = recorder.positions.last()

        val conn = URL("http://127.0.0.1:$port/api/v1/joystick").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer k")
        conn.doOutput = true
        conn.outputStream.use { it.write("""{"force":1,"bearingDegrees":0,"durationMs":$JOYSTICK_MS}""".toByteArray()) }
        assertEquals(200, conn.responseCode)
        Thread.sleep(JOYSTICK_MS + JOYSTICK_SETTLE_MS)

        val end = recorder.positions.last()
        val expected = STEP_M * JOYSTICK_STEP_S * (JOYSTICK_MS / JOYSTICK_STEP_MS)
        assertTrue("joystick did not move north", end.latitude > start.latitude)
        assertTrue("joystick drifted sideways", distance(start, LatLng(start.latitude, end.longitude)) <= ARRIVE_M * 2.5)
        assertEquals(expected, distance(start, end), expected * 0.2)
        assertNull("joystick has no stop counter", recorder.progress.firstOrNull())
    }

    @Suppress("DEPRECATION") // Own-app services are still listed, which is all this check needs.
    private fun isServiceRunning(): Boolean =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == AppConstants.ServiceConstants.MOCK_LOCATION_SERVICE_CLASS }

    private fun insertRoute(
        type: RouteType,
        waitSeconds: Int,
    ): String {
        val id = UUID.randomUUID().toString()
        val waypoints =
            listOf(STOP_1, STOP_2, STOP_3).mapIndexed { i, p -> Waypoint(UUID.randomUUID().toString(), p, i, waitSeconds) }
        runBlocking {
            routeRepository
                .insertRoute(
                    Route(id = id, name = "movement-$type", waypoints = waypoints, routeType = type, speedProfileId = "drive"),
                ).getOrThrow()
        }
        return id
    }

    private fun assertPassedNear(point: LatLng) {
        assertTrue(
            "road detour never walked: closest ${recorder.positions.minOf { distance(it, point) }} m",
            recorder.positions.any { distance(it, point) <= PASS_M },
        )
    }

    private companion object {
        const val ROAMING_SAMPLE_MS = 20_000L
        const val JOYSTICK_MS = 3_000L
        const val JOYSTICK_SETTLE_MS = 500L
        const val JOYSTICK_STEP_MS = 100L
        const val JOYSTICK_STEP_S = 0.1
    }
}
