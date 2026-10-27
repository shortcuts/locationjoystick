package com.locationjoystick.core.location

import android.content.Context
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.SpeedProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class ControlApiRoutesTest {
    private val server = LeaderSyncServer()
    private val mapController: MapController = mockk(relaxed = true)
    private val locationRepository = LocationRepository()
    private val roamingRepository: RoamingRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var port = 0

    @Before
    fun setup() {
        every { roamingRepository.isRoaming } returns MutableStateFlow(false)
        every { roamingRepository.isRoamingPaused } returns MutableStateFlow(false)
        every { mapController.cooldownForPosition(any()) } returns flowOf(CooldownState.Ready)
        val walk = SpeedProfile("walk", "Walk", 10.0)
        every { settingsRepository.getSpeedProfiles() } returns flowOf(listOf(walk, SpeedProfile("bike", "Bike", 20.0)))
        every { settingsRepository.getActiveSpeedProfile() } returns flowOf(walk)
        every { settingsRepository.getRoamingDefaults() } returns flowOf(RoamingDefaults())
        every { routeRepository.getRouteWithWaypoints("r1") } returns flowOf(Route(id = "r1", name = "R", waypoints = emptyList()))
        every { routeRepository.getRouteWithWaypoints("nope") } returns flowOf(null)
        ControlApiRoutes(context, server, mapController, locationRepository, roamingRepository, settingsRepository, routeRepository, scope)
            .install()
        port = server.start("gid")
        server.enableApi("k")
    }

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun call(
        path: String,
        method: String = "POST",
        body: String? = "{}",
    ): Pair<Int, String> {
        val conn = URL("http://localhost:$port/api/v1/$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer k")
        if (method == "POST") {
            conn.doOutput = true
            val bytes = (body ?: "").toByteArray()
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
        }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream).bufferedReader().readText()
        conn.disconnect()
        return code to text
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met in time", condition())
    }

    @Test
    fun `teleport calls the UI teleport once with no warning when ready`() {
        val (code, body) = call("teleport", body = """{"lat":1.5,"lon":2.5}""")
        assertEquals(200, code)
        assertFalse(JSONObject(body).has("warning"))
        verify(exactly = 1) { mapController.teleportTo(LatLng(1.5, 2.5)) }
    }

    @Test
    fun `teleport during cooldown still teleports and returns a warning`() {
        every { mapController.cooldownForPosition(any()) } returns flowOf(CooldownState.Cooling(30, 60, 1234.0))
        val (code, body) = call("teleport", body = """{"lat":1.5,"lon":2.5}""")
        assertEquals(200, code)
        val warning = JSONObject(body).getJSONObject("warning")
        assertEquals("teleport_cooldown", warning.getString("code"))
        assertEquals(30, warning.getInt("remainingSeconds"))
        verify(exactly = 1) { mapController.teleportTo(LatLng(1.5, 2.5)) }
    }

    @Test
    fun `teleport with out-of-range or malformed input is 400 and does nothing`() {
        assertEquals(400, call("teleport", body = """{"lat":91,"lon":2}""").first)
        assertEquals(400, call("teleport", body = "{not json").first)
        verify(exactly = 0) { mapController.teleportTo(any()) }
    }

    @Test
    fun `walk picks straight or road walking and pause resume stop map to the controller`() {
        call("walk", body = """{"lat":1,"lon":2}""")
        call("walk", body = """{"lat":3,"lon":4,"viaRoads":true}""")
        call("walk/pause")
        call("walk/resume")
        call("walk/stop")
        verify { mapController.walkTo(LatLng(1.0, 2.0)) }
        verify { mapController.walkViaRoads(LatLng(3.0, 4.0)) }
        verify { mapController.pauseWalk() }
        verify { mapController.resumeWalk() }
        verify { mapController.stopWalk() }
    }

    @Test
    fun `route and roam and spoofing controls map to the controller`() {
        call("route/pause")
        call("route/resume")
        call("route/stop")
        call("roam/pause")
        call("roam/resume")
        call("roam/stop")
        call("spoofing/start")
        call("spoofing/stop")
        verify { mapController.pauseRouteReplay() }
        verify { mapController.resumeRouteReplay() }
        verify { mapController.stopRouteReplay() }
        verify { mapController.pauseRoaming() }
        verify { mapController.resumeRoaming() }
        verify { mapController.stopRoaming() }
        verify { mapController.startSpoofing() }
        verify { mapController.stopSpoofing() }
    }

    @Test
    fun `route start maps the body and bypasses hide teleport, unknown route is 404`() {
        assertEquals(200, call("route/start", body = """{"routeId":"r1","loop":true,"teleportBetweenWaypoints":true}""").first)
        verify { mapController.startRouteReplay("r1", RouteStartConfig(isLooping = true, teleportBetweenWaypoints = true), true) }
        val (code, body) = call("route/start", body = """{"routeId":"nope"}""")
        assertEquals(404, code)
        assertTrue(body.contains("route_not_found"))
        verify(exactly = 1) { mapController.startRouteReplay(any(), any(), any()) }
    }

    @Test
    fun `roam start needs a position and no playing route, and applies overrides to defaults`() {
        assertEquals(409, call("roam/start").first)
        locationRepository.setPositionInternal(LatLng(5.0, 6.0))
        assertEquals(200, call("roam/start", body = """{"radiusMeters":250}""").first)
        verify { mapController.startRoaming(RoamingDefaults(radiusMeters = 250.0), LatLng(5.0, 6.0), any()) }

        locationRepository.setMockMode(MockMode.ROUTE_REPLAY)
        locationRepository.startSpoofing()
        assertEquals(409, call("roam/start").first)
        verify(exactly = 1) { mapController.startRoaming(any(), any(), any()) }
    }

    @Test
    fun `roam start with NaN or huge distance is 400 and never starts roaming`() {
        locationRepository.setPositionInternal(LatLng(5.0, 6.0))
        assertEquals(400, call("roam/start", body = """{"distanceMeters":"NaN"}""").first)
        assertEquals(400, call("roam/start", body = """{"radiusMeters":"Infinity"}""").first)
        assertEquals(400, call("roam/start", body = """{"speedProfileId":"warp"}""").first)
        verify(exactly = 0) { mapController.startRoaming(any(), any(), any()) }
        assertEquals(200, call("roam/start", body = """{"distanceMeters":1e12,"radiusMeters":-5}""").first)
        verify { mapController.startRoaming(RoamingDefaults(radiusMeters = 1.0, distanceMeters = 50_000.0), LatLng(5.0, 6.0), any()) }
    }

    @Test
    fun `two concurrent joystick requests advance only one hold`() {
        locationRepository.setPositionInternal(LatLng(10.0, 10.0))
        val start = java.util.concurrent.CountDownLatch(1)
        val threads =
            List(64) {
                Thread {
                    start.await()
                    call("joystick", body = """{"bearingDegrees":0,"force":1,"durationMs":1000}""")
                }.also { it.start() }
            }
        start.countDown()
        threads.forEach { it.join() }
        // Cancelled holds may have ticked early; what remains after the last request must be one hold.
        val before = locationRepository.currentPosition.value!!.latitude
        waitFor { locationRepository.currentMode.value == MockMode.TELEPORT }
        Thread.sleep(300)
        // One 1 s hold at 10 m/s is 10 m; leaked holds would multiply it.
        val movedMeters = (locationRepository.currentPosition.value!!.latitude - before) * 111_000.0
        assertTrue("moved $movedMeters m", movedMeters <= 12.0)
    }

    @Test
    fun `speed profile rejects unknown ids and selects known ones`() {
        coEvery { settingsRepository.setActiveProfileId(any()) } returns Unit
        assertEquals(400, call("speed-profile", body = """{"id":"warp"}""").first)
        assertEquals(200, call("speed-profile", body = """{"id":"walk"}""").first)
        coVerify(exactly = 1) { settingsRepository.setActiveProfileId("walk") }
    }

    @Test
    fun `position and state reflect the repository`() {
        assertEquals(409, call("position", method = "GET").first)
        locationRepository.setPositionInternal(LatLng(7.0, 8.0))
        locationRepository.startSpoofing()
        assertEquals(7.0, JSONObject(call("position", method = "GET").second).getDouble("lat"), 0.0)
        val state = JSONObject(call("state", method = "GET").second)
        assertEquals(MockLocationState.RUNNING.name, state.getString("spoofState"))
        assertEquals("walk", state.getString("speedProfileId"))
        assertEquals(8.0, state.getJSONObject("position").getDouble("lon"), 0.0)
    }

    @Test
    fun `state speedMs reflects reported speed`() {
        locationRepository.setReportedSpeed(2.5f)
        val state = JSONObject(call("state", method = "GET").second)
        assertEquals(2.5, state.getDouble("speedMs"), 0.0)
    }

    @Test
    fun `joystick hold moves the position, takes over, then releases to teleport mode`() {
        locationRepository.setPositionInternal(LatLng(10.0, 10.0))
        assertEquals(200, call("joystick", body = """{"bearingDegrees":0,"force":1,"durationMs":300}""").first)
        verify { mapController.pauseAutomatedMovement() }
        waitFor { locationRepository.currentMode.value == MockMode.JOYSTICK }
        waitFor { locationRepository.currentMode.value == MockMode.TELEPORT }
        assertTrue(locationRepository.currentPosition.value!!.latitude > 10.0)
        verify { context.startService(any()) }
    }

    @Test
    fun `joystick rejects invalid input and force zero releases`() {
        locationRepository.setPositionInternal(LatLng(10.0, 10.0))
        assertEquals(400, call("joystick", body = """{"bearingDegrees":0,"force":1,"durationMs":999999}""").first)
        assertEquals(400, call("joystick", body = """{"force":1,"durationMs":100}""").first)
        locationRepository.setMockMode(MockMode.JOYSTICK)
        assertEquals(200, call("joystick", body = """{"force":0,"durationMs":100}""").first)
        assertEquals(MockMode.TELEPORT, locationRepository.currentMode.value)
    }

    @Test
    fun `new routes stay behind the api key`() {
        server.disableApi()
        assertEquals(404, call("teleport", body = """{"lat":1,"lon":2}""").first)
        verify(exactly = 0) { mapController.teleportTo(any()) }
    }
}
