package com.locationjoystick.core.data

import app.cash.turbine.test
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.datastore.AppPreferencesDataSource
import com.locationjoystick.core.datastore.PreferencesDataSource
import com.locationjoystick.core.datastore.SettingsSnapshot
import com.locationjoystick.core.datastore.SpeedProfilePreferences
import com.locationjoystick.core.datastore.toEnumFeature
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    private lateinit var fakeDataSource: FakeAppPreferencesDataSource
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        fakeDataSource = FakeAppPreferencesDataSource()
        repository = SettingsRepository(fakeDataSource)
    }

    // getSpeedProfiles

    @Test
    fun `getSpeedProfiles returns walk, run, bike from preferences`() =
        runTest {
            fakeDataSource.speedProfilesFlow.value =
                SpeedProfilePreferences(
                    walkSpeedMs = 1.4,
                    runSpeedMs = 3.0,
                    bikeSpeedMs = 5.0,
                    activeProfileId = "walk",
                )

            repository.getSpeedProfiles().test {
                val profiles = awaitItem()
                assertEquals(3, profiles.size)
                assertEquals("walk", profiles[0].id)
                assertEquals(1.4, profiles[0].speedMetersPerSecond, 0.001)
                assertEquals("run", profiles[1].id)
                assertEquals(3.0, profiles[1].speedMetersPerSecond, 0.001)
                assertEquals("bike", profiles[2].id)
                assertEquals(5.0, profiles[2].speedMetersPerSecond, 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getSpeedProfiles emits updated values when preferences change`() =
        runTest {
            repository.getSpeedProfiles().test {
                awaitItem() // initial

                fakeDataSource.speedProfilesFlow.value =
                    SpeedProfilePreferences(
                        walkSpeedMs = 2.0,
                        runSpeedMs = 4.0,
                        bikeSpeedMs = 6.0,
                        activeProfileId = "run",
                    )

                val profiles = awaitItem()
                assertEquals(2.0, profiles[0].speedMetersPerSecond, 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getWalkSpeed, getRunSpeed, getBikeSpeed

    @Test
    fun `getWalkSpeed returns walk speed from preferences`() =
        runTest {
            fakeDataSource.speedProfilesFlow.value =
                SpeedProfilePreferences(
                    walkSpeedMs = 1.5,
                    runSpeedMs = 3.0,
                    bikeSpeedMs = 5.0,
                    activeProfileId = "walk",
                )

            repository.getWalkSpeed().test {
                assertEquals(1.5, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRunSpeed returns run speed from preferences`() =
        runTest {
            fakeDataSource.speedProfilesFlow.value =
                SpeedProfilePreferences(
                    walkSpeedMs = 1.4,
                    runSpeedMs = 3.5,
                    bikeSpeedMs = 5.0,
                    activeProfileId = "walk",
                )

            repository.getRunSpeed().test {
                assertEquals(3.5, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getBikeSpeed returns bike speed from preferences`() =
        runTest {
            fakeDataSource.speedProfilesFlow.value =
                SpeedProfilePreferences(
                    walkSpeedMs = 1.4,
                    runSpeedMs = 3.0,
                    bikeSpeedMs = 5.5,
                    activeProfileId = "walk",
                )

            repository.getBikeSpeed().test {
                assertEquals(5.5, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getActiveSpeedProfile

    @Test
    fun `getActiveSpeedProfile returns walk profile when active`() =
        runTest {
            fakeDataSource.speedProfilesFlow.value =
                SpeedProfilePreferences(
                    walkSpeedMs = 1.4,
                    runSpeedMs = 3.0,
                    bikeSpeedMs = 5.0,
                    activeProfileId = "walk",
                )

            repository.getActiveSpeedProfile().test {
                val profile = awaitItem()
                assertEquals("walk", profile.id)
                assertEquals("Walk", profile.name)
                assertEquals(1.4, profile.speedMetersPerSecond, 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getActiveSpeedProfile returns run profile when active`() =
        runTest {
            fakeDataSource.speedProfilesFlow.value =
                SpeedProfilePreferences(
                    walkSpeedMs = 1.4,
                    runSpeedMs = 3.0,
                    bikeSpeedMs = 5.0,
                    activeProfileId = "run",
                )

            repository.getActiveSpeedProfile().test {
                val profile = awaitItem()
                assertEquals("run", profile.id)
                assertEquals("Run", profile.name)
                assertEquals(3.0, profile.speedMetersPerSecond, 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getActiveSpeedProfile returns bike profile when active`() =
        runTest {
            fakeDataSource.speedProfilesFlow.value =
                SpeedProfilePreferences(
                    walkSpeedMs = 1.4,
                    runSpeedMs = 3.0,
                    bikeSpeedMs = 5.0,
                    activeProfileId = "bike",
                )

            repository.getActiveSpeedProfile().test {
                val profile = awaitItem()
                assertEquals("bike", profile.id)
                assertEquals("Bike", profile.name)
                assertEquals(5.0, profile.speedMetersPerSecond, 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getActiveSpeedProfile defaults to walk for unknown profile id`() =
        runTest {
            fakeDataSource.speedProfilesFlow.value =
                SpeedProfilePreferences(
                    walkSpeedMs = 1.4,
                    runSpeedMs = 3.0,
                    bikeSpeedMs = 5.0,
                    activeProfileId = "unknown",
                )

            repository.getActiveSpeedProfile().test {
                val profile = awaitItem()
                assertEquals("unknown", profile.id)
                assertEquals(1.4, profile.speedMetersPerSecond, 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getWidgetFeatures

    @Test
    fun `getWidgetFeatures returns mapped widget features`() =
        runTest {
            fakeDataSource.widgetItemsFlow.value = setOf("map_floating", "joystick_toggle")

            repository.getWidgetFeatures().test {
                val features = awaitItem()
                assertEquals(2, features.size)
                assertTrue(features.contains(WidgetFeature.MAP_FLOATING))
                assertTrue(features.contains(WidgetFeature.JOYSTICK_TOGGLE))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getWidgetFeatures filters out invalid keys`() =
        runTest {
            fakeDataSource.widgetItemsFlow.value = setOf("map_floating", "invalid_key", "joystick_lock")

            repository.getWidgetFeatures().test {
                val features = awaitItem()
                assertEquals(2, features.size)
                assertTrue(features.contains(WidgetFeature.MAP_FLOATING))
                assertTrue(features.contains(WidgetFeature.JOYSTICK_LOCK))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getWidgetFeatures returns empty for empty set`() =
        runTest {
            fakeDataSource.widgetItemsFlow.value = emptySet()

            repository.getWidgetFeatures().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getOnboardingComplete

    @Test
    fun `getOnboardingComplete returns false initially`() =
        runTest {
            repository.getOnboardingComplete().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getOnboardingComplete returns true after set`() =
        runTest {
            fakeDataSource.onboardingCompleteFlow.value = true

            repository.getOnboardingComplete().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getRoamingDefaults

    @Test
    fun `getRoamingDefaults returns defaults from data source`() =
        runTest {
            val expected =
                RoamingDefaults(
                    radiusMeters = 500.0,
                    distanceMeters = 1000.0,
                    speedProfileId = "walk",
                    followRoads = true,
                    returnToInitialLocation = true,
                )
            fakeDataSource.roamingDefaultsFlow.value = expected

            repository.getRoamingDefaults().test {
                val actual = awaitItem()
                assertEquals(500.0, actual.radiusMeters, 0.001)
                assertEquals(1000.0, actual.distanceMeters, 0.001)
                assertEquals("walk", actual.speedProfileId)
                assertTrue(actual.followRoads)
                assertTrue(actual.returnToInitialLocation)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setWalkSpeed, setRunSpeed, setBikeSpeed

    @Test
    fun `setWalkSpeed updates speed profiles flow`() =
        runTest {
            repository.setWalkSpeed(2.0)

            repository.getWalkSpeed().test {
                assertEquals(2.0, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRunSpeed updates speed profiles flow`() =
        runTest {
            repository.setRunSpeed(4.0)

            repository.getRunSpeed().test {
                assertEquals(4.0, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setBikeSpeed updates speed profiles flow`() =
        runTest {
            repository.setBikeSpeed(6.0)

            repository.getBikeSpeed().test {
                assertEquals(6.0, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setWalkSpeed RunSpeed BikeSpeed allow values above former 15 ms anti-cheat cap`() =
        runTest {
            repository.setWalkSpeed(50.0)
            repository.setRunSpeed(50.0)
            repository.setBikeSpeed(50.0)

            repository.getWalkSpeed().test {
                assertEquals(50.0, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
            repository.getRunSpeed().test {
                assertEquals(50.0, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
            repository.getBikeSpeed().test {
                assertEquals(50.0, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setActiveProfileId

    @Test
    fun `setActiveProfileId updates active profile`() =
        runTest {
            repository.setActiveProfileId("bike")

            repository.getActiveSpeedProfile().test {
                val profile = awaitItem()
                assertEquals("bike", profile.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setWidgetFeatures

    @Test
    fun `setWidgetFeatures stores widget feature keys`() =
        runTest {
            repository.setWidgetFeatures(listOf(WidgetFeature.SPEED_CYCLE, WidgetFeature.ROUTES_FLOATING))

            repository.getWidgetFeatures().test {
                val features = awaitItem()
                assertEquals(2, features.size)
                assertTrue(features.contains(WidgetFeature.SPEED_CYCLE))
                assertTrue(features.contains(WidgetFeature.ROUTES_FLOATING))
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setOnboardingComplete

    @Test
    fun `setOnboardingComplete sets onboarding to true`() =
        runTest {
            repository.setOnboardingComplete(true)

            repository.getOnboardingComplete().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // setSpeedUnit / getSpeedUnit

    @Test
    fun `getSpeedUnit returns KMH by default`() =
        runTest {
            repository.getSpeedUnit().test {
                assertEquals(SpeedUnit.KMH, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setSpeedUnit and getSpeedUnit round trip for MPH`() =
        runTest {
            repository.setSpeedUnit(SpeedUnit.MPH)

            repository.getSpeedUnit().test {
                assertEquals(SpeedUnit.MPH, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getSpeedUnit returns KMH for invalid stored value`() =
        runTest {
            fakeDataSource.speedUnitFlow.value = "INVALID_UNIT"

            repository.getSpeedUnit().test {
                assertEquals(SpeedUnit.KMH, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getRememberLastLocation / setRememberLastLocation

    @Test
    fun `getRememberLastLocation returns default false`() =
        runTest {
            repository.getRememberLastLocation().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRememberLastLocation enables remember last location`() =
        runTest {
            repository.setRememberLastLocation(true)

            repository.getRememberLastLocation().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getLastLocation / setLastLocation

    @Test
    fun `getLastLocation returns null when not set`() =
        runTest {
            repository.getLastLocation().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setLastLocation and getLastLocation round trip`() =
        runTest {
            val location = LatLng(48.8566, 2.3522)
            repository.setLastLocation(location)

            repository.getLastLocation().test {
                val actual = awaitItem()
                assertNotNull(actual)
                assertEquals(48.8566, actual!!.latitude, 0.0001)
                assertEquals(2.3522, actual.longitude, 0.0001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getJitterIdleRadius / setJitterIdleRadius

    @Test
    fun `getJitterIdleRadius returns default value`() =
        runTest {
            val result = repository.getJitterIdleRadius()
            result.test {
                val value = awaitItem()
                assertEquals(AppPreferencesDataSource.DEFAULT_JITTER_IDLE_RADIUS_METERS, value, 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setJitterIdleRadius updates value`() =
        runTest {
            repository.setJitterIdleRadius(15.0)

            repository.getJitterIdleRadius().test {
                assertEquals(15.0, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getJitterMovingRadius / setJitterMovingRadius

    @Test
    fun `setJitterMovingRadius updates value`() =
        runTest {
            repository.setJitterMovingRadius(25.0)

            repository.getJitterMovingRadius().test {
                assertEquals(25.0, awaitItem(), 0.001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getJitterIntervalSeconds / setJitterIntervalSeconds

    @Test
    fun `setJitterIntervalSeconds updates value`() =
        runTest {
            repository.setJitterIntervalSeconds(10)

            repository.getJitterIntervalSeconds().test {
                assertEquals(10, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // updateRoamingDefaults

    @Test
    fun `updateRoamingDefaults updates roaming defaults flow`() =
        runTest {
            val newDefaults =
                RoamingDefaults(
                    radiusMeters = 750.0,
                    distanceMeters = 2000.0,
                    speedProfileId = "bike",
                    followRoads = false,
                    returnToInitialLocation = false,
                )
            repository.updateRoamingDefaults(newDefaults)

            repository.getRoamingDefaults().test {
                val actual = awaitItem()
                assertEquals(750.0, actual.radiusMeters, 0.001)
                assertEquals(2000.0, actual.distanceMeters, 0.001)
                assertEquals("bike", actual.speedProfileId)
                assertFalse(actual.followRoads)
                assertFalse(actual.returnToInitialLocation)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getJitterSpeedIdleVariationPct returns default`() =
        runTest {
            repository.getJitterSpeedIdleVariationPct().test {
                assertEquals(AppPreferencesDataSource.DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setJitterSpeedIdleVariationPct persists value`() =
        runTest {
            repository.setJitterSpeedIdleVariationPct(20)
            repository.getJitterSpeedIdleVariationPct().test {
                assertEquals(20, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getJitterSpeedMovingVariationPct returns default`() =
        runTest {
            repository.getJitterSpeedMovingVariationPct().test {
                assertEquals(AppPreferencesDataSource.DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setJitterSpeedMovingVariationPct persists value`() =
        runTest {
            repository.setJitterSpeedMovingVariationPct(30)
            repository.getJitterSpeedMovingVariationPct().test {
                assertEquals(30, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getJitterIdleIntervalSeconds / setJitterIdleIntervalSeconds

    @Test
    fun `getJitterIdleIntervalSeconds returns default`() =
        runTest {
            repository.getJitterIdleIntervalSeconds().test {
                assertEquals(AppPreferencesDataSource.DEFAULT_JITTER_IDLE_INTERVAL_SECONDS, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setJitterIdleIntervalSeconds persists value`() =
        runTest {
            repository.setJitterIdleIntervalSeconds(8)
            repository.getJitterIdleIntervalSeconds().test {
                assertEquals(8, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getLastTeleportTime / setLastTeleportTime

    @Test
    fun `getLastTeleportTime returns 0 initially`() =
        runTest {
            repository.getLastTeleportTime().test {
                assertEquals(0L, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setLastTeleportTime persists value`() =
        runTest {
            repository.setLastTeleportTime(123456789L)
            repository.getLastTeleportTime().test {
                assertEquals(123456789L, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // getMapFollowsLocation / setMapFollowsLocation

    @Test
    fun `getMapFollowsLocation returns true by default`() =
        runTest {
            repository.getMapFollowsLocation().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setMapFollowsLocation persists false`() =
        runTest {
            repository.setMapFollowsLocation(false)
            repository.getMapFollowsLocation().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // realism getters / setters

    @Test
    fun `getRealismBearingHoldIdle returns default true`() =
        runTest {
            repository.getRealismBearingHoldIdle().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRealismBearingHoldIdle persists false`() =
        runTest {
            repository.setRealismBearingHoldIdle(false)
            repository.getRealismBearingHoldIdle().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRealismAltitudeEnabled returns default true`() =
        runTest {
            repository.getRealismAltitudeEnabled().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRealismAltitudeEnabled persists false`() =
        runTest {
            repository.setRealismAltitudeEnabled(false)
            repository.getRealismAltitudeEnabled().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRealismWarmupEnabled returns default false`() =
        runTest {
            repository.getRealismWarmupEnabled().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRealismWarmupEnabled persists true`() =
        runTest {
            repository.setRealismWarmupEnabled(true)
            repository.getRealismWarmupEnabled().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRealismSatelliteExtrasEnabled returns default true`() =
        runTest {
            repository.getRealismSatelliteExtrasEnabled().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRealismSatelliteExtrasEnabled persists false`() =
        runTest {
            repository.setRealismSatelliteExtrasEnabled(false)
            repository.getRealismSatelliteExtrasEnabled().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRealismSuspendedMockingEnabled returns default false`() =
        runTest {
            repository.getRealismSuspendedMockingEnabled().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRealismSuspendedMockingEnabled persists true`() =
        runTest {
            repository.setRealismSuspendedMockingEnabled(true)
            repository.getRealismSuspendedMockingEnabled().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getRealismPedometerMockingEnabled returns default false`() =
        runTest {
            repository.getRealismPedometerMockingEnabled().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRealismPedometerMockingEnabled persists true`() =
        runTest {
            repository.setRealismPedometerMockingEnabled(true)
            repository.getRealismPedometerMockingEnabled().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // sort prefs

    @Test
    fun `getRoutesSortNewestFirst returns true by default`() =
        runTest {
            repository.getRoutesSortNewestFirst().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getFavoritesSortNewestFirst returns true by default`() =
        runTest {
            repository.getFavoritesSortNewestFirst().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // hot locations

    @Test
    fun `getHotLocationsEnabled returns false by default`() =
        runTest {
            repository.getHotLocationsEnabled().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHotLocationsEnabled persists true`() =
        runTest {
            repository.setHotLocationsEnabled(true)
            repository.getHotLocationsEnabled().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // recent searches

    @Test
    fun `getRecentSearches returns empty list initially`() =
        runTest {
            repository.getRecentSearches().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `addRecentSearch adds an entry`() =
        runTest {
            repository.addRecentSearch("Tokyo", 35.6762, 139.6503)
            repository.getRecentSearches().test {
                val searches = awaitItem()
                assertEquals(1, searches.size)
                assertEquals("Tokyo", searches[0].displayName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // elevation

    @Test
    fun `getElevationTiltJitterDegrees returns default`() =
        runTest {
            repository.getElevationTiltJitterDegrees().test {
                assertEquals(AppPreferencesDataSource.DEFAULT_ELEVATION_TILT_JITTER_DEGREES, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setElevationTiltJitterDegrees persists value`() =
        runTest {
            repository.setElevationTiltJitterDegrees(3.0f)
            repository.getElevationTiltJitterDegrees().test {
                assertEquals(3.0f, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getElevationNoiseAmplitudeMs2 returns default`() =
        runTest {
            repository.getElevationNoiseAmplitudeMs2().test {
                assertEquals(AppPreferencesDataSource.DEFAULT_ELEVATION_NOISE_AMPLITUDE_MS2, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setElevationNoiseAmplitudeMs2 persists value`() =
        runTest {
            repository.setElevationNoiseAmplitudeMs2(0.5f)
            repository.getElevationNoiseAmplitudeMs2().test {
                assertEquals(0.5f, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

class FakeAppPreferencesDataSource : PreferencesDataSource {
    val speedProfilesFlow =
        MutableStateFlow(
            SpeedProfilePreferences(
                walkSpeedMs = AppPreferencesDataSource.DEFAULT_WALK_SPEED_MS,
                runSpeedMs = AppPreferencesDataSource.DEFAULT_RUN_SPEED_MS,
                bikeSpeedMs = AppPreferencesDataSource.DEFAULT_BIKE_SPEED_MS,
                activeProfileId = AppPreferencesDataSource.DEFAULT_ACTIVE_PROFILE_ID,
            ),
        )

    val widgetItemsFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_WIDGET_ITEMS)

    val roamingDefaultsFlow =
        MutableStateFlow(
            RoamingDefaults(
                radiusMeters = AppPreferencesDataSource.DEFAULT_ROAMING_RADIUS_METERS,
                distanceMeters = AppPreferencesDataSource.DEFAULT_ROAMING_DISTANCE_METERS,
                speedProfileId = AppPreferencesDataSource.DEFAULT_ROAMING_SPEED_PROFILE_ID,
                followRoads = AppPreferencesDataSource.DEFAULT_ROAMING_FOLLOW_ROADS,
                returnToInitialLocation = AppPreferencesDataSource.DEFAULT_ROAMING_RETURN_TO_START,
            ),
        )

    val onboardingCompleteFlow = MutableStateFlow(false)

    val speedUnitFlow = MutableStateFlow("KMH")

    val rememberLastLocationFlow = MutableStateFlow(false)

    val lastLocationFlow = MutableStateFlow<LatLng?>(null)

    val jitterIdleRadiusFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_JITTER_IDLE_RADIUS_METERS)

    val jitterMovingRadiusFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_JITTER_MOVING_RADIUS_METERS)

    val jitterIntervalSecondsFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_JITTER_INTERVAL_SECONDS)

    val jitterIdleIntervalSecondsFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_JITTER_IDLE_INTERVAL_SECONDS)

    val lastTeleportTimeFlow = MutableStateFlow(0L)

    val mapFollowsLocationFlow = MutableStateFlow(true)

    override fun getSpeedProfiles(): Flow<SpeedProfilePreferences> = speedProfilesFlow

    override suspend fun setWalkSpeed(ms: Double) {
        speedProfilesFlow.value =
            speedProfilesFlow.value.copy(
                walkSpeedMs = ms.coerceAtLeast(AppPreferencesDataSource.MIN_SPEED_MS),
            )
    }

    override suspend fun setRunSpeed(ms: Double) {
        speedProfilesFlow.value =
            speedProfilesFlow.value.copy(
                runSpeedMs = ms.coerceAtLeast(AppPreferencesDataSource.MIN_SPEED_MS),
            )
    }

    override suspend fun setBikeSpeed(ms: Double) {
        speedProfilesFlow.value =
            speedProfilesFlow.value.copy(
                bikeSpeedMs = ms.coerceAtLeast(AppPreferencesDataSource.MIN_SPEED_MS),
            )
    }

    override suspend fun setActiveProfileId(profileId: String) {
        speedProfilesFlow.value = speedProfilesFlow.value.copy(activeProfileId = profileId)
    }

    override fun getWidgetItems(): Flow<Set<String>> = widgetItemsFlow

    override suspend fun setWidgetItems(items: Set<String>) {
        widgetItemsFlow.value = items
    }

    override fun getRoamingDefaults(): Flow<RoamingDefaults> = roamingDefaultsFlow

    override suspend fun updateRoamingDefaults(defaults: RoamingDefaults) {
        roamingDefaultsFlow.value = defaults
    }

    override fun getOnboardingComplete(): Flow<Boolean> = onboardingCompleteFlow

    override suspend fun setOnboardingComplete(complete: Boolean) {
        onboardingCompleteFlow.value = complete
    }

    override fun getSpeedUnit(): Flow<String> = speedUnitFlow

    override suspend fun setSpeedUnit(unit: String) {
        speedUnitFlow.value = unit
    }

    override fun getRememberLastLocation(): Flow<Boolean> = rememberLastLocationFlow

    override suspend fun setRememberLastLocation(enabled: Boolean) {
        rememberLastLocationFlow.value = enabled
    }

    override fun getLastLocation(): Flow<LatLng?> = lastLocationFlow

    override suspend fun setLastLocation(location: LatLng) {
        lastLocationFlow.value = location
    }

    override fun getJitterIdleRadius(): Flow<Double> = jitterIdleRadiusFlow

    override fun getJitterMovingRadius(): Flow<Double> = jitterMovingRadiusFlow

    override fun getJitterIntervalSeconds(): Flow<Int> = jitterIntervalSecondsFlow

    override fun getJitterIdleIntervalSeconds(): Flow<Int> = jitterIdleIntervalSecondsFlow

    override suspend fun setJitterIdleIntervalSeconds(seconds: Int) {
        jitterIdleIntervalSecondsFlow.value =
            seconds.coerceIn(AppPreferencesDataSource.MIN_JITTER_INTERVAL_SECONDS, AppPreferencesDataSource.MAX_JITTER_INTERVAL_SECONDS)
    }

    override suspend fun setJitterIdleRadius(meters: Double) {
        jitterIdleRadiusFlow.value = meters.coerceIn(0.0, AppPreferencesDataSource.MAX_JITTER_RADIUS_METERS)
    }

    override suspend fun setJitterMovingRadius(meters: Double) {
        jitterMovingRadiusFlow.value = meters.coerceIn(0.0, AppPreferencesDataSource.MAX_JITTER_RADIUS_METERS)
    }

    override suspend fun setJitterIntervalSeconds(seconds: Int) {
        jitterIntervalSecondsFlow.value =
            seconds.coerceIn(AppPreferencesDataSource.MIN_JITTER_INTERVAL_SECONDS, AppPreferencesDataSource.MAX_JITTER_INTERVAL_SECONDS)
    }

    override fun getLastTeleportTime(): Flow<Long> = lastTeleportTimeFlow

    override suspend fun setLastTeleportTime(ms: Long) {
        lastTeleportTimeFlow.value = ms
    }

    override fun getMapFollowsLocation(): Flow<Boolean> = mapFollowsLocationFlow

    override suspend fun setMapFollowsLocation(enabled: Boolean) {
        mapFollowsLocationFlow.value = enabled
    }

    private val realismBearingHoldIdleFlow = MutableStateFlow(AppConstants.RealismConstants.BEARING_HOLD_ON_IDLE_DEFAULT)
    private val realismAltitudeEnabledFlow = MutableStateFlow(AppConstants.RealismConstants.ALTITUDE_ENABLED_DEFAULT)
    private val realismWarmupEnabledFlow = MutableStateFlow(AppConstants.RealismConstants.WARMUP_ENABLED_DEFAULT)
    private val realismSatelliteExtrasEnabledFlow = MutableStateFlow(AppConstants.RealismConstants.SATELLITE_EXTRAS_ENABLED_DEFAULT)
    private val realismSuspendedMockingEnabledFlow = MutableStateFlow(AppConstants.RealismConstants.SUSPENDED_MOCKING_ENABLED_DEFAULT)
    private val realismPedometerMockingEnabledFlow = MutableStateFlow(false)

    override fun getRealismBearingHoldIdle(): Flow<Boolean> = realismBearingHoldIdleFlow

    override fun getRealismAltitudeEnabled(): Flow<Boolean> = realismAltitudeEnabledFlow

    override fun getRealismWarmupEnabled(): Flow<Boolean> = realismWarmupEnabledFlow

    override fun getRealismSatelliteExtrasEnabled(): Flow<Boolean> = realismSatelliteExtrasEnabledFlow

    override fun getRealismSuspendedMockingEnabled(): Flow<Boolean> = realismSuspendedMockingEnabledFlow

    override fun getRealismPedometerMockingEnabled(): Flow<Boolean> = realismPedometerMockingEnabledFlow

    override suspend fun setRealismBearingHoldIdle(enabled: Boolean) {
        realismBearingHoldIdleFlow.value = enabled
    }

    override suspend fun setRealismAltitudeEnabled(enabled: Boolean) {
        realismAltitudeEnabledFlow.value = enabled
    }

    override suspend fun setRealismWarmupEnabled(enabled: Boolean) {
        realismWarmupEnabledFlow.value = enabled
    }

    override suspend fun setRealismSatelliteExtrasEnabled(enabled: Boolean) {
        realismSatelliteExtrasEnabledFlow.value = enabled
    }

    override suspend fun setRealismSuspendedMockingEnabled(enabled: Boolean) {
        realismSuspendedMockingEnabledFlow.value = enabled
    }

    override suspend fun setRealismPedometerMockingEnabled(enabled: Boolean) {
        realismPedometerMockingEnabledFlow.value = enabled
    }

    private val routesSortNewestFirstFlow = MutableStateFlow(true)
    private val favoritesSortNewestFirstFlow = MutableStateFlow(true)

    override fun getRoutesSortNewestFirst(): Flow<Boolean> = routesSortNewestFirstFlow

    override suspend fun setRoutesSortNewestFirst(newestFirst: Boolean) {
        routesSortNewestFirstFlow.value = newestFirst
    }

    override fun getFavoritesSortNewestFirst(): Flow<Boolean> = favoritesSortNewestFirstFlow

    override suspend fun setFavoritesSortNewestFirst(newestFirst: Boolean) {
        favoritesSortNewestFirstFlow.value = newestFirst
    }

    private val jitterSpeedIdleVariationPctFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT)
    private val jitterSpeedMovingVariationPctFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT)

    override fun getJitterSpeedIdleVariationPct(): Flow<Int> = jitterSpeedIdleVariationPctFlow

    override fun getJitterSpeedMovingVariationPct(): Flow<Int> = jitterSpeedMovingVariationPctFlow

    override suspend fun setJitterSpeedIdleVariationPct(pct: Int) {
        jitterSpeedIdleVariationPctFlow.value =
            pct.coerceIn(
                AppConstants.JitterConstants.SPEED_VARIATION_PCT_MIN,
                AppConstants.JitterConstants.SPEED_VARIATION_PCT_MAX,
            )
    }

    override suspend fun setJitterSpeedMovingVariationPct(pct: Int) {
        jitterSpeedMovingVariationPctFlow.value =
            pct.coerceIn(
                AppConstants.JitterConstants.SPEED_VARIATION_PCT_MIN,
                AppConstants.JitterConstants.SPEED_VARIATION_PCT_MAX,
            )
    }

    private val elevationTiltJitterDegreesFlow =
        MutableStateFlow(AppPreferencesDataSource.DEFAULT_ELEVATION_TILT_JITTER_DEGREES)
    private val elevationNoiseAmplitudeMs2Flow =
        MutableStateFlow(AppPreferencesDataSource.DEFAULT_ELEVATION_NOISE_AMPLITUDE_MS2)

    override fun getElevationTiltJitterDegrees(): Flow<Float> = elevationTiltJitterDegreesFlow

    override suspend fun setElevationTiltJitterDegrees(degrees: Float) {
        elevationTiltJitterDegreesFlow.value =
            degrees.coerceIn(
                AppConstants.ElevationConstants.MIN_TILT_JITTER_DEGREES,
                AppConstants.ElevationConstants.MAX_TILT_JITTER_DEGREES,
            )
    }

    override fun getElevationNoiseAmplitudeMs2(): Flow<Float> = elevationNoiseAmplitudeMs2Flow

    override suspend fun setElevationNoiseAmplitudeMs2(amplitude: Float) {
        elevationNoiseAmplitudeMs2Flow.value =
            amplitude.coerceIn(
                AppConstants.ElevationConstants.MIN_NOISE_AMPLITUDE_MS2,
                AppConstants.ElevationConstants.MAX_NOISE_AMPLITUDE_MS2,
            )
    }

    private val hotLocationsEnabledFlow = MutableStateFlow(false)

    override fun getHotLocationsEnabled(): Flow<Boolean> = hotLocationsEnabledFlow

    override suspend fun setHotLocationsEnabled(enabled: Boolean) {
        hotLocationsEnabledFlow.value = enabled
    }

    override fun getSelectedHotLocationIds(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setSelectedHotLocationIds(ids: Set<String>) = Unit

    override fun getHotRoutesEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setHotRoutesEnabled(enabled: Boolean) = Unit

    override fun getSelectedHotRouteIds(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setSelectedHotRouteIds(ids: Set<String>) = Unit

    val recentSearchesFlow = MutableStateFlow<List<RecentSearch>>(emptyList())

    override fun getRecentSearches(): Flow<List<RecentSearch>> = recentSearchesFlow

    override suspend fun addRecentSearch(
        displayName: String,
        lat: Double,
        lon: Double,
    ) {
        val updated =
            (listOf(RecentSearch(displayName, lat, lon)) + recentSearchesFlow.value)
                .distinctBy { it.displayName }
                .take(AppConstants.NominatimConstants.RECENT_SEARCHES_MAX_COUNT)
        recentSearchesFlow.value = updated
    }

    override fun getSettingsSnapshot(): Flow<SettingsSnapshot> =
        flowOf(
            SettingsSnapshot(
                walkSpeedMs = AppPreferencesDataSource.DEFAULT_WALK_SPEED_MS,
                runSpeedMs = AppPreferencesDataSource.DEFAULT_RUN_SPEED_MS,
                bikeSpeedMs = AppPreferencesDataSource.DEFAULT_BIKE_SPEED_MS,
                speedUnit = SpeedUnit.KMH,
                widgetFeatures = AppPreferencesDataSource.DEFAULT_WIDGET_ITEMS.mapNotNull { it.toEnumFeature<WidgetFeature>() }.toSet(),
                rememberLastLocation = false,
                mapFollowsLocation = true,
                jitterIdleRadius = AppPreferencesDataSource.DEFAULT_JITTER_IDLE_RADIUS_METERS,
                jitterMovingRadius = AppPreferencesDataSource.DEFAULT_JITTER_MOVING_RADIUS_METERS,
                jitterIntervalSeconds = AppPreferencesDataSource.DEFAULT_JITTER_INTERVAL_SECONDS,
                jitterIdleIntervalSeconds = AppPreferencesDataSource.DEFAULT_JITTER_IDLE_INTERVAL_SECONDS,
                realismBearingHoldIdle = true,
                realismAltitudeEnabled = true,
                realismWarmupEnabled = false,
                realismSatelliteExtrasEnabled = true,
                realismSuspendedMockingEnabled = false,
                realismPedometerMockingEnabled = false,
                jitterSpeedIdleVariationPct = AppPreferencesDataSource.DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT,
                jitterSpeedMovingVariationPct = AppPreferencesDataSource.DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT,
                elevationTiltJitterDegrees = AppPreferencesDataSource.DEFAULT_ELEVATION_TILT_JITTER_DEGREES,
                elevationNoiseAmplitudeMs2 = AppPreferencesDataSource.DEFAULT_ELEVATION_NOISE_AMPLITUDE_MS2,
                hotLocationsEnabled = false,
                selectedHotLocationIds = emptySet(),
                hotRoutesEnabled = false,
                selectedHotRouteIds = emptySet(),
                mapFabFeatures = emptySet(),
                roamingDefaults = roamingDefaultsFlow.value,
            ),
        )

    override suspend fun applySnapshot(snapshot: SettingsSnapshot) {
        speedProfilesFlow.value =
            speedProfilesFlow.value.copy(
                walkSpeedMs = snapshot.walkSpeedMs,
                runSpeedMs = snapshot.runSpeedMs,
                bikeSpeedMs = snapshot.bikeSpeedMs,
            )
        widgetItemsFlow.value = snapshot.widgetFeatures.map { it.name.lowercase() }.toSet()
    }
}
