package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.GroundFit
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.LegMove
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.core.model.distanceTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the tap-to-walk ground map measured with no user step. While Tap to Walk is on and the game is in front,
 * every walk the app already makes (tap to walk, quick walk, joystick) takes one screenshot per tick. Only a few
 * small crops of it are kept, in memory, until the next tick; their flow and the known mock move give the heading and
 * the scale. The moves of the current walk are fitted together, so a camera twist between walks is picked up by the
 * next walk. Behaviour and invariants: docs/features/tap-to-walk.md, "Ground map: flow-measured".
 */
@Singleton
class AutoFitCoordinator
    @Inject
    constructor(
        private val locationRepository: LocationRepository,
        private val screenCaptureSource: ScreenCaptureSource,
        private val settingsRepository: SettingsRepository,
    ) {
        // Seams for tests: wall clock and where the crop and fit maths run.
        internal var nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
        internal var analysisDispatcher: CoroutineDispatcher = Dispatchers.Default

        private var active: StateFlow<Boolean> = MutableStateFlow(false) // every switch that lets a frame exist
        private var lastTick: LatLng? = null
        private var last: Sample? = null
        private val moves = ArrayDeque<LegMove>()
        private var runningFit: GroundFit? = null

        private class Sample(
            val crops: GroundFlowFit.Crops,
            val at: LatLng,
            val atMs: Long,
        )

        /** Starts watching walks on [scope] (the service scope: the loop and its crops end with the service). */
        fun observe(scope: CoroutineScope) {
            active = screenCaptureSource.captureAllowed.stateIn(scope, SharingStarted.Eagerly, false)
            scope.launch { loop() }
        }

        private suspend fun loop() {
            try {
                while (true) {
                    val started = nowMs()
                    runCatching { tick() }.onFailure {
                        if (it is CancellationException) throw it
                        Log.e(TAG, "tick failed", it)
                        endWalk()
                    }
                    delay(maxOf(0L, AppConstants.TapToWalkConstants.FLOW_FRAME_INTERVAL_MS - (nowMs() - started)))
                }
            } finally {
                endWalk()
            }
        }

        /** One tick: a screenshot only while the gate is open and the player moved since the last tick. */
        internal suspend fun tick() {
            val pos = locationRepository.currentPosition.value
            val from = shouldSample(pos)
            lastTick = pos
            if (from == null) return endWalk()
            val crops = withContext(analysisDispatcher) { screenCaptureSource.captureCrops() } ?: return endWalk()
            val after = locationRepository.currentPosition.value ?: return endWalk()
            val cur =
                Sample(
                    crops,
                    LatLng((from.latitude + after.latitude) / 2, (from.longitude + after.longitude) / 2),
                    nowMs(),
                )
            val prev = last
            last = cur
            if (prev == null) return
            if (isTeleport(prev, cur)) {
                moves.clear() // a teleport or a vehicle: the camera may have changed, and the flow is too large to measure
                return
            }
            val move = nextMove(prev, cur) ?: return
            if (runningFit?.let { GroundFlowFit.changesCamera(it, move, crops.width, crops.height) } == true) {
                moves.clear() // a pinch or twist: the moves before it belong to another camera
                runningFit = null
            }
            moves.addLast(move)
            while (moves.size > AppConstants.TapToWalkConstants.FLOW_WINDOW_MOVES) moves.removeFirst()
            if (moves.size < MIN_MOVES) return
            val fit = withContext(analysisDispatcher) { GroundFlowFit.fit(moves.toList(), crops.width, crops.height) } ?: return
            runningFit = fit
            maybePersist(fit, crops)
        }

        private fun shouldSample(pos: LatLng?): LatLng? {
            val gate =
                active.value &&
                    locationRepository.mockLocationState.value == MockLocationState.RUNNING &&
                    locationRepository.currentMode.value in CAPTURE_MODES
            val moved = gate && pos != null && lastTick?.let { it.distanceTo(pos) >= MIN_STEP_M } == true
            return pos.takeIf { moved }
        }

        private fun isTeleport(
            prev: Sample,
            cur: Sample,
        ): Boolean =
            prev.at.distanceTo(cur.at) / maxOf((cur.atMs - prev.atMs) / 1000.0, 1e-3) >
                AppConstants.TapToWalkConstants.FLOW_MAX_SPEED_MPS

        private fun nextMove(
            prev: Sample,
            cur: Sample,
        ): LegMove? = GroundFlowFit.move(prev.crops, cur.crops, CalibrationWalkMath.offset(prev.at, cur.at, prev.at.latitude))

        private suspend fun maybePersist(
            fit: GroundFit,
            crops: GroundFlowFit.Crops,
        ) {
            val keep = settingsRepository.getGroundCalibration().first()?.takeIf { it.matches(crops.width, crops.height) }
            if (!GroundFlowFit.replaces(keep?.calibration, fit, moves.size)) return
            Log.d(TAG, "flow fit ${fit.calibration.headingDeg} deg, ${fit.calibration.lateralPxPerM} px/m from ${moves.size} moves")
            settingsRepository.setGroundCalibration(
                StoredGroundCalibration(
                    fit.calibration,
                    crops.width,
                    crops.height,
                    System.currentTimeMillis(),
                    fit.flowCount,
                    fit.medianAbsPx,
                ),
            )
        }

        // Crops must not outlive the walk or the switch that allowed them (storage and consent).
        private fun endWalk() {
            last = null
            moves.clear()
            runningFit = null
        }

        private companion object {
            const val TAG = "AutoFit"
            const val MIN_STEP_M = 0.5 // moved less than this since the last tick: standing still
            const val MIN_MOVES = 2 // one move's crops can fit, but one bad pair would then replace a good stored map
            val CAPTURE_MODES = setOf(MockMode.JOYSTICK, MockMode.TELEPORT, MockMode.WALK_TO) // replay and roaming are not measured
        }
    }
