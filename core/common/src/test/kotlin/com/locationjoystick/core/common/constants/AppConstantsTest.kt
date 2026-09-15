package com.locationjoystick.core.common.constants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConstantsTest {
    @Test
    fun `DEFAULT_REPLAY_SPEED_MS equals WALK_SPEED_MPS`() {
        assertEquals(
            AppConstants.ProfileConstants.WALK_SPEED_MPS,
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS,
            0.0001,
        )
    }

    @Test
    fun `DEFAULT_REPLAY_SPEED_MS is within valid speed range`() {
        assertTrue(
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS >= AppConstants.ProfileConstants.MIN_SPEED_MS,
        )
        assertTrue(
            AppConstants.LocationConstants.DEFAULT_REPLAY_SPEED_MS <= AppConstants.ProfileConstants.MAX_SPEED_MS,
        )
    }

    @Test
    fun `WALK_SPEED_MPS is 10 kmh`() {
        assertEquals(10.0 / 3.6, AppConstants.ProfileConstants.WALK_SPEED_MPS, 0.001)
    }

    @Test
    fun `BIKE_SPEED_MPS is 18_5 kmh`() {
        assertEquals(18.5 / 3.6, AppConstants.ProfileConstants.BIKE_SPEED_MPS, 0.001)
    }

    @Test
    fun `DRIVE_SPEED_MPS is 60 kmh`() {
        assertEquals(60.0 / 3.6, AppConstants.ProfileConstants.DRIVE_SPEED_MPS, 0.001)
    }

    @Test
    fun `DEFAULT_ENABLED_SPEED_PROFILE_IDS is walk bike drive`() {
        assertEquals(
            setOf(
                AppConstants.ProfileConstants.PROFILE_ID_WALK,
                AppConstants.ProfileConstants.PROFILE_ID_BIKE,
                AppConstants.ProfileConstants.PROFILE_ID_DRIVE,
            ),
            AppConstants.ProfileConstants.DEFAULT_ENABLED_SPEED_PROFILE_IDS,
        )
    }

    @Test
    fun `planting roam default speed is bike`() {
        assertEquals(
            AppConstants.ProfileConstants.PROFILE_ID_BIKE,
            AppConstants.RoamingConstants.PLANTING_DEFAULT_SPEED_PROFILE_ID,
        )
    }

    @Test
    fun `planting default radius is 35 meters within clamp range`() {
        assertEquals(35.0, AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS, 0.0)
        assertTrue(
            AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS >=
                AppConstants.RouteConstants.PLANTING_MIN_RADIUS_METERS,
        )
        assertTrue(
            AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS <=
                AppConstants.RouteConstants.PLANTING_MAX_RADIUS_METERS,
        )
    }

    @Test
    fun `paste temp route id is reserved and not a hot route prefix`() {
        assertEquals("paste_temp_route", AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID)
        assertEquals("Temp Route from Paste", AppConstants.RouteConstants.PASTE_TEMP_ROUTE_NAME)
        assertTrue(!AppConstants.RouteConstants.PASTE_TEMP_ROUTE_ID.startsWith("hot_route_"))
    }

    @Test
    fun `teleport between delay defaults to eight seconds and clamps to ten minutes`() {
        assertEquals(8, AppConstants.RouteConstants.TELEPORT_BETWEEN_DEFAULT_DELAY_SECONDS)
        assertEquals(0, AppConstants.RouteConstants.TELEPORT_BETWEEN_MIN_DELAY_SECONDS)
        assertEquals(600, AppConstants.RouteConstants.TELEPORT_BETWEEN_MAX_DELAY_SECONDS)
    }

    @Test
    fun `whats new json is packed as an APK asset named after the version`() {
        assertEquals("0.20.16.json", AppConstants.WhatsNewConstants.assetFileName("0.20.16"))
        assertEquals("0.20.16.json", AppConstants.WhatsNewConstants.assetFileName("0.20.16-alpha1"))
    }

    @Test
    fun `changelog button opens the public user guide`() {
        assertEquals(
            "https://locationjoystick.shrtcts.fr/changelog.html",
            AppConstants.AppInfo.CHANGELOG_URL,
        )
    }
}
