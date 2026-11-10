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
import com.locationjoystick.core.location.MapSharedState
import com.locationjoystick.core.location.WalkMode
import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.core.model.TapToWalkMove
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
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
    private val movesFlow = MutableStateFlow(TapToWalkMove.DEFAULT)
    private val hideTeleportFlow = MutableStateFlow(false)
    private val sharedStateFlow = MutableStateFlow(MapSharedState())
    private val mapController = mockk<MapController>(relaxed = true) { every { sharedState } returns sharedStateFlow }
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
                every { getTapToWalkMoves() } returns movesFlow
                every { getHideTeleportFeatures() } returns hideTeleportFlow
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

        controller.moveTo(tap, TapToWalkMove.WALK)

        verify(exactly = 1) { mapController.walkTo(expected) }
    }

    @Test
    fun newCalibration_whileTapIsWalked_reaimsTapFromTapTimePosition() {
        val tap = tapAbove(300.0)
        val first = TapToWalkOverlay.computeGroundWalkTarget(feet, provisional, tap.x, tap.y)!!
        controller.moveTo(tap, TapToWalkMove.WALK)
        walkTargetFlow.value = first

        groundCalibrationFlow.value = storedScreen

        val reaimed = TapToWalkOverlay.computeGroundWalkTarget(feet, GroundMap(storedCalibration, 1080.0, 2400.0), tap.x, tap.y)!!
        verify(exactly = 1) { mapController.walkTo(reaimed) }
    }

    @Test
    fun newCalibration_afterWalkTargetChanged_leavesWalkAlone() {
        val tap = tapAbove(300.0)
        controller.moveTo(tap, TapToWalkMove.WALK)
        walkTargetFlow.value = LatLng(41.0, -74.0)

        groundCalibrationFlow.value = storedScreen

        verify(exactly = 1) { mapController.walkTo(any()) }
    }

    @Test
    fun onGroundTap_oneMove_runsOnlyThatMove() {
        val tap = tapAbove(300.0)
        val target = TapToWalkOverlay.computeGroundWalkTarget(feet, provisional, tap.x, tap.y)!!
        movesFlow.value = setOf(TapToWalkMove.VIA_ROADS)

        assertEquals(TapDecision.Done, controller.onGroundTap(tap))

        verify(exactly = 1) { mapController.walkViaRoads(target) }
        verify(exactly = 0) { mapController.walkTo(any()) }
        verify(exactly = 0) { mapController.teleportTo(any()) }
    }

    @Test
    fun onGroundTap_teleportOnly_teleportsAndNeverWalks() {
        val tap = tapAbove(300.0)
        val target = TapToWalkOverlay.computeGroundWalkTarget(feet, provisional, tap.x, tap.y)!!
        movesFlow.value = setOf(TapToWalkMove.TELEPORT)

        assertEquals(TapDecision.Done, controller.onGroundTap(tap))

        verify(exactly = 1) { mapController.teleportTo(target) }
        verify(exactly = 0) { mapController.walkTo(any()) }
    }

    @Test
    fun onGroundTap_twoMoves_choosesWithoutMoving() {
        val tap = tapAbove(300.0)
        val target = TapToWalkOverlay.computeGroundWalkTarget(feet, provisional, tap.x, tap.y)!!
        movesFlow.value = setOf(TapToWalkMove.WALK, TapToWalkMove.TELEPORT)

        val decision = controller.onGroundTap(tap)

        assertEquals(TapDecision.Choose(target, listOf(TapToWalkMove.WALK, TapToWalkMove.TELEPORT)), decision)
        verify(exactly = 0) { mapController.walkTo(any()) }
        verify(exactly = 0) { mapController.teleportTo(any()) }
    }

    @Test
    fun onGroundTap_hideTeleport_dropsTeleportFromTheChoice() {
        movesFlow.value = setOf(TapToWalkMove.WALK, TapToWalkMove.TELEPORT)
        hideTeleportFlow.value = true

        assertEquals(TapDecision.Done, controller.onGroundTap(tapAbove(300.0)))

        verify(exactly = 1) { mapController.walkTo(any()) }
        verify(exactly = 0) { mapController.teleportTo(any()) }
    }

    @Test
    fun onGroundTap_pastHorizon_doesNothing() {
        movesFlow.value = setOf(TapToWalkMove.WALK, TapToWalkMove.VIA_ROADS)

        assertEquals(TapDecision.Done, controller.onGroundTap(GroundTap(feet, 540.0, -100000.0)))

        verify(exactly = 0) { mapController.walkTo(any()) }
        verify(exactly = 0) { mapController.walkViaRoads(any()) }
    }

    @Test
    fun moveTo_usesTheMapStoredSinceTheTap() {
        val tap = tapAbove(300.0)
        groundCalibrationFlow.value = storedScreen

        controller.moveTo(tap, TapToWalkMove.WALK)

        val expected = TapToWalkOverlay.computeGroundWalkTarget(feet, GroundMap(storedCalibration, 1080.0, 2400.0), tap.x, tap.y)!!
        verify(exactly = 1) { mapController.walkTo(expected) }
    }

    @Test
    fun newCalibration_whileRoadWalk_reroutesToTheCorrectedTarget() {
        val tap = tapAbove(300.0)
        val first = TapToWalkOverlay.computeGroundWalkTarget(feet, provisional, tap.x, tap.y)!!
        controller.moveTo(tap, TapToWalkMove.VIA_ROADS)
        sharedStateFlow.value = MapSharedState(walkMode = WalkMode.Walking(target = first, start = feet, isViaRoads = true))

        groundCalibrationFlow.value = storedScreen

        val reaimed = TapToWalkOverlay.computeGroundWalkTarget(feet, GroundMap(storedCalibration, 1080.0, 2400.0), tap.x, tap.y)!!
        verify(exactly = 1) { mapController.walkViaRoads(reaimed) }
        verify(exactly = 0) { mapController.walkTo(any()) }
    }

    @Test
    fun newCalibration_afterRoadWalkReplaced_leavesItAlone() {
        controller.moveTo(tapAbove(300.0), TapToWalkMove.VIA_ROADS)
        sharedStateFlow.value = MapSharedState(walkMode = WalkMode.Walking(target = LatLng(41.0, -74.0), start = feet, isViaRoads = true))

        groundCalibrationFlow.value = storedScreen

        verify(exactly = 1) { mapController.walkViaRoads(any()) }
    }

    @Test
    fun newCalibration_afterTeleport_neverReaims() {
        controller.moveTo(tapAbove(300.0), TapToWalkMove.TELEPORT)

        groundCalibrationFlow.value = storedScreen

        verify(exactly = 1) { mapController.teleportTo(any()) }
        verify(exactly = 0) { mapController.walkTo(any()) }
        verify(exactly = 0) { mapController.walkViaRoads(any()) }
    }
}
