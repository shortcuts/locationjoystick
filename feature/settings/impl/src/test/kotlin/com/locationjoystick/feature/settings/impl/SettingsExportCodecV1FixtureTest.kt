package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsExportCodecV1FixtureTest {
    private val v1Fixture =
        """
        {
          "schemaVersion": 1,
          "exportedAt": 1700000000000,
          "settings": {
            "speedUnit": "KMH",
            "enabledWidgetFeatures": ["JOYSTICK_TOGGLE", "SPEED_CYCLE"]
          },
          "speedProfiles": [],
          "routes": [],
          "favoriteLocations": [],
          "jitterIdleRadius": 0.5,
          "jitterMovingRadius": 1.5,
          "jitterIntervalSeconds": 3
        }
        """.trimIndent()

    @Test
    fun `v1 fixture parses successfully`() {
        val result = SettingsExportCodec.parseExportData(v1Fixture)
        assertEquals(1, result.schemaVersion)
    }

    @Test
    fun `v1 round-trip preserves all realism fields`() {
        val original =
            SettingsExportCodec.parseExportData(v1Fixture).copy(
                settings =
                    SettingsExportCodec.parseExportData(v1Fixture).settings.copy(
                        bearingHoldOnIdle = false,
                        altitudeEnabled = false,
                        warmupEnabled = true,
                        satelliteExtrasEnabled = false,
                        suspendedMockingEnabled = true,
                    ),
            )
        val serialized = SettingsExportCodec.serializeExportData(original)
        val parsed = SettingsExportCodec.parseExportData(serialized)
        assertEquals(false, parsed.settings.bearingHoldOnIdle)
        assertEquals(false, parsed.settings.altitudeEnabled)
        assertEquals(true, parsed.settings.warmupEnabled)
        assertEquals(false, parsed.settings.satelliteExtrasEnabled)
        assertEquals(true, parsed.settings.suspendedMockingEnabled)
    }

    @Test
    fun `version 0 throws`() {
        val v0Json = v1Fixture.replace(""""schemaVersion": 1""", """"schemaVersion": 0""")
        try {
            SettingsExportCodec.parseExportData(v0Json)
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported") == true)
        }
    }

    @Test
    fun `version 2 throws`() {
        val v2Json = v1Fixture.replace(""""schemaVersion": 1""", """"schemaVersion": 2""")
        try {
            SettingsExportCodec.parseExportData(v2Json)
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported") == true)
        }
    }
}
