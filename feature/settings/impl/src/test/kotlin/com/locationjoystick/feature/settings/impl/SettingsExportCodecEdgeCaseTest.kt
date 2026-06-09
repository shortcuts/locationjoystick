package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.model.WidgetFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsExportCodecEdgeCaseTest {
    private fun baseData() = minimalExportData()

    // -------------------------------------------------------------------------
    // Unicode in names
    // -------------------------------------------------------------------------

    @Test
    fun `round-trip preserves unicode characters in route name`() {
        val data =
            baseData().copy(
                routes =
                    listOf(
                        Route(
                            id = "r1",
                            name = "東京ルート 🗺️",
                            waypoints = emptyList(),
                            routeType = RouteType.STRAIGHT,
                        ),
                    ),
            )

        val restored = SettingsExportCodec.parseExportData(SettingsExportCodec.serializeExportData(data))

        assertEquals("東京ルート 🗺️", restored.routes[0].name)
    }

    @Test
    fun `round-trip preserves unicode characters in favorite name`() {
        val data =
            baseData().copy(
                favoriteLocations =
                    listOf(
                        FavoriteLocation(
                            id = "f1",
                            name = "Café ☕ — Montmartre",
                            position = LatLng(48.8867, 2.3431),
                        ),
                    ),
            )

        val restored = SettingsExportCodec.parseExportData(SettingsExportCodec.serializeExportData(data))

        assertEquals("Café ☕ — Montmartre", restored.favoriteLocations[0].name)
    }

    @Test
    fun `round-trip preserves CJK characters in favorite name`() {
        val data =
            baseData().copy(
                favoriteLocations =
                    listOf(
                        FavoriteLocation(id = "f1", name = "新宿駅", position = LatLng(35.6896, 139.7006)),
                    ),
            )

        val restored = SettingsExportCodec.parseExportData(SettingsExportCodec.serializeExportData(data))

        assertEquals("新宿駅", restored.favoriteLocations[0].name)
    }

    // -------------------------------------------------------------------------
    // Extreme coordinate values
    // -------------------------------------------------------------------------

    @Test
    fun `round-trip preserves north pole coordinates`() {
        val data =
            baseData().copy(
                favoriteLocations =
                    listOf(FavoriteLocation(id = "f1", name = "North Pole", position = LatLng(90.0, 0.0))),
            )

        val restored = SettingsExportCodec.parseExportData(SettingsExportCodec.serializeExportData(data))

        assertEquals(90.0, restored.favoriteLocations[0].position.latitude, 1e-9)
        assertEquals(0.0, restored.favoriteLocations[0].position.longitude, 1e-9)
    }

    @Test
    fun `round-trip preserves south pole coordinates`() {
        val data =
            baseData().copy(
                favoriteLocations =
                    listOf(FavoriteLocation(id = "f1", name = "South Pole", position = LatLng(-90.0, 0.0))),
            )

        val restored = SettingsExportCodec.parseExportData(SettingsExportCodec.serializeExportData(data))

        assertEquals(-90.0, restored.favoriteLocations[0].position.latitude, 1e-9)
    }

    @Test
    fun `round-trip preserves international date line coordinates`() {
        val data =
            baseData().copy(
                favoriteLocations =
                    listOf(
                        FavoriteLocation(id = "f1", name = "Date Line W", position = LatLng(0.0, -180.0)),
                        FavoriteLocation(id = "f2", name = "Date Line E", position = LatLng(0.0, 180.0)),
                    ),
            )

        val restored = SettingsExportCodec.parseExportData(SettingsExportCodec.serializeExportData(data))

        assertEquals(-180.0, restored.favoriteLocations[0].position.longitude, 1e-9)
        assertEquals(180.0, restored.favoriteLocations[1].position.longitude, 1e-9)
    }

    @Test
    fun `round-trip preserves high-precision waypoint coordinates`() {
        val data =
            baseData().copy(
                routes =
                    listOf(
                        Route(
                            id = "r1",
                            name = "Precise",
                            waypoints =
                                listOf(
                                    Waypoint(
                                        id = "wp1",
                                        position = LatLng(48.856613786471934, 2.352183073759177),
                                        orderIndex = 0,
                                    ),
                                ),
                            routeType = RouteType.STRAIGHT,
                        ),
                    ),
            )

        val restored = SettingsExportCodec.parseExportData(SettingsExportCodec.serializeExportData(data))

        assertEquals(
            48.856613786471934,
            restored.routes[0]
                .waypoints[0]
                .position.latitude,
            1e-9,
        )
        assertEquals(
            2.352183073759177,
            restored.routes[0]
                .waypoints[0]
                .position.longitude,
            1e-9,
        )
    }

    // -------------------------------------------------------------------------
    // Missing optional fields fall back to AppConstants (not hardcoded literals)
    // -------------------------------------------------------------------------

    @Test
    fun `missing jitterMovingRadius defaults to AppConstants value`() {
        val json =
            """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[],"favoriteLocations":[],"jitterIdleRadius":0.5,"jitterIntervalSeconds":3}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(
            "Missing jitterMovingRadius must default to AppConstants.JitterConstants.DEFAULT_MOVING_RADIUS_METERS",
            AppConstants.JitterConstants.DEFAULT_MOVING_RADIUS_METERS,
            parsed.jitterMovingRadius,
            1e-9,
        )
    }

    @Test
    fun `missing jitterIdleRadius defaults to AppConstants value`() {
        val json =
            """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[],"favoriteLocations":[],"jitterMovingRadius":1.0,"jitterIntervalSeconds":3}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(
            "Missing jitterIdleRadius must default to AppConstants.JitterConstants.DEFAULT_IDLE_RADIUS_METERS",
            AppConstants.JitterConstants.DEFAULT_IDLE_RADIUS_METERS,
            parsed.jitterIdleRadius,
            1e-9,
        )
    }

    @Test
    fun `missing jitterIntervalSeconds defaults to AppConstants value`() {
        val json =
            """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[],"favoriteLocations":[],"jitterIdleRadius":0.5,"jitterMovingRadius":1.0}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(
            "Missing jitterIntervalSeconds must default to AppConstants.JitterConstants.DEFAULT_MOVING_INTERVAL_SECONDS",
            AppConstants.JitterConstants.DEFAULT_MOVING_INTERVAL_SECONDS,
            parsed.jitterIntervalSeconds,
        )
    }

    // -------------------------------------------------------------------------
    // Schema version validation uses constant (not hardcoded literal)
    // -------------------------------------------------------------------------

    @Test
    fun `schemaVersion matching SCHEMA_VERSION constant is accepted`() {
        val data = baseData().copy(schemaVersion = AppConstants.ExportConstants.SCHEMA_VERSION)
        val json = SettingsExportCodec.serializeExportData(data)

        // Must not throw
        val parsed = SettingsExportCodec.parseExportData(json)
        assertEquals(AppConstants.ExportConstants.SCHEMA_VERSION, parsed.schemaVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `schemaVersion above SCHEMA_VERSION constant is rejected`() {
        val json =
            """{"schemaVersion":${AppConstants.ExportConstants.SCHEMA_VERSION + 1},"exportedAt":0,"settings":{},"speedProfiles":[],"routes":[],"favoriteLocations":[]}"""
        SettingsExportCodec.parseExportData(json)
    }

    // -------------------------------------------------------------------------
    // Resilient parsing — unknown enum values fall back gracefully
    // -------------------------------------------------------------------------

    @Test
    fun `invalid speedUnit falls back to KMH`() {
        val json =
            """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"INVALID_UNIT","enabledWidgetFeatures":[]},"speedProfiles":[],"routes":[],"favoriteLocations":[]}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(SpeedUnit.KMH, parsed.settings.speedUnit)
    }

    @Test
    fun `unknown widget feature is skipped, known feature is preserved`() {
        val json =
            """{"schemaVersion":1,"exportedAt":0,"settings":{"speedUnit":"KMH","enabledWidgetFeatures":["UNKNOWN_FEATURE","JOYSTICK_TOGGLE"]},"speedProfiles":[],"routes":[],"favoriteLocations":[]}"""

        val parsed = SettingsExportCodec.parseExportData(json)

        assertEquals(1, parsed.settings.enabledWidgetFeatures.size)
        assertTrue(parsed.settings.enabledWidgetFeatures.contains(WidgetFeature.JOYSTICK_TOGGLE))
    }
}
