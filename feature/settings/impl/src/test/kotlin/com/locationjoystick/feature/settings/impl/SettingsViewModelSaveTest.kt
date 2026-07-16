package com.locationjoystick.feature.settings.impl

import app.cash.turbine.test
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.datastore.AppPreferencesDataSource
import com.locationjoystick.core.datastore.PreferencesDataSource
import com.locationjoystick.core.datastore.SettingsSnapshot
import com.locationjoystick.core.datastore.SpeedProfilePreferences
import com.locationjoystick.core.datastore.toAppFeature
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.testing.FakeFavoriteDao
import com.locationjoystick.core.testing.FakeRouteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelSaveTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeDataSource: SaveTestPreferencesDataSource
    private lateinit var fakeSettingsRepo: SettingsRepository
    private lateinit var fakeFavoriteRepo: FavoriteRepository
    private lateinit var fakeRouteRepo: RouteRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        fakeDataSource = SaveTestPreferencesDataSource()
        fakeSettingsRepo = SettingsRepository(fakeDataSource)
        fakeFavoriteRepo = FavoriteRepository(FakeFavoriteDao())
        fakeRouteRepo = RouteRepository(routeDao = FakeRouteDao(), context = context)
        viewModel =
            SettingsViewModel(
                settingsRepository = fakeSettingsRepo,
                favoriteRepository = fakeFavoriteRepo,
                routeRepository = fakeRouteRepo,
                sensorPermissionBootstrap = SensorPermissionBootstrap(context),
                importExportRepository = ImportExportRepository(context),
                exportSyncServer = ExportSyncServer(),
                exportSyncClient = ExportSyncClient(),
                nsdCodeManager = NsdCodeManager(context),
                context = context,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveChanges triggers exactly one applySnapshot call`() =
        runTest(testDispatcher) {
            viewModel.setWalkSpeed(2.0)

            viewModel.saveChanges()

            assertEquals(1, fakeDataSource.applySnapshotCallCount)
        }

    @Test
    fun `saveChanges on success clears draft and emits Settings saved`() =
        runTest(testDispatcher) {
            // Subscribe to uiState so WhileSubscribed flows activate
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.userFeedback.test {
                viewModel.setWalkSpeed(2.0)
                assertTrue(viewModel.uiState.value.isDirty)

                viewModel.saveChanges()

                assertFalse(viewModel.uiState.value.isDirty)
                val feedback = awaitItem()
                assertFalse(feedback.isError)
                assertTrue(feedback.message.contains("saved", ignoreCase = true))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saveChanges on failure retains draft and emits error feedback`() =
        runTest(testDispatcher) {
            // Subscribe to uiState so WhileSubscribed flows activate
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.userFeedback.test {
                fakeDataSource.shouldThrowOnApplySnapshot = true
                viewModel.setWalkSpeed(2.0)
                assertTrue(viewModel.uiState.value.isDirty)

                viewModel.saveChanges()

                assertTrue(viewModel.uiState.value.isDirty)
                val feedback = awaitItem()
                assertTrue(feedback.isError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saveChanges includes roamingDefaults in the single applySnapshot call`() =
        runTest(testDispatcher) {
            val customRoaming =
                RoamingDefaults(
                    radiusMeters = 999.0,
                    distanceMeters = 5000.0,
                    speedProfileId = "bike",
                    followRoads = true,
                    returnToInitialLocation = true,
                )
            viewModel.updateRoamingDefaults(customRoaming)

            viewModel.saveChanges()

            assertEquals(1, fakeDataSource.applySnapshotCallCount)
            val snapshot = fakeDataSource.lastAppliedSnapshot!!
            assertEquals(999.0, snapshot.roamingDefaults.radiusMeters, 0.001)
            assertEquals(5000.0, snapshot.roamingDefaults.distanceMeters, 0.001)
            assertEquals("bike", snapshot.roamingDefaults.speedProfileId)
        }

    @Test
    fun `importSettings(ExportData) applies all settings atomically via single applySnapshot call`() =
        runTest(testDispatcher) {
            val customRoaming =
                RoamingDefaults(
                    radiusMeters = 750.0,
                    distanceMeters = 3000.0,
                    speedProfileId = "run",
                    followRoads = true,
                    returnToInitialLocation = false,
                )
            val exportData =
                ExportData(
                    settings =
                        AppSettings(
                            speedUnit = SpeedUnit.MPH,
                            enabledWidgetFeatures = setOf(AppFeature.JOYSTICK_TOGGLE),
                            bearingHoldOnIdle = false,
                            altitudeEnabled = false,
                            warmupEnabled = true,
                            satelliteExtrasEnabled = false,
                            suspendedMockingEnabled = true,
                            roamingDefaults = customRoaming,
                        ),
                    speedProfiles =
                        listOf(
                            SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.1),
                            SpeedProfile(id = "run", name = "Run", speedMetersPerSecond = 3.3),
                            SpeedProfile(id = "bike", name = "Bike", speedMetersPerSecond = 7.7),
                        ),
                    jitterIdleRadius = 2.5,
                    jitterMovingRadius = 4.0,
                    jitterIntervalSeconds = 15,
                    jitterIdleIntervalSeconds = 45,
                    jitterSpeedIdleVariationPct = 10,
                    jitterSpeedMovingVariationPct = 20,
                    hotLocationsEnabled = false,
                )

            viewModel.userFeedback.test {
                viewModel.importSettings(exportData)
                awaitItem() // wait for "Import complete" — ensures applyExportData fully ran
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, fakeDataSource.applySnapshotCallCount)
            val snapshot = fakeDataSource.lastAppliedSnapshot!!
            assertEquals(1.1, snapshot.walkSpeedMs, 0.001)
            assertEquals(3.3, snapshot.runSpeedMs, 0.001)
            assertEquals(7.7, snapshot.bikeSpeedMs, 0.001)
            assertEquals(SpeedUnit.MPH, snapshot.speedUnit)
            assertEquals(setOf(AppFeature.JOYSTICK_TOGGLE), snapshot.enabledWidgetFeatures)
            assertFalse(snapshot.realismBearingHoldIdle)
            assertFalse(snapshot.realismAltitudeEnabled)
            assertTrue(snapshot.realismWarmupEnabled)
            assertFalse(snapshot.realismSatelliteExtrasEnabled)
            assertTrue(snapshot.realismSuspendedMockingEnabled)
            assertEquals(2.5, snapshot.jitterIdleRadius, 0.001)
            assertEquals(4.0, snapshot.jitterMovingRadius, 0.001)
            assertEquals(15, snapshot.jitterIntervalSeconds)
            assertEquals(45, snapshot.jitterIdleIntervalSeconds)
            assertEquals(10, snapshot.jitterSpeedIdleVariationPct)
            assertEquals(20, snapshot.jitterSpeedMovingVariationPct)
            assertFalse(snapshot.hotLocationsEnabled)
            assertEquals(750.0, snapshot.roamingDefaults.radiusMeters, 0.001)
            assertEquals(3000.0, snapshot.roamingDefaults.distanceMeters, 0.001)
            assertEquals("run", snapshot.roamingDefaults.speedProfileId)
        }

    @Test
    fun `importSettings(ExportData) preserves rememberLastLocation and mapFollowsLocation`() =
        runTest(testDispatcher) {
            // The fake data source returns rememberLastLocation=false, mapFollowsLocation=true
            // from getSettingsSnapshot(). Importing must not reset them.
            viewModel.userFeedback.test {
                viewModel.importSettings(ExportData())
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val snapshot = fakeDataSource.lastAppliedSnapshot!!
            assertFalse(snapshot.rememberLastLocation)
            assertTrue(snapshot.mapFollowsLocation)
        }

    @Test
    fun `saveChanges with hotLocationsEnabled true upserts hot favorites`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.userFeedback.test {
                viewModel.setHotLocationsEnabled(true)
                viewModel.saveChanges()
                val feedback = awaitItem()
                assertFalse(feedback.isError)
                cancelAndIgnoreRemainingEvents()
            }
            // At least one hot favorite should be persisted
            val favorites = fakeFavoriteRepo.getFavorites().first()
            assertTrue("Expected hot favorites to be upserted", favorites.isNotEmpty())
        }

    @Test
    fun `saveChanges with hotLocationsEnabled false removes hot favorites`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            // First upsert hot locations
            viewModel.setHotLocationsEnabled(true)
            viewModel.saveChanges()
            val afterUpsert = fakeFavoriteRepo.getFavorites().first()
            assertTrue("Precondition: hot favorites upserted", afterUpsert.isNotEmpty())

            // Now disable and save — should remove them
            viewModel.setHotLocationsEnabled(false)
            viewModel.userFeedback.test {
                viewModel.saveChanges()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            val afterRemove = fakeFavoriteRepo.getFavorites().first().filter { it.id.startsWith("hot_") }
            assertTrue("Expected hot favorites to be removed", afterRemove.isEmpty())
        }

    @Test
    fun `onQrScanned with malformed url emits error feedback`() =
        runTest(testDispatcher) {
            viewModel.userFeedback.test {
                viewModel.onQrScanned("locationjoystick://export?host=1.2.3.4")
                val feedback = awaitItem()
                assertTrue(feedback.isError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `importSettings(ExportData) falls back to current speed when profile id absent from export`() =
        runTest(testDispatcher) {
            // Export omits the "bike" profile — bike speed must be retained from current snapshot.
            val currentBikeSpeed = AppPreferencesDataSource.DEFAULT_BIKE_SPEED_MS
            val exportData =
                ExportData(
                    speedProfiles =
                        listOf(
                            SpeedProfile(id = "walk", name = "Walk", speedMetersPerSecond = 1.0),
                            SpeedProfile(id = "run", name = "Run", speedMetersPerSecond = 3.0),
                            // "bike" intentionally absent
                        ),
                )

            viewModel.userFeedback.test {
                viewModel.importSettings(exportData)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val snapshot = fakeDataSource.lastAppliedSnapshot!!
            assertEquals(1.0, snapshot.walkSpeedMs, 0.001)
            assertEquals(3.0, snapshot.runSpeedMs, 0.001)
            assertEquals(currentBikeSpeed, snapshot.bikeSpeedMs, 0.001)
        }

    @Test
    fun `resetAllData clears favorites, routes, and preferences, and emits feedback`() =
        runTest(testDispatcher) {
            fakeFavoriteRepo.addFavorite(id = "fav1", name = "Home", position = LatLng(1.0, 2.0))
            fakeRouteRepo.insertRoute(Route(id = "route1", name = "Route 1", waypoints = emptyList()))
            assertTrue(fakeFavoriteRepo.getFavorites().first().isNotEmpty())
            assertTrue(fakeRouteRepo.getRoutes().first().isNotEmpty())

            viewModel.userFeedback.test {
                viewModel.resetAllData()
                val feedback = awaitItem()
                assertFalse(feedback.isError)
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(fakeFavoriteRepo.getFavorites().first().isEmpty())
            assertTrue(fakeRouteRepo.getRoutes().first().isEmpty())
            assertEquals(1, fakeDataSource.clearAllExceptOnboardingCallCount)
        }
}

// ---------------------------------------------------------------------------
// Fake PreferencesDataSource local to this test file
// ---------------------------------------------------------------------------

internal class SaveTestPreferencesDataSource : PreferencesDataSource {
    var applySnapshotCallCount = 0
    var lastAppliedSnapshot: SettingsSnapshot? = null
    var shouldThrowOnApplySnapshot = false

    private val speedProfilesFlow =
        MutableStateFlow(
            SpeedProfilePreferences(
                walkSpeedMs = AppPreferencesDataSource.DEFAULT_WALK_SPEED_MS,
                runSpeedMs = AppPreferencesDataSource.DEFAULT_RUN_SPEED_MS,
                bikeSpeedMs = AppPreferencesDataSource.DEFAULT_BIKE_SPEED_MS,
                activeProfileId = AppPreferencesDataSource.DEFAULT_ACTIVE_PROFILE_ID,
            ),
        )
    private val widgetItemsFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_WIDGET_ITEMS)
    private val mapItemsFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_MAP_FAB_ITEMS)
    private val featureOrderFlow = MutableStateFlow(AppFeature.DEFAULT_ORDER)
    private val roamingDefaultsFlow =
        MutableStateFlow(
            RoamingDefaults(
                radiusMeters = AppPreferencesDataSource.DEFAULT_ROAMING_RADIUS_METERS,
                distanceMeters = AppPreferencesDataSource.DEFAULT_ROAMING_DISTANCE_METERS,
                speedProfileId = AppPreferencesDataSource.DEFAULT_ROAMING_SPEED_PROFILE_ID,
                followRoads = AppPreferencesDataSource.DEFAULT_ROAMING_FOLLOW_ROADS,
                returnToInitialLocation = AppPreferencesDataSource.DEFAULT_ROAMING_RETURN_TO_START,
            ),
        )

    override fun getSettingsSnapshot(): Flow<SettingsSnapshot> =
        MutableStateFlow(
            SettingsSnapshot(
                slowWalkSpeedMs = speedProfilesFlow.value.slowWalkSpeedMs,
                walkSpeedMs = speedProfilesFlow.value.walkSpeedMs,
                runSpeedMs = speedProfilesFlow.value.runSpeedMs,
                bikeSpeedMs = speedProfilesFlow.value.bikeSpeedMs,
                driveSpeedMs = speedProfilesFlow.value.driveSpeedMs,
                speedUnit = SpeedUnit.KMH,
                featureOrder = featureOrderFlow.value,
                enabledWidgetFeatures = widgetItemsFlow.value.mapNotNull { it.toAppFeature() }.toSet(),
                enabledMapFeatures = mapItemsFlow.value.mapNotNull { it.toAppFeature() }.toSet(),
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
                hotLocationsEnabled = false,
                selectedHotLocationIds = emptySet(),
                hotRoutesEnabled = false,
                selectedHotRouteIds = emptySet(),
                roamingDefaults = roamingDefaultsFlow.value,
            ),
        )

    override suspend fun applySnapshot(snapshot: SettingsSnapshot) {
        if (shouldThrowOnApplySnapshot) throw RuntimeException("Simulated DataStore failure")
        applySnapshotCallCount++
        lastAppliedSnapshot = snapshot
        speedProfilesFlow.value =
            speedProfilesFlow.value.copy(
                slowWalkSpeedMs = snapshot.slowWalkSpeedMs,
                walkSpeedMs = snapshot.walkSpeedMs,
                runSpeedMs = snapshot.runSpeedMs,
                bikeSpeedMs = snapshot.bikeSpeedMs,
                driveSpeedMs = snapshot.driveSpeedMs,
            )
        widgetItemsFlow.value = snapshot.enabledWidgetFeatures.map { it.name.lowercase() }.toSet()
        mapItemsFlow.value = snapshot.enabledMapFeatures.map { it.name.lowercase() }.toSet()
        featureOrderFlow.value = snapshot.featureOrder
        roamingDefaultsFlow.value = snapshot.roamingDefaults
    }

    // --- minimal implementations for remaining interface methods ---

    override fun getSpeedProfiles(): Flow<SpeedProfilePreferences> = speedProfilesFlow

    override suspend fun setSlowWalkSpeed(ms: Double) {
        speedProfilesFlow.value = speedProfilesFlow.value.copy(slowWalkSpeedMs = ms)
    }

    override suspend fun setWalkSpeed(ms: Double) {
        speedProfilesFlow.value = speedProfilesFlow.value.copy(walkSpeedMs = ms)
    }

    override suspend fun setRunSpeed(ms: Double) {
        speedProfilesFlow.value = speedProfilesFlow.value.copy(runSpeedMs = ms)
    }

    override suspend fun setBikeSpeed(ms: Double) {
        speedProfilesFlow.value = speedProfilesFlow.value.copy(bikeSpeedMs = ms)
    }

    override suspend fun setDriveSpeed(ms: Double) {
        speedProfilesFlow.value = speedProfilesFlow.value.copy(driveSpeedMs = ms)
    }

    override suspend fun setActiveProfileId(profileId: String) {
        speedProfilesFlow.value = speedProfilesFlow.value.copy(activeProfileId = profileId)
    }

    override fun getWidgetItems(): Flow<Set<String>> = widgetItemsFlow

    override suspend fun setWidgetItems(items: Set<String>) {
        widgetItemsFlow.value = items
    }

    override fun getMapItems(): Flow<Set<String>> = mapItemsFlow

    override suspend fun setMapItems(items: Set<String>) {
        mapItemsFlow.value = items
    }

    override fun getFeatureOrder(): Flow<List<AppFeature>> = featureOrderFlow

    override suspend fun setFeatureOrder(order: List<AppFeature>) {
        featureOrderFlow.value = order
    }

    override fun getRoamingDefaults(): Flow<RoamingDefaults> = roamingDefaultsFlow

    override suspend fun updateRoamingDefaults(defaults: RoamingDefaults) {
        roamingDefaultsFlow.value = defaults
    }

    override fun getOnboardingComplete(): Flow<Boolean> = flowOf(false)

    override suspend fun setOnboardingComplete(complete: Boolean) = Unit

    override fun getSpeedUnit(): Flow<String> = flowOf("KMH")

    override suspend fun setSpeedUnit(unit: String) = Unit

    override fun getThemeMode(): Flow<String> = flowOf("DARK")

    override suspend fun setThemeMode(mode: String) = Unit

    override fun getRememberLastLocation(): Flow<Boolean> = flowOf(false)

    override suspend fun setRememberLastLocation(enabled: Boolean) = Unit

    override fun getLastLocation(): Flow<LatLng?> = flowOf(null)

    override suspend fun setLastLocation(location: LatLng) = Unit

    override fun getJitterIdleRadius(): Flow<Double> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_IDLE_RADIUS_METERS)

    override fun getJitterMovingRadius(): Flow<Double> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_MOVING_RADIUS_METERS)

    override fun getJitterIntervalSeconds(): Flow<Int> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_INTERVAL_SECONDS)

    override suspend fun setJitterIdleRadius(meters: Double) = Unit

    override suspend fun setJitterMovingRadius(meters: Double) = Unit

    override suspend fun setJitterIntervalSeconds(seconds: Int) = Unit

    override fun getJitterIdleIntervalSeconds(): Flow<Int> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_IDLE_INTERVAL_SECONDS)

    override suspend fun setJitterIdleIntervalSeconds(seconds: Int) = Unit

    override fun getLastTeleportTime(): Flow<Long> = flowOf(0L)

    override suspend fun setLastTeleportTime(ms: Long) = Unit

    override fun getMapFollowsLocation(): Flow<Boolean> = flowOf(true)

    override suspend fun setMapFollowsLocation(enabled: Boolean) = Unit

    override fun getRealismBearingHoldIdle(): Flow<Boolean> = flowOf(true)

    override fun getRealismAltitudeEnabled(): Flow<Boolean> = flowOf(true)

    override fun getRealismWarmupEnabled(): Flow<Boolean> = flowOf(false)

    override fun getRealismSatelliteExtrasEnabled(): Flow<Boolean> = flowOf(true)

    override fun getRealismSuspendedMockingEnabled(): Flow<Boolean> = flowOf(false)

    override fun getRealismPedometerMockingEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setRealismBearingHoldIdle(enabled: Boolean) = Unit

    override suspend fun setRealismAltitudeEnabled(enabled: Boolean) = Unit

    override suspend fun setRealismWarmupEnabled(enabled: Boolean) = Unit

    override suspend fun setRealismSatelliteExtrasEnabled(enabled: Boolean) = Unit

    override suspend fun setRealismSuspendedMockingEnabled(enabled: Boolean) = Unit

    override suspend fun setRealismPedometerMockingEnabled(enabled: Boolean) = Unit

    override fun getRecentSearches(): Flow<List<RecentSearch>> = flowOf(emptyList())

    override suspend fun addRecentSearch(
        displayName: String,
        lat: Double,
        lon: Double,
    ) = Unit

    override fun getRoutesSortNewestFirst(): Flow<Boolean> = flowOf(true)

    override suspend fun setRoutesSortNewestFirst(newestFirst: Boolean) = Unit

    override fun getFavoritesSortNewestFirst(): Flow<Boolean> = flowOf(true)

    override suspend fun setFavoritesSortNewestFirst(newestFirst: Boolean) = Unit

    override fun getJitterSpeedIdleVariationPct(): Flow<Int> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT)

    override fun getJitterSpeedMovingVariationPct(): Flow<Int> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT)

    override suspend fun setJitterSpeedIdleVariationPct(pct: Int) = Unit

    override suspend fun setJitterSpeedMovingVariationPct(pct: Int) = Unit

    override fun getHotLocationsEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setHotLocationsEnabled(enabled: Boolean) = Unit

    override fun getSelectedHotLocationIds(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setSelectedHotLocationIds(ids: Set<String>) = Unit

    override fun getHotRoutesEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setHotRoutesEnabled(enabled: Boolean) = Unit

    override fun getSelectedHotRouteIds(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setSelectedHotRouteIds(ids: Set<String>) = Unit

    override fun getFloatingMapQuickWalk(): Flow<Boolean> = flowOf(false)

    override fun getTapToWalkOverlayEnabled(): Flow<Boolean> = flowOf(false)

    override fun getTapToWalkScaleMpx(): Flow<Double> = flowOf(AppConstants.TapToWalkConstants.DEFAULT_SCALE_MPX)

    override fun getCompassTrackingEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setCompassTrackingEnabled(enabled: Boolean) = Unit

    override fun getCompassRegionCxPct(): Flow<Float> = flowOf(AppConstants.CompassTrackingConstants.DEFAULT_REGION_CX_PCT)

    override suspend fun setCompassRegionCxPct(cx: Float) = Unit

    override fun getCompassRegionCyPct(): Flow<Float> = flowOf(AppConstants.CompassTrackingConstants.DEFAULT_REGION_CY_PCT)

    override suspend fun setCompassRegionCyPct(cy: Float) = Unit

    override fun getCompassRegionRadiusPct(): Flow<Float> = flowOf(AppConstants.CompassTrackingConstants.DEFAULT_REGION_RADIUS_PCT)

    override suspend fun setCompassRegionRadiusPct(radius: Float) = Unit

    var clearAllExceptOnboardingCallCount = 0

    override suspend fun clearAllExceptOnboarding() {
        clearAllExceptOnboardingCallCount++
    }
}
