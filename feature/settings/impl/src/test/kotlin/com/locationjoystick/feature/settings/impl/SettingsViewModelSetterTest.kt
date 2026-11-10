package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.HotLocationsRepository
import com.locationjoystick.core.data.HotRoutesRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.testing.FakeFavoriteDao
import com.locationjoystick.core.testing.FakePreferencesDataStore
import com.locationjoystick.core.testing.FakeRouteDao
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class SettingsViewModelSetterTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: SettingsViewModel
    private lateinit var fakeDataSource: SaveTestPreferencesDataSource
    private lateinit var captureRepository: CaptureCoordinatesRepository
    private lateinit var hotLocationsRepository: HotLocationsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        fakeDataSource = SaveTestPreferencesDataSource()
        val settingsRepo = SettingsRepository(fakeDataSource)
        captureRepository = CaptureCoordinatesRepository(FakePreferencesDataStore())
        hotLocationsRepository = HotLocationsRepository(context)
        runBlocking { hotLocationsRepository.load() }
        viewModel =
            SettingsViewModel(
                settingsRepository = settingsRepo,
                captureCoordinatesRepository = captureRepository,
                favoriteRepository = FavoriteRepository(FakeFavoriteDao()),
                hotLocationsRepository = hotLocationsRepository,
                hotRoutesRepository = HotRoutesRepository(context),
                routeRepository = RouteRepository(routeDao = FakeRouteDao()),
                backupRepository = mockk(relaxed = true),
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

    // -------------------------------------------------------------------------
    // convertMsToDisplay (companion)
    // -------------------------------------------------------------------------

    @Test
    fun `convertMsToDisplay KMH multiplies by 3_6`() {
        assertEquals(3.6, SettingsViewModel.convertMsToDisplay(1.0, SpeedUnit.KMH), 0.0001)
        assertEquals(0.0, SettingsViewModel.convertMsToDisplay(0.0, SpeedUnit.KMH), 0.0001)
    }

    @Test
    fun `convertMsToDisplay MPH multiplies by 2_237`() {
        assertEquals(2.237, SettingsViewModel.convertMsToDisplay(1.0, SpeedUnit.MPH), 0.0001)
    }

    // -------------------------------------------------------------------------
    // Setters write straight to the data source
    // -------------------------------------------------------------------------

    @Test
    fun `screen state starts Loading then becomes Loaded and never reverts`() =
        runTest(testDispatcher) {
            assertEquals(SettingsScreenState.Loading, viewModel.screenState.value)
            val seen = mutableListOf<SettingsScreenState>()
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect { seen += it } }
            advanceUntilIdle()
            viewModel.setSpeed("run", 3.6)
            advanceUntilIdle()
            val firstLoaded = seen.indexOfFirst { it is SettingsScreenState.Loaded }
            assertTrue(firstLoaded >= 0)
            assertTrue(seen.drop(firstLoaded).all { it is SettingsScreenState.Loaded })
        }

    @Test
    fun `setSpeed stores speed in ms`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setSpeed("run", 3.6) // 3.6 km/h = 1.0 m/s
            advanceUntilIdle()
            assertEquals(
                1.0,
                viewModel.loaded.speeds
                    .getValue("run"),
                0.01,
            )
        }

    @Test
    fun `setSpeed for a second profile stores speed in ms`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setSpeed("bike", 18.0) // 18 km/h = 5.0 m/s
            advanceUntilIdle()
            assertEquals(
                5.0,
                viewModel.loaded.speeds
                    .getValue("bike"),
                0.01,
            )
        }

    @Test
    fun `setGuidedPath writes immediately and does not`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setGuidedPath(GuidedPath.POKEMON_GO)
            assertEquals("POKEMON_GO", fakeDataSource.lastSetGuidedPath)
        }

    @Test
    fun `setSpeedUnit`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setSpeedUnit(SpeedUnit.MPH)
            assertEquals(SpeedUnit.MPH, viewModel.loaded.speedUnit)
        }

    @Test
    fun `setWidgetFeatures`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            val features = setOf(AppFeature.JOYSTICK_TOGGLE, AppFeature.SPEED_CYCLE)
            viewModel.setWidgetFeatures(features)
            assertEquals(features, viewModel.loaded.enabledWidgetFeatures)
        }

    @Test
    fun `setRememberLastLocation`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setRememberLastLocation(true)
            assertTrue(viewModel.loaded.rememberLastLocation)
        }

    @Test
    fun `setMapFollowsLocation`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setMapFollowsLocation(false)
            assertFalse(viewModel.loaded.mapFollowsLocation)
        }

    @Test
    fun `jitter setters`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setJitterIdleRadius(3.0)
            assertEquals(3.0, viewModel.loaded.jitterIdleRadiusMeters, 0.001)

            viewModel.setJitterMovingRadius(5.0)
            assertEquals(5.0, viewModel.loaded.jitterMovingRadiusMeters, 0.001)

            viewModel.setJitterMaxStepMeters(2.5)
            assertEquals(2.5, viewModel.loaded.jitterMaxStepMeters, 0.001)
        }

    @Test
    fun `realism setters`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setRealismBearingHoldIdle(false)
            assertFalse(viewModel.loaded.realismBearingHoldIdle)

            viewModel.setRealismAltitudeEnabled(false)
            assertFalse(viewModel.loaded.realismAltitudeEnabled)

            viewModel.setRealismWarmupEnabled(true)
            assertTrue(viewModel.loaded.realismWarmupEnabled)

            viewModel.setRealismSatelliteExtrasEnabled(false)
            assertFalse(viewModel.loaded.realismSatelliteExtrasEnabled)

            viewModel.setRealismSuspendedMockingEnabled(true)
            assertTrue(viewModel.loaded.realismSuspendedMockingEnabled)
        }

    @Test
    fun `setHideTeleportFeatures`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setHideTeleportFeatures(true)
            assertTrue(viewModel.loaded.hideTeleportFeatures)
        }

    @Test
    fun `setShowRouteJumpButtons`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setShowRouteJumpButtons(true)
            assertTrue(viewModel.loaded.showRouteJumpButtons)
        }

    @Test
    fun `setLastKnownPositionEnabled`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setLastKnownPositionEnabled(true)
            assertTrue(viewModel.loaded.lastKnownPositionEnabled)
        }

    @Test
    fun `jitter speed variation setters`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setJitterSpeedIdleVariationPct(15)
            assertEquals(15, viewModel.loaded.jitterSpeedIdleVariationPct)

            viewModel.setJitterSpeedMovingVariationPct(25)
            assertEquals(25, viewModel.loaded.jitterSpeedMovingVariationPct)

            viewModel.setJitterSpeedIdleWobbleProbabilityPct(30)
            assertEquals(30, viewModel.loaded.jitterSpeedIdleWobbleProbabilityPct)
        }

    // -------------------------------------------------------------------------
    // setHotLocationsEnabled logic
    // -------------------------------------------------------------------------

    @Test
    fun `setHotLocationsEnabled true with empty selection auto-selects all`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setHotLocationsEnabled(true)
            assertTrue(viewModel.loaded.hotLocationsEnabled)
            // All hot location IDs should be selected when enabling with empty selection
            val expectedIds =
                hotLocationsRepository.locations.value
                    .map { FavoriteRepository.idForLocation(it.name, it.city) }
                    .toSet()
            assertTrue(expectedIds.isNotEmpty())
            assertEquals(expectedIds, viewModel.loaded.selectedHotLocationIds)
        }

    @Test
    fun `setHotLocationsEnabled false marks disabled`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setHotLocationsEnabled(false)
            assertFalse(viewModel.loaded.hotLocationsEnabled)
        }

    @Test
    fun `setSelectedHotLocationIds stores ids`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            val ids = setOf("hot_paris", "hot_tokyo")
            viewModel.setSelectedHotLocationIds(ids)
            assertEquals(ids, viewModel.loaded.selectedHotLocationIds)
        }

    // -------------------------------------------------------------------------
    // Tap-to-walk
    // -------------------------------------------------------------------------

    @Test
    fun `setFloatingMapQuickWalk updates state`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setFloatingMapQuickWalk(true)
            assertTrue(viewModel.loaded.floatingMapQuickWalk)
        }

    @Test
    fun `setTapToWalkOverlayEnabled saves at once`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setTapToWalkOverlayEnabled(true)
            assertEquals(true, fakeDataSource.tapToWalkEnabledWrite)
        }

    // -------------------------------------------------------------------------
    // Capture — live keys shared with CaptureCoordinatesRepository (Settings > Menus section),
    // same as the top-level Capture screen.
    // -------------------------------------------------------------------------

    @Test
    fun `setCaptureModeEnabled writes through the shared capture repository`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setCaptureModeEnabled(true)
            assertTrue(viewModel.loaded.captureModeEnabled)
            assertTrue(captureRepository.captureModeEnabled.first())
        }

    @Test
    fun `setCaptureEnabled and setJumpEnabled write through the shared capture repository`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setCaptureEnabled(true)
            viewModel.setJumpEnabled(true)
            assertTrue(viewModel.loaded.captureEnabled)
            assertTrue(viewModel.loaded.jumpEnabled)
        }

    @Test
    fun `setCapturePreviousBrowserPackage persists the pass-through browser choice`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setCapturePreviousBrowserPackage("com.example.browser")
            assertEquals("com.example.browser", viewModel.loaded.capturePreviousBrowserPackage)
        }

    @Test
    fun `restoreCaptureDefaultBrowser turns capture off and sets captureSetupReset`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setCaptureModeEnabled(true)
            viewModel.setCaptureEnabled(true)
            viewModel.setJumpEnabled(true)

            viewModel.restoreCaptureDefaultBrowser()

            val state = viewModel.loaded
            assertFalse(state.captureModeEnabled)
            assertFalse(state.captureEnabled)
            assertFalse(state.jumpEnabled)
            assertTrue(state.captureSetupReset)
        }

    @Test
    fun `captureSetupReset clears when app is no longer default browser or setup is tapped`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.restoreCaptureDefaultBrowser()
            viewModel.onCaptureDefaultBrowserChecked(true)
            assertTrue(viewModel.loaded.captureSetupReset)

            viewModel.onCaptureDefaultBrowserChecked(false)
            assertFalse(viewModel.loaded.captureSetupReset)

            viewModel.restoreCaptureDefaultBrowser()
            viewModel.clearCaptureSetupReset()
            assertFalse(viewModel.loaded.captureSetupReset)
        }

    @Test
    fun `setLaunchAfterLinkPackage persists`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.screenState.collect {} }
            viewModel.setLaunchAfterLinkPackage("com.example.game")
            assertEquals("com.example.game", viewModel.loaded.launchAfterLinkPackage)
            viewModel.setLaunchAfterLinkPackage(null)
            assertNull(viewModel.loaded.launchAfterLinkPackage)
        }
}
