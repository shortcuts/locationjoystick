package com.locationjoystick.feature.widget.impl

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.ScreenFeatureGates
import com.locationjoystick.core.model.StoredGroundCalibration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the Tap to Walk overlay for [FloatingWidgetService]: the overlay window, the tap being
 * walked and the re-aim when a newer ground map arrives.
 */
internal class TapToWalkController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val mapController: MapController,
    private val onShowDisclosure: () -> Unit,
) {
    companion object {
        private const val TAG = "TapToWalkController"
    }

    private val isActiveFlow = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = isActiveFlow

    val visible: StateFlow<Boolean> =
        combine(settingsRepository.getTapToWalkOverlayEnabled(), settingsRepository.getGuidedPath()) { enabled, path ->
            ScreenFeatureGates.tapToWalkShown(enabled, path)
        }.stateIn(scope, SharingStarted.Eagerly, false)
    private val groundCalibration: StateFlow<StoredGroundCalibration?> =
        settingsRepository
            .getGroundCalibration()
            .stateIn(scope, SharingStarted.Eagerly, null)
    private val accessibilityDisclosureChoice: StateFlow<String> =
        settingsRepository
            .getAccessibilityDisclosureChoice()
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                AppConstants.AccessibilityConstants.DISCLOSURE_UNANSWERED,
            )

    private var overlay: TapToWalkOverlay? = null

    /** The tap being walked and its target, so a newer map can re-aim it from the position it was made at. */
    private var aimedTap: Pair<GroundTap, LatLng>? = null

    init {
        scope.launch { groundCalibration.collect { reaimOnNewMap() } }
        scope.launch { visible.collect { if (!it) dismiss() } }
    }

    fun onClicked() {
        when (tapToWalkClick(overlay?.isShowing() == true, needsAccessibilityDisclosure())) {
            TapToWalkClick.DISMISS_OVERLAY -> dismiss()
            TapToWalkClick.SHOW_DISCLOSURE -> onShowDisclosure()
            TapToWalkClick.OPEN_OVERLAY -> openTapToWalkOverlay()
        }
    }

    fun dismiss() {
        overlay?.dismiss()
        overlay = null
    }

    private fun openTapToWalkOverlay() {
        val view =
            TapToWalkOverlay(
                context = context,
                windowManager = windowManager,
                lifecycleOwner = lifecycleOwner,
                savedStateRegistryOwner = savedStateRegistryOwner,
                onGroundTap = ::walkGroundTap,
                getPosition = { mapController.sharedState.value.currentPosition },
                onDismissed = { isActiveFlow.value = false },
            )
        overlay = view
        isActiveFlow.value = true
        view.show()
    }

    internal fun walkGroundTap(tap: GroundTap) {
        val target = resolveGroundTap(tap)
        if (target == null) {
            Toast.makeText(context, R.string.tap_to_walk_past_horizon, Toast.LENGTH_LONG).show()
            return
        }
        aimedTap = tap to target
        mapController.walkTo(target)
    }

    private fun groundMapNow(): GroundMap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val bounds = windowManager.maximumWindowMetrics.bounds
        return TapToWalkOverlay.groundMapFor(groundCalibration.value, bounds.width(), bounds.height())
    }

    private fun resolveGroundTap(tap: GroundTap): LatLng? =
        groundMapNow()?.let { TapToWalkOverlay.computeGroundWalkTarget(tap.from, it, tap.x, tap.y) }

    /**
     * A fresh map measured while a tap is walked (the first flow of a fresh install, or a corrected heading) re-aims that
     * tap from its tap-time position. A walk that already ended or was replaced is left alone.
     */
    private fun reaimOnNewMap() {
        val (tap, target) = aimedTap ?: return
        val walkTarget = locationRepository.walkTarget.value
        if (walkTarget != target) {
            aimedTap = null
            return
        }
        val next = TapToWalkOverlay.reaimedTarget(tap, target, walkTarget, groundMapNow()) ?: return
        Log.d(TAG, "tap re-aimed under the new map")
        aimedTap = tap to next
        mapController.walkTo(next)
    }

    /**
     * The first screenshot is what the overlay's tap would trigger, so Play's Accessibility
     * API policy puts the disclosure here — in the feature's own usage flow — not only in Settings.
     * Re-shown until accepted: decline and skip are persisted but the next tap asks again.
     */
    private fun needsAccessibilityDisclosure(): Boolean =
        ScreenAccessibilityService.isSupported() &&
            accessibilityDisclosureChoice.value != AppConstants.AccessibilityConstants.DISCLOSURE_ACCEPTED
}
