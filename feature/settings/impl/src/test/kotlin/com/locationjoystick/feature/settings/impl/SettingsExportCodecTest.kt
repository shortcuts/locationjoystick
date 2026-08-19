package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------

private fun route(
    id: String = "r1",
    name: String = "test",
    routeType: RouteType = RouteType.STRAIGHT,
    waypoints: List<Waypoint> = emptyList(),
    isLooping: Boolean = false,
    createdAt: Long = 0L,
    speedProfileId: String? = null,
): Route =
    Route(
        id = id,
        name = name,
        waypoints = waypoints,
        isLooping = isLooping,
        routeType = routeType,
        speedProfileId = speedProfileId,
        createdAt = createdAt,
    )

private fun waypoint(
    id: String = "wp1",
    lat: Double = 1.0,
    lon: Double = 2.0,
    orderIndex: Int = 0,
): Waypoint = Waypoint(id = id, position = LatLng(latitude = lat, longitude = lon), orderIndex = orderIndex)

private fun favorite(
    id: String = "fav1",
    name: String = "Home",
    lat: Double = 48.8566,
    lon: Double = 2.3522,
    createdAt: Long = 1000L,
): FavoriteLocation = FavoriteLocation(id = id, name = name, position = LatLng(lat, lon), createdAt = createdAt)

private fun fullExportData(): ExportData =
    minimalExportData().copy(
        routes = listOf(route(routeType = RouteType.GUIDED, waypoints = listOf(waypoint()))),
        favoriteLocations = listOf(favorite()),
        speedProfiles = listOf(SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.4)),
    )

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
class SettingsExportCodecTest {
    @Test
    fun `serialize produces compact JSON with no newlines or indentation`() {
        val json = SettingsExportCodec.serializeExportData(minimalExportData())

        assertFalse("JSON must not contain newlines", json.contains('\n'))
        assertFalse("JSON must not contain leading spaces (indentation)", json.contains("  "))
    }

    @Test
    fun `serialize includes routeType field in route objects`() {
        val data =
            minimalExportData().copy(
                routes = listOf(route(routeType = RouteType.GUIDED)),
            )

        val json = SettingsExportCodec.serializeExportData(data)

        assert(json.contains("\"routeType\"")) { "Expected routeType key in JSON" }
        assert(json.contains("GUIDED")) { "Expected GUIDED value in JSON" }
    }

    @Test
    fun `parse restores GUIDED routeType`() {
        val data =
            minimalExportData().copy(
                routes = listOf(route(routeType = RouteType.GUIDED)),
            )
        val json = SettingsExportCodec.serializeExportData(data)

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(1, parsed.routes.size)
        assertEquals(RouteType.GUIDED, parsed.routes[0].routeType)
    }

