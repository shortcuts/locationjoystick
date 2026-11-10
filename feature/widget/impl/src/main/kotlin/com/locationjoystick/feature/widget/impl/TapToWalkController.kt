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
import com.locationjoystick.core.location.walkTarget
import com.locationjoystick.core.model.GroundMap
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.ScreenFeatureGates
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.core.model.TapToWalkMove
import com.locationjoystick.core.model.effectiveTapToWalkMoves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val state = MutableStateFlow<TapToWalkState>(TapToWalkState.Hidden)
    val isActive: StateFlow<Boolean> =
        state.map { it.isActive }.stateIn(scope, SharingStarted.Eagerly, false)

    val visible: StateFlow<Boolean> =
        combine(settingsRepository.getTapToWalkOverlayEnabled(), settingsRepository.getGuidedPath()) { enabled, path ->
            ScreenFeatureGates.tapToWalkShown(enabled, path)
        }.stateIn(scope, SharingStarted.Eagerly, false)
    private val effectiveMoves: StateFlow<List<TapToWalkMove>> =
        combine(settingsRepository.getTapToWalkMoves(), settingsRepository.getHideTeleportFeatures(), ::effectiveTapToWalkMoves)
            .stateIn(scope, SharingStarted.Eagerly, TapToWalkMove.DEFAULT.toList())
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

    init {
        scope.launch { groundCalibration.collect { reaimOnNewMap() } }
        scope.launch { visible.collect { if (!it) dismiss() } }
    }

    fun onClicked() {
        when (tapToWalkClick(state.value.overlay?.isShowing() == true, needsAccessibilityDisclosure())) {
            TapToWalkClick.DISMISS_OVERLAY -> dismiss()
            TapToWalkClick.SHOW_DISCLOSURE -> onShowDisclosure()
            TapToWalkClick.OPEN_OVERLAY -> openTapToWalkOverlay()
        }
    }

    fun dismiss() {
        state.value.overlay?.dismiss()
        transition(TapToWalkEvent.OverlayClosed)
    }

    private fun transition(event: TapToWalkEvent) {
        state.update { current -> current.next(event) ?: current.also { Log.w(TAG, "ignored $event from $it") } }
    }

    private fun openTapToWalkOverlay() {
        val view =
            TapToWalkOverlay(
                context = context,
                windowManager = windowManager,
                lifecycleOwner = lifecycleOwner,
                savedStateRegistryOwner = savedStateRegistryOwner,
                onGroundTap = ::onGroundTap,
                onMove = ::moveTo,
                cooldownForPosition = mapController::cooldownForPosition,
                getPosition = { mapController.sharedState.value.currentPosition },
                onDismissed = { transition(TapToWalkEvent.OverlayClosed) },
            )
        transition(TapToWalkEvent.OverlayOpened(view))
        view.show()
    }

    /** One effective move runs at once; two or more return [TapDecision.Choose] and start nothing. */
    internal fun onGroundTap(tap: GroundTap): TapDecision {
        val target = resolveOrWarn(tap) ?: return TapDecision.Done
        val moves = effectiveMoves.value
        if (moves.size > 1) return TapDecision.Choose(target, moves)
        moveTo(tap, moves.single())
        return TapDecision.Done
    }

    /** Resolves the tap again so a map stored while the chooser was open applies. */
    internal fun moveTo(
        tap: GroundTap,
        move: TapToWalkMove,
    ) {
        val target = resolveOrWarn(tap) ?: return
        when (move) {
            TapToWalkMove.WALK -> mapController.walkTo(target)
            TapToWalkMove.VIA_ROADS -> mapController.walkViaRoads(target)
            TapToWalkMove.TELEPORT -> mapController.teleportTo(target)
        }
        if (move != TapToWalkMove.TELEPORT) transition(TapToWalkEvent.Aimed(tap, target, move))
    }

    private fun resolveOrWarn(tap: GroundTap): LatLng? =
        resolveGroundTap(tap).also {
            if (it == null) Toast.makeText(context, R.string.tap_to_walk_past_horizon, Toast.LENGTH_LONG).show()
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
        val walking = state.value as? TapToWalkState.Walking ?: return
        val (tap, target) = walking.tap to walking.target
        // A road walk's route end can be snapped off the tap by the router, so compare the tap target the walk mode keeps.
        val walkTarget =
            if (walking.move == TapToWalkMove.VIA_ROADS) mapController.sharedState.value.walkTarget else locationRepository.walkTarget.value
        if (walkTarget != target) {
            transition(TapToWalkEvent.WalkEnded)
            return
        }
        val next = TapToWalkOverlay.reaimedTarget(tap, target, walkTarget, groundMapNow()) ?: return
        Log.d(TAG, "tap re-aimed under the new map")
        transition(TapToWalkEvent.Aimed(tap, next, walking.move))
        if (walking.move == TapToWalkMove.VIA_ROADS) mapController.walkViaRoads(next) else mapController.walkTo(next)
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

/** What a ground tap does: it ran at once, or the user picks one of [moves] first. */
internal sealed interface TapDecision {
    data object Done : TapDecision

    data class Choose(
        val target: LatLng,
        val moves: List<TapToWalkMove>,
    ) : TapDecision
}
