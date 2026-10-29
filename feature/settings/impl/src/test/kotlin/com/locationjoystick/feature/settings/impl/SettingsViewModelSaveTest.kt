package com.locationjoystick.feature.settings.impl

import app.cash.turbine.test
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.common.util.NetworkUtils
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.BackupRepository
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.HotLocationsRepository
import com.locationjoystick.core.data.HotRoutesRepository
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
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.GeocodingProviderId
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MapTileSource
import com.locationjoystick.core.model.RealPositionSnapshot
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.testing.FakeFavoriteDao
import com.locationjoystick.core.testing.FakePreferencesDataStore
import com.locationjoystick.core.testing.FakeRouteDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelSaveTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeDataSource: SaveTestPreferencesDataSource
    private lateinit var fakeSettingsRepo: SettingsRepository
    private lateinit var fakeFavoriteRepo: FavoriteRepository
    private lateinit var fakeRouteRepo: RouteRepository
    private lateinit var backupRepository: BackupRepository
    private lateinit var viewModel: SettingsViewModel
    private val exportSyncServer = mockk<ExportSyncServer>(relaxed = true)
    private val nsdCodeManager = mockk<NsdCodeManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        fakeDataSource = SaveTestPreferencesDataSource()
        fakeSettingsRepo = SettingsRepository(fakeDataSource)
        fakeFavoriteRepo = FavoriteRepository(FakeFavoriteDao(), ioDispatcher = testDispatcher)
        fakeRouteRepo = RouteRepository(routeDao = FakeRouteDao(), ioDispatcher = testDispatcher)
        backupRepository = mockk(relaxed = true)
        val hotLocationsRepository = HotLocationsRepository(context).also { runBlocking { it.load() } }
        viewModel =
            SettingsViewModel(
                settingsRepository = fakeSettingsRepo,
                captureCoordinatesRepository = CaptureCoordinatesRepository(FakePreferencesDataStore()),
                favoriteRepository = fakeFavoriteRepo,
                hotLocationsRepository = hotLocationsRepository,
                hotRoutesRepository = HotRoutesRepository(context),
                routeRepository = fakeRouteRepo,
                backupRepository = backupRepository,
                sensorPermissionBootstrap = SensorPermissionBootstrap(context),
                importExportRepository = ImportExportRepository(context),
                exportSyncServer = exportSyncServer,
                exportSyncClient = ExportSyncClient(),
                nsdCodeManager = nsdCodeManager,
                context = context,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `flushChanges triggers exactly one applySnapshot call`() =
        runTest(testDispatcher) {
            viewModel.setSpeed("walk", 2.0)

            viewModel.flushChanges()

            assertEquals(1, fakeDataSource.applySnapshotCallCount)
        }

    @Test
    fun `flushChanges on success writes the value and emits no feedback`() =
        runTest(testDispatcher) {
            // Subscribe to uiState so WhileSubscribed flows activate
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.userFeedback.test {
                viewModel.setSpeed("walk", 2.0)

                viewModel.flushChanges()

                assertEquals(1, fakeDataSource.applySnapshotCallCount)
                assertEquals(2.0 / 3.6, fakeDataSource.lastAppliedSnapshot!!.walkSpeedMs, 0.001)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a setter writes once after the quiet period and not before`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setSpeed("walk", 2.0)

            advanceTimeBy(100)
            assertEquals(0, fakeDataSource.applySnapshotCallCount)

            advanceTimeBy(300)
            assertEquals(1, fakeDataSource.applySnapshotCallCount)
        }

    @Test
    fun `an edit made during a write stays pending`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            fakeDataSource.onApplySnapshot = { viewModel.setSpeed("run", 18.0) }
            viewModel.setSpeed("walk", 2.0)

            viewModel.flushChanges()

            assertEquals(
                5.0,
                viewModel.uiState.value.speeds
                    .getValue("run"),
                0.001,
            )
        }

    @Test
    fun `flushChanges with no pending change writes nothing`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

            viewModel.flushChanges()
            advanceTimeBy(1_000)

            assertEquals(0, fakeDataSource.applySnapshotCallCount)
        }

    @Test
    fun `flushChanges on failure retains draft and emits error feedback`() =
        runTest(testDispatcher) {
            // Subscribe to uiState so WhileSubscribed flows activate
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.userFeedback.test {
                fakeDataSource.shouldThrowOnApplySnapshot = true
                viewModel.setSpeed("walk", 2.0)

                viewModel.flushChanges()

                assertEquals(
                    2.0 / 3.6,
                    viewModel.uiState.value.speeds
                        .getValue("walk"),
                    0.001,
                )
                val feedback = awaitItem()
                assertTrue(feedback.isError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flushChanges includes roamingDefaults in the single applySnapshot call`() =
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

            viewModel.flushChanges()

            assertEquals(1, fakeDataSource.applySnapshotCallCount)
            val snapshot = fakeDataSource.lastAppliedSnapshot!!
            assertEquals(999.0, snapshot.roamingDefaults.radiusMeters, 0.001)
            assertEquals(5000.0, snapshot.roamingDefaults.distanceMeters, 0.001)
            assertEquals("bike", snapshot.roamingDefaults.speedProfileId)
        }

    @Test
    fun `flushChanges writes disabled geocoding providers and refuses to disable all`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setDisabledGeocodingProviders(setOf(GeocodingProviderId.NOMINATIM))
            viewModel.setDisabledGeocodingProviders(GeocodingProviderId.entries.toSet())

            viewModel.flushChanges()

            assertEquals(
                setOf(GeocodingProviderId.NOMINATIM),
                fakeDataSource.lastAppliedSnapshot!!.disabledGeocodingProviders,
            )
        }

    @Test
    fun `flushChanges includes planting radius in the applied snapshot`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setPlantingRadius(60.0)

            viewModel.flushChanges()

            assertEquals(60.0, fakeDataSource.lastAppliedSnapshot!!.plantingRadiusMeters, 0.001)
        }

    @Test
    fun `flushChanges includes altitude fields in the single applySnapshot call`() =
        runTest(testDispatcher) {
            // Subscribe to uiState so WhileSubscribed flows activate
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setRealismRealElevationEnabled(false)
            viewModel.setAltitudeJitterRadius(2.5)
            viewModel.setAltitudeOverrideButtonEnabled(true)

            viewModel.flushChanges()

            assertEquals(1, fakeDataSource.applySnapshotCallCount)
            val snapshot = fakeDataSource.lastAppliedSnapshot!!
            assertFalse(snapshot.realismRealElevationEnabled)
            assertEquals(2.5, snapshot.altitudeJitterRadiusMeters, 0.001)
            assertTrue(snapshot.altitudeOverrideButtonEnabled)
        }

    @Test
    fun `flushChanges keeps the mock location check bypass`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            fakeSettingsRepo.setBypassMockLocationCheck(true)
            viewModel.setSpeed("walk", 2.0)

            viewModel.flushChanges()

            assertTrue(fakeDataSource.lastAppliedSnapshot!!.bypassMockLocationCheck)
        }

    @Test
    fun `replace import that fails mid-insert keeps old data and reports error`() =
        runTest(testDispatcher) {
            coEvery { backupRepository.apply(any(), any(), any(), any()) } throws IllegalStateException("boom")

            viewModel.userFeedback.test {
                viewModel.importSettings(ExportData(settings = AppSettings(), speedProfiles = emptyList()), replace = true)
                assertTrue(awaitItem().isError)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(0, fakeDataSource.applySnapshotCallCount)
        }

    @Test
    fun `import that restores reconnect on without consent points to the disclosure`() =
        runTest(testDispatcher) {
            viewModel.userFeedback.test {
                viewModel.importSettings(ExportData(settings = AppSettings(reconnectTapEnabled = true)), replace = true)
                assertEquals(
                    "Import complete. Open Settings, Menus, Reconnect accessory to allow screen access.",
                    awaitItem().message,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `replace import restores the exported manual order`() =
        runTest(testDispatcher) {
            viewModel.userFeedback.test {
                viewModel.importSettings(ExportData(favoritesManualOrder = listOf("b", "a")), replace = true)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("b", "a"), fakeDataSource.favoritesManualOrderFlow.value)
        }

    @Test
    fun `add import keeps the current order`() =
        runTest(testDispatcher) {
            fakeDataSource.favoritesManualOrderFlow.value = listOf("x", "y")

            viewModel.userFeedback.test {
                viewModel.importSettings(
                    ExportData(favoriteLocations = listOf(FavoriteLocation("n", "N", LatLng(1.0, 2.0), 0L))),
                    replace = false,
                )
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("x", "y", "n"), fakeDataSource.favoritesManualOrderFlow.value)
        }

    @Test
    fun `file without the key keeps the current order`() =
        runTest(testDispatcher) {
            fakeDataSource.favoritesManualOrderFlow.value = listOf("x", "y")

            viewModel.userFeedback.test {
                viewModel.importSettings(ExportData(), replace = true)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("x", "y"), fakeDataSource.favoritesManualOrderFlow.value)
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
                            hideTeleportFeatures = true,
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
                    jitterMaxStepMeters = 2.0,
                    jitterSpeedIdleVariationPct = 10,
                    jitterSpeedMovingVariationPct = 20,
                    jitterSpeedIdleWobbleProbabilityPct = 40,
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
            assertTrue(snapshot.hideTeleportFeatures)
            assertEquals(2.5, snapshot.jitterIdleRadius, 0.001)
            assertEquals(4.0, snapshot.jitterMovingRadius, 0.001)
            assertEquals(2.0, snapshot.jitterMaxStepMeters, 0.001)
            assertEquals(10, snapshot.jitterSpeedIdleVariationPct)
            assertEquals(20, snapshot.jitterSpeedMovingVariationPct)
            assertEquals(40, snapshot.jitterSpeedIdleWobbleProbabilityPct)
            assertFalse(snapshot.hotLocationsEnabled)
            assertEquals(750.0, snapshot.roamingDefaults.radiusMeters, 0.001)
            assertEquals(3000.0, snapshot.roamingDefaults.distanceMeters, 0.001)
            assertEquals("run", snapshot.roamingDefaults.speedProfileId)
        }

    @Test
    fun `importSettings(ExportData) applies rememberLastLocation, mapFollowsLocation and tap to walk settings`() =
        runTest(testDispatcher) {
            // The fake snapshot has rememberLastLocation=false, mapFollowsLocation=true, tap to walk off.
            val exportData =
                ExportData(
                    settings =
                        AppSettings(
                            rememberLastLocation = true,
                            mapFollowsLocation = false,
                            tapToWalkOverlayEnabled = true,
                            floatingMapQuickWalk = true,
                        ),
                )
            viewModel.userFeedback.test {
                viewModel.importSettings(exportData)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            val snapshot = fakeDataSource.lastAppliedSnapshot!!
            assertTrue(snapshot.rememberLastLocation)
            assertFalse(snapshot.mapFollowsLocation)
            assertEquals(true, fakeDataSource.tapToWalkEnabledWrite)
            assertTrue(snapshot.floatingMapQuickWalk)
        }

    @Test
    fun `flushChanges with hotLocationsEnabled true upserts hot favorites`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.userFeedback.test {
                viewModel.setHotLocationsEnabled(true)
                viewModel.flushChanges()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            // At least one hot favorite should be persisted
            val favorites = fakeFavoriteRepo.getFavorites().first()
            assertTrue("Expected hot favorites to be upserted", favorites.isNotEmpty())
        }

    @Test
    fun `turning hot locations off clears Home when it was a hot favorite`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.userFeedback.test {
                viewModel.setHotLocationsEnabled(true)
                viewModel.flushChanges()
                fakeSettingsRepo.setHomeFavoriteId(
                    fakeFavoriteRepo
                        .getFavorites()
                        .first()
                        .first()
                        .id,
                )

                viewModel.setHotLocationsEnabled(false)
                viewModel.flushChanges()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(null, fakeSettingsRepo.getHomeFavoriteId().first())
        }

    @Test
    fun `flushChanges with hotLocationsEnabled false removes hot favorites`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.userFeedback.test {
                viewModel.setHotLocationsEnabled(true)
                viewModel.flushChanges()
                val afterUpsert = fakeFavoriteRepo.getFavorites().first()
                assertTrue("Precondition: hot favorites upserted", afterUpsert.isNotEmpty())

                viewModel.setHotLocationsEnabled(false)
                viewModel.flushChanges()
                cancelAndIgnoreRemainingEvents()
            }
            val afterRemove = fakeFavoriteRepo.getFavorites().first().filter { it.id.startsWith("hot_") }
            assertTrue("Expected hot favorites to be removed", afterRemove.isEmpty())
        }

    @Test
    fun `prepareQrExport flips isPreparingQrExport true then false around the async setup`() =
        runTest(testDispatcher) {
            viewModel.isPreparingQrExport.test {
                assertFalse(awaitItem())

                viewModel.prepareQrExport()

                assertTrue(awaitItem())
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stopQrExport during prepare leaves server stopped`() =
        runTest(testDispatcher) {
            val inPrepare = CountDownLatch(1)
            val release = CountDownLatch(1)
            val calls = CopyOnWriteArrayList<String>()
            mockkObject(NetworkUtils)
            try {
                every { NetworkUtils.getLocalIpAddress() } answers {
                    inPrepare.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    "1.2.3.4"
                }
                every { exportSyncServer.start(any(), any()) } answers {
                    calls += "start"
                    8080
                }
                every { exportSyncServer.stop() } answers { calls += "stop" }

                viewModel.prepareQrExport()
                assertTrue(inPrepare.await(5, TimeUnit.SECONDS))
                viewModel.stopQrExport()
                release.countDown()
                withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.isPreparingQrExport.first { !it } } }

                assertFalse(calls.toString(), calls.lastOrNull() == "start")
            } finally {
                unmockkObject(NetworkUtils)
            }
        }

    @Test
    fun `prepareQrExport exports the unsaved draft and the active speed profile`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setMapFollowsLocation(false)
            mockkObject(NetworkUtils)
            try {
                every { NetworkUtils.getLocalIpAddress() } returns "1.2.3.4"
                val json = slot<String>()
                every { exportSyncServer.start(any(), capture(json)) } returns 8080

                viewModel.prepareQrExport()
                withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.isPreparingQrExport.first { !it } } }

                val settings = SettingsExportCodec.parseExportData(json.captured).settings
                assertFalse(settings.mapFollowsLocation)
                assertEquals("walk", settings.activeSpeedProfileId)
            } finally {
                unmockkObject(NetworkUtils)
            }
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
                awaitItem()
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
    var onApplySnapshot: (() -> Unit)? = null

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
    private val enabledSpeedProfileIdsFlow = MutableStateFlow(AppPreferencesDataSource.DEFAULT_ENABLED_SPEED_PROFILE_IDS)
    private val bypassMockLocationCheckFlow = MutableStateFlow(false)
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
                jitterMaxStepMeters = AppPreferencesDataSource.DEFAULT_JITTER_MAX_STEP_METERS,
                realismBearingHoldIdle = true,
                realismAltitudeEnabled = true,
                realismWarmupEnabled = false,
                realismSatelliteExtrasEnabled = true,
                realismSuspendedMockingEnabled = false,
                jitterSpeedIdleVariationPct = AppPreferencesDataSource.DEFAULT_JITTER_SPEED_IDLE_VARIATION_PCT,
                jitterSpeedMovingVariationPct = AppPreferencesDataSource.DEFAULT_JITTER_SPEED_MOVING_VARIATION_PCT,
                hotLocationsEnabled = false,
                selectedHotLocationIds = emptySet(),
                hotRoutesEnabled = false,
                selectedHotRouteIds = emptySet(),
                roamingDefaults = roamingDefaultsFlow.value,
                enabledSpeedProfileIds = enabledSpeedProfileIdsFlow.value,
            ),
        )

    override suspend fun applySnapshot(snapshot: SettingsSnapshot) {
        if (shouldThrowOnApplySnapshot) throw RuntimeException("Simulated DataStore failure")
        applySnapshotCallCount++
        onApplySnapshot?.invoke()
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
        enabledSpeedProfileIdsFlow.value = snapshot.enabledSpeedProfileIds
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

    override fun getEnabledSpeedProfileIds(): Flow<Set<String>> = enabledSpeedProfileIdsFlow

    override suspend fun setEnabledSpeedProfileIds(ids: Set<String>) {
        enabledSpeedProfileIdsFlow.value = ids
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

    override fun getWhatsNewLastSeenVersion(): Flow<String> = flowOf("")

    override suspend fun setWhatsNewLastSeenVersion(version: String) = Unit

    override fun getRememberLastLocation(): Flow<Boolean> = flowOf(false)

    override suspend fun setRememberLastLocation(enabled: Boolean) = Unit

    override fun getLastLocation(): Flow<LatLng?> = flowOf(null)

    override suspend fun setLastLocation(location: LatLng) = Unit

    val favoritesManualOrderFlow = MutableStateFlow<List<String>>(emptyList())

    override fun getFavoritesManualOrder(): Flow<List<String>> = favoritesManualOrderFlow

    override suspend fun setFavoritesManualOrder(ids: List<String>) {
        favoritesManualOrderFlow.value = ids
    }

    val routesManualOrderFlow = MutableStateFlow<List<String>>(emptyList())

    override fun getRoutesManualOrder(): Flow<List<String>> = routesManualOrderFlow

    override suspend fun setRoutesManualOrder(ids: List<String>) {
        routesManualOrderFlow.value = ids
    }

    val homeFavoriteIdFlow = MutableStateFlow<String?>(null)

    override fun getHomeFavoriteId(): Flow<String?> = homeFavoriteIdFlow

    override suspend fun setHomeFavoriteId(id: String?) {
        homeFavoriteIdFlow.value = id
    }

    override fun getJitterIdleRadius(): Flow<Double> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_IDLE_RADIUS_METERS)

    override fun getJitterMovingRadius(): Flow<Double> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_MOVING_RADIUS_METERS)

    override fun getJitterMaxStepMeters(): Flow<Double> = flowOf(AppPreferencesDataSource.DEFAULT_JITTER_MAX_STEP_METERS)

    override suspend fun setJitterIdleRadius(meters: Double) = Unit

    override suspend fun setJitterMovingRadius(meters: Double) = Unit

    override suspend fun setJitterMaxStepMeters(meters: Double) = Unit

    override fun getLastTeleportTime(): Flow<Long> = flowOf(0L)

    override suspend fun setLastTeleportTime(ms: Long) = Unit

    override fun getMapFollowsLocation(): Flow<Boolean> = flowOf(true)

    override suspend fun setMapFollowsLocation(enabled: Boolean) = Unit

    override fun getDisabledGeocodingProviders(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setDisabledGeocodingProviders(names: Set<String>) = Unit

    override fun getGuidedPath(): Flow<String> = flowOf("DEFAULT")

    var lastSetGuidedPath: String? = null

    override suspend fun setGuidedPath(name: String) {
        lastSetGuidedPath = name
    }

    override fun getMapTileSource(): Flow<String> = flowOf(MapTileSource.DEFAULT.name)

    override suspend fun setMapTileSource(name: String) = Unit

    override fun getRealismBearingHoldIdle(): Flow<Boolean> = flowOf(true)

    override fun getRealismAltitudeEnabled(): Flow<Boolean> = flowOf(true)

    override fun getRealismWarmupEnabled(): Flow<Boolean> = flowOf(false)

    override fun getRealismSatelliteExtrasEnabled(): Flow<Boolean> = flowOf(true)

    override fun getRealismSuspendedMockingEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setRealismBearingHoldIdle(enabled: Boolean) = Unit

    override suspend fun setRealismAltitudeEnabled(enabled: Boolean) = Unit

    override suspend fun setRealismWarmupEnabled(enabled: Boolean) = Unit

    override suspend fun setRealismSatelliteExtrasEnabled(enabled: Boolean) = Unit

    override suspend fun setRealismSuspendedMockingEnabled(enabled: Boolean) = Unit

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

    override fun getJitterSpeedIdleWobbleProbabilityPct(): Flow<Int> =
        flowOf(AppPreferencesDataSource.DEFAULT_JITTER_SPEED_IDLE_WOBBLE_PROBABILITY_PCT)

    override suspend fun setJitterSpeedIdleWobbleProbabilityPct(pct: Int) = Unit

    override fun getHotLocationsEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setHotLocationsEnabled(enabled: Boolean) = Unit

    override fun getHideTeleportFeatures(): Flow<Boolean> = flowOf(false)

    override suspend fun setHideTeleportFeatures(enabled: Boolean) = Unit

    override fun getHideWidgetOverlay(): Flow<Boolean> = flowOf(false)

    override suspend fun setHideWidgetOverlay(enabled: Boolean) = Unit

    override fun getKeepWidgetOnIdle(): Flow<Boolean> = flowOf(false)

    override suspend fun setKeepWidgetOnIdle(enabled: Boolean) = Unit

    override fun getHideForegroundNotification(): Flow<Boolean> = flowOf(false)

    override suspend fun setHideForegroundNotification(enabled: Boolean) = Unit

    override fun getLastKnownPositionEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setLastKnownPositionEnabled(enabled: Boolean) = Unit

    override fun getRealPositionSnapshot(): Flow<RealPositionSnapshot?> = flowOf(null)

    override suspend fun setRealPositionSnapshot(snapshot: RealPositionSnapshot) = Unit

    override fun getShowRouteJumpButtons(): Flow<Boolean> = flowOf(false)

    override suspend fun setShowRouteJumpButtons(enabled: Boolean) = Unit

    override fun getBypassMockLocationCheck(): Flow<Boolean> = bypassMockLocationCheckFlow

    override suspend fun setBypassMockLocationCheck(enabled: Boolean) {
        bypassMockLocationCheckFlow.value = enabled
    }

    override fun getRealismRealElevationEnabled(): Flow<Boolean> = flowOf(true)

    override suspend fun setRealismRealElevationEnabled(enabled: Boolean) = Unit

    private val baseAltitudeOverrideFlow = MutableStateFlow<Double?>(null)

    override fun getBaseAltitudeOverride(): Flow<Double?> = baseAltitudeOverrideFlow

    override suspend fun setBaseAltitudeOverride(meters: Double) {
        baseAltitudeOverrideFlow.value = meters
    }

    override suspend fun clearBaseAltitudeOverride() {
        baseAltitudeOverrideFlow.value = null
    }

    override fun getAltitudeOverrideButtonEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setAltitudeOverrideButtonEnabled(enabled: Boolean) = Unit

    override fun getDebugStatsEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setDebugStatsEnabled(enabled: Boolean) = Unit

    override fun getAltitudeJitterRadius(): Flow<Double> = flowOf(AppConstants.RealismConstants.ALTITUDE_SIGMA_METERS)

    override suspend fun setAltitudeJitterRadius(meters: Double) = Unit

    override fun getPlantingRadius(): Flow<Double> = flowOf(AppConstants.RouteConstants.PLANTING_DEFAULT_RADIUS_METERS)

    override suspend fun setPlantingRadius(meters: Double) = Unit

    override fun getSelectedHotLocationIds(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setSelectedHotLocationIds(ids: Set<String>) = Unit

    override fun getHotRoutesEnabled(): Flow<Boolean> = flowOf(false)

    override suspend fun setHotRoutesEnabled(enabled: Boolean) = Unit

    override fun getSelectedHotRouteIds(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun setSelectedHotRouteIds(ids: Set<String>) = Unit

    override fun getFloatingMapQuickWalk(): Flow<Boolean> = flowOf(false)

    override fun getTapToWalkOverlayEnabled(): Flow<Boolean> = flowOf(false)

    var tapToWalkEnabledWrite: Boolean? = null

    override suspend fun setTapToWalkOverlayEnabled(enabled: Boolean) {
        tapToWalkEnabledWrite = enabled
    }

    override fun getAccessibilityDisclosureChoice(): Flow<String> = flowOf("")

    override fun getGroundCalibration(): Flow<com.locationjoystick.core.model.StoredGroundCalibration?> = flowOf(null)

    override suspend fun setGroundCalibration(value: com.locationjoystick.core.model.StoredGroundCalibration?) = Unit

    override suspend fun setAccessibilityDisclosureChoice(choice: String) = Unit

    private var reconnectTapEnabledValue = false

    override fun getReconnectTapEnabled(): Flow<Boolean> = flowOf(reconnectTapEnabledValue)

    override suspend fun setReconnectTapEnabled(enabled: Boolean) {
        reconnectTapEnabledValue = enabled
    }

    var clearAllExceptOnboardingCallCount = 0

    override suspend fun clearAllExceptOnboarding() {
        clearAllExceptOnboardingCallCount++
    }

    override fun getUpdateCheckLastCheckedAtMs(): Flow<Long> = flowOf(0L)

    override suspend fun setUpdateCheckLastCheckedAtMs(timestampMs: Long) = Unit

    override fun getUpdateCheckCachedLatestVersion(): Flow<String> = flowOf("")

    override suspend fun setUpdateCheckCachedLatestVersion(version: String) = Unit

    override fun getUpdateCheckDismissedVersion(): Flow<String> = flowOf("")

    override suspend fun setUpdateCheckDismissedVersion(version: String) = Unit
}
