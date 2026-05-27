package com.locationjoystick.core.data

import app.cash.turbine.test
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.datastore.AppPreferencesDataSource
import com.locationjoystick.core.datastore.PreferencesDataSource
import com.locationjoystick.core.datastore.SpeedProfilePreferences
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
                walkSpeedMs = ms.coerceIn(AppPreferencesDataSource.MIN_SPEED_MS, AppPreferencesDataSource.MAX_SPEED_MS),
            )
    }

    override suspend fun setRunSpeed(ms: Double) {
        speedProfilesFlow.value =
            speedProfilesFlow.value.copy(
                runSpeedMs = ms.coerceIn(AppPreferencesDataSource.MIN_SPEED_MS, AppPreferencesDataSource.MAX_SPEED_MS),
            )
    }

    override suspend fun setBikeSpeed(ms: Double) {
        speedProfilesFlow.value =
            speedProfilesFlow.value.copy(
                bikeSpeedMs = ms.coerceIn(AppPreferencesDataSource.MIN_SPEED_MS, AppPreferencesDataSource.MAX_SPEED_MS),
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

    override fun getRealismBearingHoldIdle(): Flow<Boolean> = realismBearingHoldIdleFlow

    override fun getRealismAltitudeEnabled(): Flow<Boolean> = realismAltitudeEnabledFlow

    override fun getRealismWarmupEnabled(): Flow<Boolean> = realismWarmupEnabledFlow

    override fun getRealismSatelliteExtrasEnabled(): Flow<Boolean> = realismSatelliteExtrasEnabledFlow

    override fun getRealismSuspendedMockingEnabled(): Flow<Boolean> = realismSuspendedMockingEnabledFlow

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
}
