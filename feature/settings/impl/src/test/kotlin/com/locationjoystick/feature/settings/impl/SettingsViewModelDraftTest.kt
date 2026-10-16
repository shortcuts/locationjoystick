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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class SettingsViewModelDraftTest {
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
        viewModel =
            SettingsViewModel(
                settingsRepository = settingsRepo,
                captureCoordinatesRepository = captureRepository,
                favoriteRepository = FavoriteRepository(FakeFavoriteDao()),
                hotLocationsRepository = hotLocationsRepository,
                hotRoutesRepository = HotRoutesRepository(context),
                routeRepository = RouteRepository(routeDao = FakeRouteDao()),
                sensorPermissionBootstrap = SensorPermissionBootstrap(context),
                importExportRepository = ImportExportRepository(context),
                exportSyncServer = ExportSyncServer(),
                exportSyncClient = ExportSyncClient(),
                nsdCodeManager = NsdCodeManager(context),
                calibrationWalkOrchestrator =
                    io.mockk.mockk(relaxed = true) {
                        io.mockk.every { state } returns
                            kotlinx.coroutines.flow.MutableStateFlow(com.locationjoystick.core.location.CalibrationState.Idle)
                    },
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
    // Simple draft setters — each should mark dirty
    // -------------------------------------------------------------------------

    @Test
    fun `setSpeed marks dirty and stores speed in ms`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setSpeed("run", 3.6) // 3.6 km/h = 1.0 m/s
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(
                1.0,
                viewModel.uiState.value.speeds
                    .getValue("run"),
                0.01,
            )
        }

    @Test
    fun `setSpeed for a second profile marks dirty and stores speed in ms`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setSpeed("bike", 18.0) // 18 km/h = 5.0 m/s
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(
                5.0,
                viewModel.uiState.value.speeds
                    .getValue("bike"),
                0.01,
            )
        }

    @Test
    fun `setGuidedPath writes immediately and does not mark dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setGuidedPath(GuidedPath.POKEMON_GO)
            assertEquals("POKEMON_GO", fakeDataSource.lastSetGuidedPath)
            assertFalse(viewModel.uiState.value.isDirty)
        }

    @Test
    fun `setSpeedUnit marks dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setSpeedUnit(SpeedUnit.MPH)
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(SpeedUnit.MPH, viewModel.uiState.value.speedUnit)
        }

    @Test
    fun `setWidgetFeatures marks dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            val features = setOf(AppFeature.JOYSTICK_TOGGLE, AppFeature.SPEED_CYCLE)
            viewModel.setWidgetFeatures(features)
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(features, viewModel.uiState.value.enabledWidgetFeatures)
        }

    @Test
    fun `setRememberLastLocation marks dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setRememberLastLocation(true)
            assertTrue(viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.rememberLastLocation)
        }

    @Test
    fun `setMapFollowsLocation marks dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setMapFollowsLocation(false)
            assertTrue(viewModel.uiState.value.isDirty)
            assertFalse(viewModel.uiState.value.mapFollowsLocation)
        }

    @Test
    fun `jitter setters mark dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setJitterIdleRadius(3.0)
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(3.0, viewModel.uiState.value.jitterIdleRadiusMeters, 0.001)

            viewModel.setJitterMovingRadius(5.0)
            assertEquals(5.0, viewModel.uiState.value.jitterMovingRadiusMeters, 0.001)

            viewModel.setJitterMaxStepMeters(2.5)
            assertEquals(2.5, viewModel.uiState.value.jitterMaxStepMeters, 0.001)
        }

    @Test
    fun `realism setters mark dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setRealismBearingHoldIdle(false)
            assertFalse(viewModel.uiState.value.realismBearingHoldIdle)

            viewModel.setRealismAltitudeEnabled(false)
            assertFalse(viewModel.uiState.value.realismAltitudeEnabled)

            viewModel.setRealismWarmupEnabled(true)
            assertTrue(viewModel.uiState.value.realismWarmupEnabled)

            viewModel.setRealismSatelliteExtrasEnabled(false)
            assertFalse(viewModel.uiState.value.realismSatelliteExtrasEnabled)

            viewModel.setRealismSuspendedMockingEnabled(true)
            assertTrue(viewModel.uiState.value.realismSuspendedMockingEnabled)

            assertTrue(viewModel.uiState.value.isDirty)
        }

    @Test
    fun `setHideTeleportFeatures marks dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setHideTeleportFeatures(true)
            assertTrue(viewModel.uiState.value.hideTeleportFeatures)
            assertTrue(viewModel.uiState.value.isDirty)
        }

    @Test
    fun `setShowRouteJumpButtons marks dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setShowRouteJumpButtons(true)
            assertTrue(viewModel.uiState.value.showRouteJumpButtons)
            assertTrue(viewModel.uiState.value.isDirty)
        }

    @Test
    fun `jitter speed variation setters mark dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setJitterSpeedIdleVariationPct(15)
            assertEquals(15, viewModel.uiState.value.jitterSpeedIdleVariationPct)

            viewModel.setJitterSpeedMovingVariationPct(25)
            assertEquals(25, viewModel.uiState.value.jitterSpeedMovingVariationPct)

            viewModel.setJitterSpeedIdleWobbleProbabilityPct(30)
            assertEquals(30, viewModel.uiState.value.jitterSpeedIdleWobbleProbabilityPct)

            assertTrue(viewModel.uiState.value.isDirty)
        }

    // -------------------------------------------------------------------------
    // setHotLocationsEnabled logic
    // -------------------------------------------------------------------------

    @Test
    fun `setHotLocationsEnabled true with empty selection auto-selects all`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setHotLocationsEnabled(true)
            assertTrue(viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.hotLocationsEnabled)
            // All hot location IDs should be selected when enabling with empty selection
            val expectedIds =
                hotLocationsRepository.locations.value
                    .map { FavoriteRepository.idForLocation(it.name, it.city) }
                    .toSet()
            assertTrue(expectedIds.isNotEmpty())
            assertEquals(expectedIds, viewModel.uiState.value.selectedHotLocationIds)
        }

    @Test
    fun `setHotLocationsEnabled false marks disabled`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setHotLocationsEnabled(false)
            assertTrue(viewModel.uiState.value.isDirty)
            assertFalse(viewModel.uiState.value.hotLocationsEnabled)
        }

    @Test
    fun `setSelectedHotLocationIds stores ids`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            val ids = setOf("hot_paris", "hot_tokyo")
            viewModel.setSelectedHotLocationIds(ids)
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(ids, viewModel.uiState.value.selectedHotLocationIds)
        }

    // -------------------------------------------------------------------------
    // Tap-to-walk
    // -------------------------------------------------------------------------

    @Test
    fun `setFloatingMapQuickWalk marks dirty and updates state`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setFloatingMapQuickWalk(true)
            assertTrue(viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.floatingMapQuickWalk)
        }

    @Test
    fun `setTapToWalkOverlayEnabled marks dirty and updates state`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setTapToWalkOverlayEnabled(true)
            assertTrue(viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.tapToWalkOverlayEnabled)
        }

    @Test
    fun `setTapToWalkScaleMpx marks dirty and updates state`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setTapToWalkScaleMpx(0.25)
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(0.25, viewModel.uiState.value.tapToWalkScaleMpx, 1e-9)
        }

    // -------------------------------------------------------------------------
    // Capture — live keys shared with CaptureCoordinatesRepository (Settings > Menus section),
    // no draft/save step, same as the top-level Capture screen.
    // -------------------------------------------------------------------------

    @Test
    fun `setCaptureModeEnabled writes through the shared capture repository without marking dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setCaptureModeEnabled(true)
            assertTrue(viewModel.uiState.value.captureModeEnabled)
            assertFalse(viewModel.uiState.value.isDirty)
            assertTrue(captureRepository.captureModeEnabled.first())
        }

    @Test
    fun `setCaptureEnabled and setJumpEnabled write through the shared capture repository`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setCaptureEnabled(true)
            viewModel.setJumpEnabled(true)
            assertTrue(viewModel.uiState.value.captureEnabled)
            assertTrue(viewModel.uiState.value.jumpEnabled)
            assertFalse(viewModel.uiState.value.isDirty)
        }

    @Test
    fun `setCapturePreviousBrowserPackage persists the pass-through browser choice`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setCapturePreviousBrowserPackage("com.example.browser")
            assertEquals("com.example.browser", viewModel.uiState.value.capturePreviousBrowserPackage)
        }

    @Test
    fun `restoreCaptureDefaultBrowser turns capture off and sets captureSetupReset`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setCaptureModeEnabled(true)
            viewModel.setCaptureEnabled(true)
            viewModel.setJumpEnabled(true)

            viewModel.restoreCaptureDefaultBrowser()

            val state = viewModel.uiState.value
            assertFalse(state.captureModeEnabled)
            assertFalse(state.captureEnabled)
            assertFalse(state.jumpEnabled)
            assertTrue(state.captureSetupReset)
        }

    @Test
    fun `captureSetupReset clears when app is no longer default browser or setup is tapped`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.restoreCaptureDefaultBrowser()
            viewModel.onCaptureDefaultBrowserChecked(true)
            assertTrue(viewModel.uiState.value.captureSetupReset)

            viewModel.onCaptureDefaultBrowserChecked(false)
            assertFalse(viewModel.uiState.value.captureSetupReset)

            viewModel.restoreCaptureDefaultBrowser()
            viewModel.clearCaptureSetupReset()
            assertFalse(viewModel.uiState.value.captureSetupReset)
        }

    @Test
    fun `setLaunchAfterLinkPackage persists without marking dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setLaunchAfterLinkPackage("com.example.game")
            assertEquals("com.example.game", viewModel.uiState.value.launchAfterLinkPackage)
            assertFalse(viewModel.uiState.value.isDirty)
            viewModel.setLaunchAfterLinkPackage(null)
            assertNull(viewModel.uiState.value.launchAfterLinkPackage)
        }

    // -------------------------------------------------------------------------
    // discardChanges
    // -------------------------------------------------------------------------

    @Test
    fun `discardChanges clears dirty state`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setSpeed("run", 5.0)
            viewModel.setSpeedUnit(SpeedUnit.MPH)
            assertTrue(viewModel.uiState.value.isDirty)

            viewModel.discardChanges()

            assertFalse(viewModel.uiState.value.isDirty)
        }
}
