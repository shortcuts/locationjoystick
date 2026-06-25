package com.locationjoystick.feature.settings.impl

import app.cash.turbine.test
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.root.SensorPermissionBootstrap
import com.locationjoystick.core.common.util.NsdCodeManager
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.testing.FakeFavoriteDao
import com.locationjoystick.core.testing.FakeRouteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Regression: validation range `0.1..15.0` was applied to the display value (km/h or mph)
 * instead of m/s, blocking speeds above ~15 km/h. Any positive display speed must now
 * convert and save correctly without an upper cap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpeedProfileInputTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeDataSource: SaveTestPreferencesDataSource
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        fakeDataSource = SaveTestPreferencesDataSource()
        viewModel =
            SettingsViewModel(
                settingsRepository = SettingsRepository(fakeDataSource),
                favoriteRepository = FavoriteRepository(FakeFavoriteDao()),
                routeRepository = RouteRepository(routeDao = FakeRouteDao(), context = context),
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
    // High display-unit speeds (regression: previously blocked above 15.0)
    // -------------------------------------------------------------------------

    @Test
    fun `setWalkSpeed 50 kmh saves correct ms value`() =
        runTest(testDispatcher) {
            viewModel.uiState.test {
                awaitItem() // consume initial loading state
                viewModel.userFeedback.test {
                    viewModel.setWalkSpeed(50.0) // 50 km/h — was rejected by old 0.1..15.0 range
                    viewModel.saveChanges()
                    awaitItem() // wait for "Settings saved" feedback

                    val saved = fakeDataSource.lastAppliedSnapshot!!.walkSpeedMs
                    assertEquals(50.0 / 3.6, saved, 0.01)
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setBikeSpeed 100 kmh saves correct ms value`() =
        runTest(testDispatcher) {
            viewModel.uiState.test {
                awaitItem() // consume initial loading state
                viewModel.userFeedback.test {
                    viewModel.setBikeSpeed(100.0) // 100 km/h ≈ 27.8 m/s
                    viewModel.saveChanges()
                    awaitItem() // wait for "Settings saved" feedback

                    val saved = fakeDataSource.lastAppliedSnapshot!!.bikeSpeedMs
                    assertEquals(100.0 / 3.6, saved, 0.01)
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setRunSpeed 20 kmh saves correct ms value`() =
        runTest(testDispatcher) {
            viewModel.uiState.test {
                awaitItem() // consume initial loading state
                viewModel.userFeedback.test {
                    viewModel.setRunSpeed(20.0) // 20 km/h — also above old 15.0 cap
                    viewModel.saveChanges()
                    awaitItem() // wait for "Settings saved" feedback

                    val saved = fakeDataSource.lastAppliedSnapshot!!.runSpeedMs
                    assertEquals(20.0 / 3.6, saved, 0.01)
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // Anti-cheat warning threshold — stored m/s must cross the right boundary
    // -------------------------------------------------------------------------

    @Test
    fun `speed above anti-cheat threshold is stored above threshold`() =
        runTest(testDispatcher) {
            viewModel.uiState.test {
                awaitItem() // consume initial loading state
                viewModel.userFeedback.test {
                    // 40 km/h = 11.1 m/s > ANTI_CHEAT_WARNING_THRESHOLD_MS (8.0 m/s)
                    viewModel.setWalkSpeed(40.0)
                    viewModel.saveChanges()
                    awaitItem() // wait for "Settings saved" feedback

                    val savedMs = fakeDataSource.lastAppliedSnapshot!!.walkSpeedMs
                    assertTrue(savedMs > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS)
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `speed below anti-cheat threshold is stored below threshold`() =
        runTest(testDispatcher) {
            viewModel.uiState.test {
                awaitItem() // consume initial loading state
                viewModel.userFeedback.test {
                    // 10 km/h = 2.78 m/s < ANTI_CHEAT_WARNING_THRESHOLD_MS (8.0 m/s)
                    viewModel.setWalkSpeed(10.0)
                    viewModel.saveChanges()
                    awaitItem() // wait for "Settings saved" feedback

                    val savedMs = fakeDataSource.lastAppliedSnapshot!!.walkSpeedMs
                    assertFalse(savedMs > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS)
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `speed exactly at anti-cheat threshold boundary is not above threshold`() =
        runTest(testDispatcher) {
            viewModel.uiState.test {
                awaitItem() // consume initial loading state
                viewModel.userFeedback.test {
                    // Threshold is 8.0 m/s = 28.8 km/h — strict > so boundary value must NOT trigger
                    val thresholdKmh = AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS * 3.6
                    viewModel.setBikeSpeed(thresholdKmh)
                    viewModel.saveChanges()
                    awaitItem() // wait for "Settings saved" feedback

                    val savedMs = fakeDataSource.lastAppliedSnapshot!!.bikeSpeedMs
                    assertFalse(savedMs > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS)
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // convertMsToDisplay — used by SpeedProfileInput to render the display value
    // -------------------------------------------------------------------------

    @Test
    fun `convertMsToDisplay kmh multiplies by 3_6`() {
        assertEquals(50.4, SettingsViewModel.convertMsToDisplay(14.0, SpeedUnit.KMH), 0.01)
    }

    @Test
    fun `convertMsToDisplay mph multiplies by 2_237`() {
        assertEquals(31.318, SettingsViewModel.convertMsToDisplay(14.0, SpeedUnit.MPH), 0.01)
    }
}
