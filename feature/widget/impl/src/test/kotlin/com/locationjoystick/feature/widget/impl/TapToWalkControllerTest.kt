package com.locationjoystick.feature.widget.impl

import android.content.Context
import android.graphics.Rect
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.StoredGroundCalibration
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TapToWalkControllerTest {
    private val feet = LatLng(40.0, -74.0)
    private val provisional = GroundMap(GroundFlowFit.provisional(), 1080.0, 2400.0)
    private val storedCalibration = GroundCalibration(90.0, 7.5, 3.1, -0.0022, 540.0, 1510.0)
    private val storedScreen = StoredGroundCalibration(storedCalibration, 1080, 2400, 0L, 0, 0.0)

    private val walkTargetFlow = MutableStateFlow<LatLng?>(null)
    private val groundCalibrationFlow = MutableStateFlow<StoredGroundCalibration?>(null)
    private val mapController = mockk<MapController>(relaxed = true)
    private lateinit var controller: TapToWalkController

    @Before
    fun setUp() {
        val windowManager =
            mockk<WindowManager> {
                every { maximumWindowMetrics } returns
                    mockk<WindowMetrics> { every { bounds } returns Rect(0, 0, 1080, 2400) }
            }
        val settingsRepository =
            mockk<SettingsRepository> {
                every { getTapToWalkOverlayEnabled() } returns flowOf(false)
                every { getGuidedPath() } returns flowOf(GuidedPath.DEFAULT)
                every { getGroundCalibration() } returns groundCalibrationFlow
                every { getAccessibilityDisclosureChoice() } returns
                    flowOf(AppConstants.AccessibilityConstants.DISCLOSURE_UNANSWERED)
            }
        val locationRepository = mockk<LocationRepository> { every { walkTarget } returns walkTargetFlow }
        controller =
            TapToWalkController(
                context = ApplicationProvider.getApplicationContext<Context>(),
                windowManager = windowManager,
                lifecycleOwner = mockk<LifecycleOwner>(relaxed = true),
                savedStateRegistryOwner = mockk<SavedStateRegistryOwner>(relaxed = true),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
                settingsRepository = settingsRepository,
                locationRepository = locationRepository,
                mapController = mapController,
                onShowDisclosure = {},
            )
    }

    private fun tapAbove(distancePx: Double) = GroundTap(feet, 540.0, 1510.0 - distancePx)

    @Test
    fun walkGroundTap_resolvedTap_callsWalkToOnce() {
        val tap = tapAbove(300.0)
        val expected = TapToWalkOverlay.computeGroundWalkTarget(feet, provisional, tap.x, tap.y)!!

        controller.walkGroundTap(tap)

        verify(exactly = 1) { mapController.walkTo(expected) }
    }

    @Test
    fun newCalibration_whileTapIsWalked_reaimsTapFromTapTimePosition() {
        val tap = tapAbove(300.0)
        val first = TapToWalkOverlay.computeGroundWalkTarget(feet, provisional, tap.x, tap.y)!!
        controller.walkGroundTap(tap)
        walkTargetFlow.value = first

        groundCalibrationFlow.value = storedScreen

        val reaimed = TapToWalkOverlay.computeGroundWalkTarget(feet, GroundMap(storedCalibration, 1080.0, 2400.0), tap.x, tap.y)!!
        verify(exactly = 1) { mapController.walkTo(reaimed) }
    }

    @Test
    fun newCalibration_afterWalkTargetChanged_leavesWalkAlone() {
        val tap = tapAbove(300.0)
        controller.walkGroundTap(tap)
        walkTargetFlow.value = LatLng(41.0, -74.0)

        groundCalibrationFlow.value = storedScreen

        verify(exactly = 1) { mapController.walkTo(any()) }
    }
}
