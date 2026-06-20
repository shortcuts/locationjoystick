package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature
import com.locationjoystick.core.testing.FakeFavoriteDao
import com.locationjoystick.core.testing.FakeRouteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SettingsViewModelDraftTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        val fakeDataSource = SaveTestPreferencesDataSource()
        val settingsRepo = SettingsRepository(fakeDataSource)
        viewModel =
            SettingsViewModel(
                settingsRepository = settingsRepo,
                favoriteRepository = FavoriteRepository(FakeFavoriteDao()),
                routeRepository = RouteRepository(routeDao = FakeRouteDao(), context = context),
                sensorPermissionBootstrap = SensorPermissionBootstrap(context),
                importExportRepository = ImportExportRepository(context),
                exportSyncServer = ExportSyncServer(),
                exportSyncClient = ExportSyncClient(),
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
    fun `setRunSpeed marks dirty and stores speed in ms`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setRunSpeed(3.6) // 3.6 km/h = 1.0 m/s
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(1.0, viewModel.uiState.value.runSpeed, 0.01)
        }

    @Test
    fun `setBikeSpeed marks dirty and stores speed in ms`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setBikeSpeed(18.0) // 18 km/h = 5.0 m/s
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(5.0, viewModel.uiState.value.bikeSpeed, 0.01)
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
            val features = setOf(WidgetFeature.JOYSTICK_TOGGLE, WidgetFeature.SPEED_CYCLE)
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

            viewModel.setJitterIntervalSeconds(10)
            assertEquals(10, viewModel.uiState.value.jitterIntervalSeconds)

            viewModel.setJitterIdleIntervalSeconds(30)
            assertEquals(30, viewModel.uiState.value.jitterIdleIntervalSeconds)
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

            viewModel.setRealismPedometerMockingEnabled(true)
            assertTrue(viewModel.uiState.value.realismPedometerMockingEnabled)

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

            assertTrue(viewModel.uiState.value.isDirty)
        }

    @Test
    fun `elevation setters mark dirty`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setElevationTiltJitterDegrees(3.5f)
            assertEquals(3.5f, viewModel.uiState.value.elevationTiltJitterDegrees, 0.001f)

            viewModel.setElevationNoiseAmplitudeMs2(0.5f)
            assertEquals(0.5f, viewModel.uiState.value.elevationNoiseAmplitudeMs2, 0.001f)

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
                FavoriteRepository.HOT_LOCATIONS
                    .map { FavoriteRepository.idForLocation(it.name, it.city) }
                    .toSet()
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
    // discardChanges
    // -------------------------------------------------------------------------

    @Test
    fun `discardChanges clears dirty state`() =
        runTest(testDispatcher) {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            viewModel.setRunSpeed(5.0)
            viewModel.setSpeedUnit(SpeedUnit.MPH)
            assertTrue(viewModel.uiState.value.isDirty)

            viewModel.discardChanges()

            assertFalse(viewModel.uiState.value.isDirty)
        }
}
