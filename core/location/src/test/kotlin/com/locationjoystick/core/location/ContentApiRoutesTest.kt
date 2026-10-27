package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.Waypoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class ContentApiRoutesTest {
    private val server = LeaderSyncServer()
    private val favoriteRepository: FavoriteRepository = mockk(relaxed = true)
    private val routeRepository: RouteRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val locationRepository = LocationRepository()
    private var port = 0

    private val fav = FavoriteLocation("f1", "Home", LatLng(1.0, 2.0), createdAt = 5L, category = "x")
    private val route =
        Route(
            id = "r1",
            name = "R",
            waypoints = listOf(Waypoint("w1", LatLng(1.0, 1.0), 0), Waypoint("w2", LatLng(2.0, 2.0), 1, 3)),
            createdAt = 7L,
        )
    private val paste = route.copy(id = AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID)

    @Before
    fun setup() {
        every { favoriteRepository.getFavorites() } returns flowOf(listOf(fav))
        coEvery { favoriteRepository.addFavorite(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { favoriteRepository.updateFavorite(any()) } returns Result.success(Unit)
        coEvery { favoriteRepository.deleteFavorite(any()) } returns Result.success(Unit)
        every { routeRepository.getRoutes() } returns flowOf(listOf(route, paste))
        every { routeRepository.getRouteWithWaypoints("r1") } returns flowOf(route)
        every { routeRepository.getRouteWithWaypoints(paste.id) } returns flowOf(paste)
        every { routeRepository.getRouteWithWaypoints("nope") } returns flowOf(null)
        coEvery { routeRepository.insertRoute(any()) } returns Result.success(Unit)
        coEvery { routeRepository.updateRoute(any()) } returns Result.success(Unit)
        coEvery { routeRepository.deleteRoute(any()) } returns Result.success(Unit)
        every { settingsRepository.getSpeedProfiles() } returns flowOf(SpeedProfile.defaultProfiles())
        every { settingsRepository.getActiveSpeedProfile() } returns flowOf(SpeedProfile.defaultProfiles()[1])
        every { settingsRepository.getEnabledSpeedProfileIds() } returns flowOf(setOf("walk", "run"))
        ContentApiRoutes(server, favoriteRepository, routeRepository, settingsRepository, locationRepository).install()
        port = server.start("gid")
        server.enableApi("k")
    }

    @After
    fun tearDown() = server.stop()

    private fun call(
        method: String,
        path: String,
        body: String? = null,
        auth: Boolean = true,
    ): Pair<Int, String> {
        val conn = URL("http://localhost:$port/api/v1/$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        if (auth) conn.setRequestProperty("Authorization", "Bearer k")
        if (body != null) {
            conn.doOutput = true
            val bytes = body.toByteArray()
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
        }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream).bufferedReader().readText()
        conn.disconnect()
        return code to text
    }

    private fun errorCode(body: String) = JSONObject(body).getJSONObject("error").getString("code")

    private val wp = """[{"lat":1,"lon":1},{"lat":2,"lon":2,"waitSeconds":4}]"""

    @Test
    fun `favorites list get and unknown id`() {
        assertEquals("Home", JSONArray(call("GET", "favorites").second).getJSONObject(0).getString("name"))
        val (code, body) = call("GET", "favorites/f1")
        assertEquals(200, code)
        assertEquals(1.0, JSONObject(body).getDouble("lat"), 0.0)
        val (c404, b404) = call("GET", "favorites/zzz")
        assertEquals(404, c404)
        assertEquals("favorite_not_found", errorCode(b404))
    }

    @Test
    fun `favorite create is 201 and persists`() {
        val (code, body) = call("POST", "favorites", """{"name":" Cafe ","lat":3,"lon":4,"category":"food"}""")
        assertEquals(201, code)
        assertEquals("Cafe", JSONObject(body).getString("name"))
        coVerify { favoriteRepository.addFavorite(any(), "Cafe", LatLng(3.0, 4.0), any(), "food") }
    }

    @Test
    fun `favorite create rejects empty name bad coords and malformed json`() {
        assertEquals(400, call("POST", "favorites", """{"name":" ","lat":3,"lon":4}""").first)
        assertEquals(400, call("POST", "favorites", """{"name":"a","lat":91,"lon":4}""").first)
        assertEquals(400, call("POST", "favorites", "{nope").first)
    }

    @Test
    fun `favorite update keeps id and createdAt and delete works`() {
        val (code, body) = call("PUT", "favorites/f1", """{"name":"New","lat":9,"lon":8}""")
        assertEquals(200, code)
        assertEquals(5L, JSONObject(body).getLong("createdAt"))
        coVerify { favoriteRepository.updateFavorite(fav.copy(name = "New", position = LatLng(9.0, 8.0), category = null)) }
        assertEquals(404, call("PUT", "favorites/zzz", """{"name":"a","lat":1,"lon":1}""").first)
        assertEquals(200, call("DELETE", "favorites/f1").first)
        coVerify { favoriteRepository.deleteFavorite("f1") }
        assertEquals(404, call("DELETE", "favorites/zzz").first)
    }

    @Test
    fun `routes list hides paste temp and get 404s for it`() {
        val list = JSONArray(call("GET", "routes").second)
        assertEquals(1, list.length())
        assertEquals(2, list.getJSONObject(0).getJSONArray("waypoints").length())
        assertEquals(404, call("GET", "routes/${paste.id}").first)
        assertEquals(404, call("DELETE", "routes/${paste.id}").first)
        assertEquals("route_not_found", errorCode(call("GET", "routes/nope").second))
    }

    @Test
    fun `route create assigns order and rejects invalid input`() {
        val (code, body) = call("POST", "routes", """{"name":"N","routeType":"GUIDED","speedProfileId":"run","waypoints":$wp}""")
        assertEquals(201, code)
        val ws = JSONObject(body).getJSONArray("waypoints")
        assertEquals(1, ws.getJSONObject(1).getInt("orderIndex"))
        assertEquals(4, ws.getJSONObject(1).getInt("waitSeconds"))
        coVerify { routeRepository.insertRoute(match { it.name == "N" && it.speedProfileId == "run" }) }
        assertEquals(400, call("POST", "routes", """{"name":"","waypoints":$wp}""").first)
        assertEquals(400, call("POST", "routes", """{"name":"N","waypoints":[{"lat":1,"lon":1}]}""").first)
        assertEquals(400, call("POST", "routes", """{"name":"N","waypoints":[{"lat":1,"lon":1},{"lat":95,"lon":1}]}""").first)
        assertEquals(400, call("POST", "routes", """{"name":"N","routeType":"FLY","waypoints":$wp}""").first)
        assertEquals(400, call("POST", "routes", """{"name":"N","speedProfileId":"warp","waypoints":$wp}""").first)
        assertEquals(400, call("POST", "routes", "{nope").first)
    }

    @Test
    fun `route update keeps createdAt delete works and active route is 409`() {
        val (code, body) = call("PUT", "routes/r1", """{"name":"Z","waypoints":$wp}""")
        assertEquals(200, code)
        assertEquals(7L, JSONObject(body).getLong("createdAt"))
        coVerify { routeRepository.updateRoute(match { it.id == "r1" && it.name == "Z" }) }
        assertEquals(200, call("DELETE", "routes/r1").first)
        locationRepository.setActiveRouteId("r1")
        assertEquals(409, call("DELETE", "routes/r1").first)
        assertEquals(409, call("PUT", "routes/r1", """{"name":"Z","waypoints":$wp}""").first)
    }

    @Test
    fun `route update keeps planting radius and category unless the body sets the radius`() {
        every { routeRepository.getRouteWithWaypoints("r1") } returns flowOf(route.copy(category = "c", plantingRadiusMeters = 20.0))
        assertEquals(200, call("PUT", "routes/r1", """{"name":"Z","waypoints":$wp}""").first)
        coVerify { routeRepository.updateRoute(match { it.plantingRadiusMeters == 20.0 && it.category == "c" }) }
        assertEquals(200, call("PUT", "routes/r1", """{"name":"Z","waypoints":$wp,"plantingRadiusMeters":null}""").first)
        coVerify { routeRepository.updateRoute(match { it.plantingRadiusMeters == null && it.category == "c" }) }
        assertEquals(400, call("PUT", "routes/r1", """{"name":"Z","waypoints":$wp,"plantingRadiusMeters":4}""").first)
        coVerify(exactly = 2) { routeRepository.updateRoute(any()) }
    }

    @Test
    fun `speed profiles list five with active and enabled flags`() {
        val list = JSONArray(call("GET", "speed-profiles").second)
        assertEquals(5, list.length())
        val walk = list.getJSONObject(1)
        assertTrue(walk.getBoolean("active") && walk.getBoolean("enabled") && walk.getBoolean("builtIn"))
        assertEquals(404, call("GET", "speed-profiles/warp").first)
    }

    @Test
    fun `speed update validates range and calls matching setter`() {
        for (bad in listOf("0", "15.5", "\"NaN\"", "\"x\"")) {
            assertEquals(bad, 400, call("PUT", "speed-profiles/run", """{"speedMetersPerSecond":$bad}""").first)
        }
        assertEquals(200, call("PUT", "speed-profiles/run", """{"speedMetersPerSecond":3.5}""").first)
        coVerify(exactly = 1) { settingsRepository.setRunSpeed(3.5) }
        assertEquals(404, call("PUT", "speed-profiles/warp", """{"speedMetersPerSecond":3.5}""").first)
    }

    @Test
    fun `speed profile create and delete are 405 and pattern routes need auth`() {
        assertEquals(405, call("POST", "speed-profiles", "{}").first)
        assertEquals(405, call("DELETE", "speed-profiles/walk").first)
        assertEquals(401, call("GET", "favorites/f1", auth = false).first)
    }
}
