package com.locationjoystick.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDomainTest {
    // LatLng edge cases

    @Test
    fun `LatLng equality works for same coordinates`() {
        val a = LatLng(48.8566, 2.3522)
        val b = LatLng(48.8566, 2.3522)
        assertEquals(a, b)
    }

    @Test
    fun `LatLng hashCode is consistent`() {
        val a = LatLng(48.8566, 2.3522)
        val b = LatLng(48.8566, 2.3522)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `LatLng toString contains coordinates`() {
        val point = LatLng(48.8566, 2.3522)
        val str = point.toString()
        assertTrue(str.contains("48.8566"))
        assertTrue(str.contains("2.3522"))
    }

    @Test
    fun `LatLng copy creates new instance`() {
        val original = LatLng(0.0, 0.0)
        val copied = original.copy(latitude = 1.0)
        assertEquals(1.0, copied.latitude, 0.0001)
        assertEquals(0.0, copied.longitude, 0.0001)
        assertEquals(0.0, original.latitude, 0.0001)
    }

    @Test
    fun `LatLng with extreme coordinates works`() {
        val northPole = LatLng(90.0, 0.0)
        val southPole = LatLng(-90.0, 0.0)
        val dateLine = LatLng(0.0, 180.0)

        assertTrue(northPole.distanceTo(southPole) > 0)
        assertTrue(northPole.distanceTo(dateLine) > 0)
    }

    @Test
    fun `LatLng bearingTo at poles is valid`() {
        val northPole = LatLng(89.9, 0.0)
        val other = LatLng(89.8, 1.0)
        val bearing = northPole.bearingTo(other)
        assertTrue("bearing should be valid 0-360", bearing >= 0.0 && bearing < 360.0)
    }

    // Waypoint

    @Test
    fun `Waypoint data class works`() {
        val wp = Waypoint(id = "wp-1", position = LatLng(0.0, 0.0), orderIndex = 0)
        assertEquals("wp-1", wp.id)
        assertEquals(0.0, wp.position.latitude, 0.0001)
        assertEquals(0, wp.orderIndex)
    }

    @Test
    fun `Waypoint equality by value`() {
        val a = Waypoint("wp-1", LatLng(0.0, 0.0), 0)
        val b = Waypoint("wp-1", LatLng(0.0, 0.0), 0)
        assertEquals(a, b)
    }

    // Route

    @Test
    fun `Route data class works`() {
        val waypoints =
            listOf(
                Waypoint("wp-1", LatLng(0.0, 0.0), 0),
                Waypoint("wp-2", LatLng(1.0, 0.0), 1),
            )
        val route =
            Route(
                id = "route-1",
                name = "Test Route",
                waypoints = waypoints,
                isLooping = true,
                routeType = RouteType.GUIDED,
                createdAt = 1000L,
                updatedAt = 2000L,
            )

        assertEquals("route-1", route.id)
        assertEquals("Test Route", route.name)
        assertEquals(2, route.waypoints.size)
        assertTrue(route.isLooping)
        assertEquals(RouteType.GUIDED, route.routeType)
        assertEquals(1000L, route.createdAt)
        assertEquals(2000L, route.updatedAt)
    }

    @Test
    fun `Route copy preserves waypoints`() {
        val waypoints = listOf(Waypoint("wp-1", LatLng(0.0, 0.0), 0))
        val route =
            Route(
                id = "r1",
                name = "A",
                waypoints = waypoints,
                isLooping = false,
                routeType = RouteType.STRAIGHT,
                createdAt = 0,
                updatedAt = 0,
            )
        val updated = route.copy(name = "B")

        assertEquals("B", updated.name)
        assertEquals(1, updated.waypoints.size)
        assertEquals(route.waypoints[0], updated.waypoints[0])
    }

    // FavoriteLocation

    @Test
    fun `FavoriteLocation data class works`() {
        val fav =
            FavoriteLocation(
                id = "fav-1",
                name = "Home",
                position = LatLng(48.8566, 2.3522),
                createdAt = 1000L,
            )

        assertEquals("fav-1", fav.id)
        assertEquals("Home", fav.name)
        assertEquals(48.8566, fav.position.latitude, 0.0001)
        assertEquals(1000L, fav.createdAt)
    }

    // RoamingConfig

    @Test
    fun `RoamingConfig data class works`() {
        val config =
            RoamingConfig(
                centerPosition = LatLng(0.0, 0.0),
                radiusMeters = 500.0,
                distanceMeters = 1000.0,
                useRoadSnapping = true,
                speedProfileId = "walk",
                returnToInitialLocation = true,
            )

        assertEquals(0.0, config.centerPosition.latitude, 0.0001)
        assertEquals(500.0, config.radiusMeters, 0.001)
        assertEquals(1000.0, config.distanceMeters, 0.001)
        assertTrue(config.useRoadSnapping)
        assertEquals("walk", config.speedProfileId)
        assertTrue(config.returnToInitialLocation)
    }

    // RoamingDefaults

    @Test
    fun `RoamingDefaults data class works`() {
        val defaults =
            RoamingDefaults(
                radiusMeters = 500.0,
                distanceMeters = 1000.0,
                speedProfileId = "walk",
                followRoads = true,
                returnToInitialLocation = false,
            )

        assertEquals(500.0, defaults.radiusMeters, 0.001)
        assertEquals(1000.0, defaults.distanceMeters, 0.001)
        assertEquals("walk", defaults.speedProfileId)
        assertTrue(defaults.followRoads)
        assertFalse(defaults.returnToInitialLocation)
    }

    // AppSettings

    @Test
    fun `AppSettings data class works`() {
        val settings =
            AppSettings(
                activeSpeedProfileId = "walk",
                joystickStyle = JoystickStyle.FLOATING,
                enabledWidgetFeatures = setOf(AppFeature.MAP_FLOATING, AppFeature.JOYSTICK_TOGGLE),
                mapFollowsLocation = true,
                useRoadSnappingByDefault = false,
                speedUnit = SpeedUnit.KMH,
            )

        assertEquals("walk", settings.activeSpeedProfileId)
        assertEquals(JoystickStyle.FLOATING, settings.joystickStyle)
        assertEquals(2, settings.enabledWidgetFeatures.size)
        assertTrue(settings.mapFollowsLocation)
        assertFalse(settings.useRoadSnappingByDefault)
        assertEquals(SpeedUnit.KMH, settings.speedUnit)
    }

    // ExportData

    @Test
    fun `ExportData data class works`() {
        val export =
            ExportData(
                schemaVersion = 1,
                exportedAt = 1000L,
                settings =
                    AppSettings(
                        activeSpeedProfileId = "walk",
                        joystickStyle = JoystickStyle.FLOATING,
                        enabledWidgetFeatures = emptySet(),
                        mapFollowsLocation = false,
                        useRoadSnappingByDefault = false,
                        speedUnit = SpeedUnit.KMH,
                    ),
                speedProfiles = SpeedProfile.defaultProfiles(),
                routes = emptyList(),
                favoriteLocations = emptyList(),
            )

        assertEquals(1, export.schemaVersion)
        assertEquals(1000L, export.exportedAt)
        assertEquals(3, export.speedProfiles.size)
        assertTrue(export.routes.isEmpty())
        assertTrue(export.favoriteLocations.isEmpty())
    }

    // Enum values

    @Test
    fun `RouteType has STRAIGHT and GUIDED`() {
        assertEquals(2, RouteType.entries.size)
        assertEquals(RouteType.STRAIGHT, RouteType.valueOf("STRAIGHT"))
        assertEquals(RouteType.GUIDED, RouteType.valueOf("GUIDED"))
    }

    @Test
    fun `MockMode has all expected values`() {
        val modes = MockMode.entries.map { it.name }.toSet()
        assertTrue(modes.contains("JOYSTICK"))
        assertTrue(modes.contains("ROUTE_REPLAY"))
        assertTrue(modes.contains("ROAMING"))
        assertTrue(modes.contains("TELEPORT"))
    }

    @Test
    fun `MockLocationState has all expected values`() {
        val states = MockLocationState.entries.map { it.name }.toSet()
        assertTrue(states.contains("IDLE"))
        assertTrue(states.contains("RUNNING"))
        assertTrue(states.contains("PAUSED"))
        assertTrue(states.contains("ERROR"))
    }

    @Test
    fun `JoystickStyle has FLOATING and FIXED`() {
        assertEquals(2, JoystickStyle.entries.size)
        assertEquals(JoystickStyle.FLOATING, JoystickStyle.valueOf("FLOATING"))
        assertEquals(JoystickStyle.FIXED, JoystickStyle.valueOf("FIXED"))
    }

    @Test
    fun `SpeedUnit has KMH and MPH`() {
        assertEquals(2, SpeedUnit.entries.size)
        assertEquals(SpeedUnit.KMH, SpeedUnit.valueOf("KMH"))
        assertEquals(SpeedUnit.MPH, SpeedUnit.valueOf("MPH"))
    }

    @Test
    fun `AppFeature has all expected values`() {
        val features = AppFeature.entries.map { it.name }.toSet()
        assertTrue(features.contains("JOYSTICK_TOGGLE"))
        assertTrue(features.contains("JOYSTICK_LOCK"))
        assertTrue(features.contains("ROUTES"))
        assertTrue(features.contains("FAVORITES"))
        assertTrue(features.contains("ROAMING"))
        assertTrue(features.contains("SEARCH"))
        assertTrue(features.contains("SPEED_CYCLE"))
        assertTrue(features.contains("MAP_FLOATING"))
    }

    // SpeedProfile custom instance

    @Test
    fun `SpeedProfile custom instance works`() {
        val profile = SpeedProfile(id = "custom", name = "Custom", speedMetersPerSecond = 3.5)
        assertEquals("custom", profile.id)
        assertEquals("Custom", profile.name)
        assertEquals(3.5, profile.speedMetersPerSecond, 0.001)
    }

    @Test
    fun `SpeedProfile equality by value`() {
        val a = SpeedProfile("x", "X", 1.0)
        val b = SpeedProfile("x", "X", 1.0)
        assertEquals(a, b)
    }

    // RoamingDefaults.toConfig

    @Test
    fun `toConfig maps followRoads to useRoadSnapping`() {
        val defaults = RoamingDefaults(followRoads = true)
        val config = defaults.toConfig(LatLng(35.0, 139.0))
        assertTrue(config.useRoadSnapping)
    }

    @Test
    fun `toConfig maps followRoads false to useRoadSnapping false`() {
        val defaults = RoamingDefaults(followRoads = false)
        val config = defaults.toConfig(LatLng(35.0, 139.0))
        assertFalse(config.useRoadSnapping)
    }

    @Test
    fun `toConfig copies all fields from defaults`() {
        val center = LatLng(35.6762, 139.6503)
        val defaults =
            RoamingDefaults(
                radiusMeters = 2000.0,
                distanceMeters = 500.0,
                speedProfileId = "bike",
                followRoads = false,
                returnToInitialLocation = true,
            )
        val config = defaults.toConfig(center)
        assertEquals(center, config.centerPosition)
        assertEquals(2000.0, config.radiusMeters, 0.001)
        assertEquals(500.0, config.distanceMeters, 0.001)
        assertEquals("bike", config.speedProfileId)
        assertFalse(config.useRoadSnapping)
        assertTrue(config.returnToInitialLocation)
    }

    // sortedByAge

    @Test
    fun `sortedByAge newestFirst sorts descending by createdAt`() {
        val favorites =
            listOf(
                FavoriteLocation("1", "Old", LatLng(0.0, 0.0), 1000L),
                FavoriteLocation("2", "Newest", LatLng(0.0, 0.0), 3000L),
                FavoriteLocation("3", "Mid", LatLng(0.0, 0.0), 2000L),
            )
        val sorted = favorites.sortedByAge(newestFirst = true)
        assertEquals("Newest", sorted[0].name)
        assertEquals("Mid", sorted[1].name)
        assertEquals("Old", sorted[2].name)
    }

    @Test
    fun `sortedByAge oldestFirst sorts ascending by createdAt`() {
        val favorites =
            listOf(
                FavoriteLocation("1", "Old", LatLng(0.0, 0.0), 1000L),
                FavoriteLocation("2", "Newest", LatLng(0.0, 0.0), 3000L),
                FavoriteLocation("3", "Mid", LatLng(0.0, 0.0), 2000L),
            )
        val sorted = favorites.sortedByAge(newestFirst = false)
        assertEquals("Old", sorted[0].name)
        assertEquals("Mid", sorted[1].name)
        assertEquals("Newest", sorted[2].name)
    }

    // RouteReplayMode

    @Test
    fun `RouteReplayMode has all expected values`() {
        val modes = RouteReplayMode.entries.map { it.name }.toSet()
        assertTrue(modes.contains("ONE_WAY"))
        assertTrue(modes.contains("RETURN_TO_LOCATION"))
        assertTrue(modes.contains("LOOP"))
        assertTrue(modes.contains("LOOP_REVERSE"))
        assertEquals(4, RouteReplayMode.entries.size)
    }
}