    @Test
    fun `parse defaults missing routeType to STRAIGHT`() {
        // Manually craft JSON without routeType field
        @Suppress("ktlint:standard:max-line-length") // JSON string literal cannot be split without changing its value
        val json = """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[{"id":"r1","name":"test","isLooping":false,"createdAt":0,"waypoints":[]}],"favoriteLocations":[],"jitterIdleRadius":0.0,"jitterMovingRadius":1.0,"jitterIntervalSeconds":3}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(1, parsed.routes.size)
        assertEquals(RouteType.STRAIGHT, parsed.routes[0].routeType)
    }

    @Test
    fun `round-trip preserves all fields`() {
        val original = fullExportData()
        val json = SettingsExportCodec.serializeExportData(original)
        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(original.schemaVersion, parsed.schemaVersion)
        assertEquals(original.exportedAt, parsed.exportedAt)
        assertEquals(original.settings.speedUnit, parsed.settings.speedUnit)
        assertEquals(original.speedProfiles.size, parsed.speedProfiles.size)
        assertEquals(original.speedProfiles[0].id, parsed.speedProfiles[0].id)
        assertEquals(original.speedProfiles[0].speedMetersPerSecond, parsed.speedProfiles[0].speedMetersPerSecond, 0.001)
        assertEquals(original.routes.size, parsed.routes.size)
        assertEquals(original.routes[0].id, parsed.routes[0].id)
        assertEquals(original.routes[0].name, parsed.routes[0].name)
        assertEquals(original.routes[0].routeType, parsed.routes[0].routeType)
        assertEquals(original.routes[0].waypoints.size, parsed.routes[0].waypoints.size)
        assertEquals(original.routes[0].waypoints[0].id, parsed.routes[0].waypoints[0].id)
        assertEquals(
            original.routes[0]
                .waypoints[0]
                .position.latitude,
            parsed.routes[0]
                .waypoints[0]
                .position.latitude,
            0.0001,
        )
        assertEquals(original.favoriteLocations.size, parsed.favoriteLocations.size)
        assertEquals(original.favoriteLocations[0].id, parsed.favoriteLocations[0].id)
        assertEquals(original.favoriteLocations[0].name, parsed.favoriteLocations[0].name)
        assertEquals(original.favoriteLocations[0].position.latitude, parsed.favoriteLocations[0].position.latitude, 0.0001)
        assertEquals(original.jitterIdleRadius, parsed.jitterIdleRadius, 0.001)
        assertEquals(original.jitterMovingRadius, parsed.jitterMovingRadius, 0.001)
        assertEquals(original.jitterIntervalSeconds, parsed.jitterIntervalSeconds)
    }

    @Test
    fun `round-trip preserves route speedProfileId`() {
        val data =
            minimalExportData().copy(
                routes = listOf(route(speedProfileId = "bike")),
            )
        val json = SettingsExportCodec.serializeExportData(data)

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals("bike", parsed.routes[0].speedProfileId)
    }

    @Test
    fun `parse defaults missing speedProfileId to null`() {
        @Suppress("ktlint:standard:max-line-length") // JSON string literal cannot be split without changing its value
        val json = """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[{"id":"r1","name":"test","isLooping":false,"createdAt":0,"waypoints":[]}],"favoriteLocations":[],"jitterIdleRadius":0.0,"jitterMovingRadius":1.0,"jitterIntervalSeconds":3}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(null, parsed.routes[0].speedProfileId)
    }

    @Test
    fun `round-trip preserves hideTeleportFeatures`() {
        val data =
            minimalExportData().copy(
                settings = minimalExportData().settings.copy(hideTeleportFeatures = true),
            )
        val json = SettingsExportCodec.serializeExportData(data)

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(true, parsed.settings.hideTeleportFeatures)
    }

    @Test
    fun `parse defaults missing hideTeleportFeatures to false`() {
        @Suppress("ktlint:standard:max-line-length") // JSON string literal cannot be split without changing its value
        val json = """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[],"favoriteLocations":[],"jitterIdleRadius":0.0,"jitterMovingRadius":1.0,"jitterIntervalSeconds":3}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(false, parsed.settings.hideTeleportFeatures)
    }

    @Test
    fun `round-trip preserves showRouteJumpButtons`() {
        val data =
            minimalExportData().copy(
                settings = minimalExportData().settings.copy(showRouteJumpButtons = true),
            )
        val json = SettingsExportCodec.serializeExportData(data)

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(true, parsed.settings.showRouteJumpButtons)
    }

    @Test
    fun `parse defaults missing showRouteJumpButtons to false`() {
        @Suppress("ktlint:standard:max-line-length") // JSON string literal cannot be split without changing its value
        val json = """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[],"favoriteLocations":[],"jitterIdleRadius":0.0,"jitterMovingRadius":1.0,"jitterIntervalSeconds":3}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(false, parsed.settings.showRouteJumpButtons)
    }

    @Test
    fun `round-trip preserves hideForegroundNotification`() {
        val data =
            minimalExportData().copy(
                settings = minimalExportData().settings.copy(hideForegroundNotification = true),
            )
        val json = SettingsExportCodec.serializeExportData(data)

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(true, parsed.settings.hideForegroundNotification)
    }

    @Test
    fun `parse defaults missing hideForegroundNotification to false`() {
        @Suppress("ktlint:standard:max-line-length") // JSON string literal cannot be split without changing its value
        val json = """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[],"favoriteLocations":[],"jitterIdleRadius":0.0,"jitterMovingRadius":1.0,"jitterIntervalSeconds":3}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(false, parsed.settings.hideForegroundNotification)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on unsupported schemaVersion`() {
        val json = """{"schemaVersion":99,"exportedAt":0,"settings":{},"speedProfiles":[],"routes":[],"favoriteLocations":[]}"""
        SettingsExportCodec.parseExportData(json)
    }
}
