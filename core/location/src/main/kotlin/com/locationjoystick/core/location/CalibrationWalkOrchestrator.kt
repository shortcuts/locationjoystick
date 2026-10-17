package com.locationjoystick.core.location

import android.content.Context
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.di.ApplicationScope
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.CapturedLeg
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.GroundCalibrationSolver
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SolveResult
import com.locationjoystick.core.model.StoredGroundCalibration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class CalibrationFailure {
    NOT_READY,
    SERVICE_UNAVAILABLE,
    STORAGE,
    TIMEOUT,
    SPEED_ABORT,
    INTERRUPTED,
    FRAME_SIZE_CHANGED,
    FEET_NOT_FOUND,
    TOO_FEW_FLOWS,
    IMPLAUSIBLE_FIT,
}

sealed interface CalibrationState {
    data object Idle : CalibrationState

    data class Walking(
        val leg: Int,
        val legCount: Int,
        val frames: Int,
    ) : CalibrationState

    data class Analysing(
        val step: Int,
        val stepCount: Int,
    ) : CalibrationState

    data class Done(
        val result: StoredGroundCalibration,
    ) : CalibrationState

    data class Failed(
        val reason: CalibrationFailure,
    ) : CalibrationState
}

/**
 * Walks 8 legs of 40 m at walking speed (never a teleport), screenshots every 3 s through the
 * accessibility service, solves the ground map from the frames and stores it per device.
 * Behaviour and invariants: docs/features/tap-to-walk.md, "Calibration walk". The UX that starts it
 * (game in front, countdown, copy) is not part of this class.
 */
@Singleton
class CalibrationWalkOrchestrator
    @Inject
    constructor(
        private val mapController: MapController,
        private val locationRepository: LocationRepository,
        private val screenCaptureSource: ScreenCaptureSource,
        private val settingsRepository: SettingsRepository,
        @param:ApplicationContext private val context: Context,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) {
        // Seams for tests: wall clock and the (slow, pure) solver.
        internal var nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
        internal var solve: (List<CapturedLeg>, Int, Int, (Int) -> Unit) -> SolveResult =
            { legs, w, h, onStep -> GroundCalibrationSolver.solve(legs, w, h, onStep) }

        @Suppress("ktlint:standard:property-naming")
        private val _state = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
        val state: StateFlow<CalibrationState> = _state.asStateFlow()

        private var job: Job? = null

        private class Abort(
            val reason: CalibrationFailure,
        ) : Exception(reason.name)

        /** True when spoofing runs idle in a joystick or teleport mode with a position and no walk target. */
        val isReady: Boolean
            get() =
                locationRepository.mockLocationState.value == MockLocationState.RUNNING &&
                    locationRepository.currentMode.value in READY_MODES &&
                    locationRepository.walkTarget.value == null &&
                    locationRepository.currentPosition.value != null

        /**
         * Starts a calibration walk. False when one is already running, or when the app is not ready
         * (state becomes [CalibrationState.Failed] with [CalibrationFailure.NOT_READY]).
         */
        fun start(): Boolean =
            synchronized(this) {
                if (_state.value is CalibrationState.Walking || _state.value is CalibrationState.Analysing) return false
                if (!isReady) {
                    _state.value = CalibrationState.Failed(CalibrationFailure.NOT_READY)
                    return false
                }
                _state.value = CalibrationState.Walking(1, LEG_COUNT, 0)
                job = appScope.launch { run() }
                true
            }

        /** Stops the walk, drops the frames and returns to [CalibrationState.Idle]; nothing is stored. */
        fun cancel() {
            synchronized(this) {
                job?.cancel()
                job = null
                if (_state.value is CalibrationState.Walking || _state.value is CalibrationState.Analysing) {
                    mapController.stopWalk()
                    _state.value = CalibrationState.Idle
                }
            }
        }

        private suspend fun run() {
            val spool = File(context.cacheDir, SPOOL_DIR)
            try {
                spool.deleteRecursively()
                spool.mkdirs()
                val probe = probeFrame(spool)
                val legs = walkLegs(spool, probe.width, probe.height)
                _state.value = CalibrationState.Analysing(0, GroundCalibrationSolver.STEP_COUNT)
                val result =
                    withContext(Dispatchers.Default) {
                        solve(legs, probe.width, probe.height) {
                            _state.value = CalibrationState.Analysing(it, GroundCalibrationSolver.STEP_COUNT)
                        }
                    }
                when (result) {
                    is SolveResult.Ok -> {
                        val stored =
                            StoredGroundCalibration(
                                result.fit.calibration,
                                probe.width,
                                probe.height,
                                System.currentTimeMillis(),
                                result.fit.flowCount,
                                result.fit.medianAbsPx,
                            )
                        settingsRepository.setGroundCalibration(stored)
                        _state.value = CalibrationState.Done(stored)
                    }
                    SolveResult.FeetNotFound -> throw Abort(CalibrationFailure.FEET_NOT_FOUND)
                    SolveResult.TooFewFlows -> throw Abort(CalibrationFailure.TOO_FEW_FLOWS)
                    SolveResult.ImplausibleFit -> throw Abort(CalibrationFailure.IMPLAUSIBLE_FIT)
                }
            } catch (a: Abort) {
                Log.w(TAG, "Calibration aborted: ${a.reason}")
                mapController.stopWalk()
                _state.value = CalibrationState.Failed(a.reason)
            } finally {
                withContext(NonCancellable) { spool.deleteRecursively() }
            }
        }

        /** One frame before moving: proves the service is bound and consented, and sizes the spool check. */
        private suspend fun probeFrame(spool: File): GrayImage {
            if (!screenCaptureSource.isAvailable) throw Abort(CalibrationFailure.SERVICE_UNAVAILABLE)
            val probe = screenCaptureSource.captureGrayFrame() ?: throw Abort(CalibrationFailure.SERVICE_UNAVAILABLE)
            val needed =
                LEG_COUNT.toLong() * AppConstants.TapToWalkConstants.CALIBRATION_SPOOL_FRAMES_PER_LEG * probe.width * probe.height
            if (spool.usableSpace < needed) throw Abort(CalibrationFailure.STORAGE)
            return probe
        }

        private suspend fun walkLegs(
            spool: File,
            width: Int,
            height: Int,
        ): List<CapturedLeg> {
            val legs = mutableListOf<CapturedLeg>()
            for ((index, bearing) in CalibrationWalkMath.BEARINGS_DEG.withIndex()) {
                val from = locationRepository.currentPosition.value ?: throw Abort(CalibrationFailure.INTERRUPTED)
                val target = CalibrationWalkMath.commandedTarget(from, bearing, CalibrationWalkMath.LEG_METERS)
                _state.value = CalibrationState.Walking(index + 1, LEG_COUNT, 0)
                mapController.walkTo(target, AppConstants.ProfileConstants.WALK_SPEED_MPS)
                legs += captureLeg(File(spool, index.toString()), index, bearing, target, width, height)
            }
            return legs
        }

        /** Screenshots every tick until the walk target clears; frames go to disk, metadata stays in memory. */
        private suspend fun captureLeg(
            dir: File,
            index: Int,
            bearing: Double,
            target: LatLng,
            width: Int,
            height: Int,
        ): CapturedLeg {
            dir.mkdirs()
            val times = ArrayList<Double>()
            val positions = ArrayList<LatLng>()
            val legStart = nowMs()
            val timeoutMs =
                (
                    AppConstants.TapToWalkConstants.CALIBRATION_LEG_TIMEOUT_FACTOR *
                        CalibrationWalkMath.LEG_METERS / AppConstants.ProfileConstants.WALK_SPEED_MPS * 1000
                ).toLong()
            var seenTarget = false
            while (true) {
                val tick = nowMs()
                if (tick - legStart > timeoutMs) throw Abort(CalibrationFailure.TIMEOUT)
                val before = locationRepository.currentPosition.value
                val frame = screenCaptureSource.captureGrayFrame()
                val after = locationRepository.currentPosition.value
                if (frame != null && before != null && after != null) {
                    if (frame.width != width || frame.height != height) throw Abort(CalibrationFailure.FRAME_SIZE_CHANGED)
                    val position = LatLng((before.latitude + after.latitude) / 2, (before.longitude + after.longitude) / 2)
                    val t = (tick - legStart) / 1000.0
                    if (positions.isNotEmpty()) {
                        val dt = maxOf(t - times.last(), 1e-6)
                        if (haversineDistance(positions.last(), position) / dt >
                            AppConstants.TapToWalkConstants.CALIBRATION_SPEED_ABORT_MPS
                        ) {
                            throw Abort(CalibrationFailure.SPEED_ABORT)
                        }
                    }
                    val file = File(dir, "${positions.size}.gray")
                    withContext(Dispatchers.IO) { file.writeBytes(frame.pixels) }
                    times += t
                    positions += position
                    _state.value = CalibrationState.Walking(index + 1, LEG_COUNT, positions.size)
                }
                val walking = locationRepository.walkTarget.value != null
                seenTarget = seenTarget || walking
                if (seenTarget && !walking) break
                delay(maxOf(0L, AppConstants.TapToWalkConstants.CALIBRATION_FRAME_INTERVAL_MS - (nowMs() - tick)))
            }
            // Arrival and cancellation both clear the target; only distance tells them apart.
            val end = locationRepository.currentPosition.value ?: throw Abort(CalibrationFailure.INTERRUPTED)
            if (haversineDistance(end, target) > AppConstants.TapToWalkConstants.CALIBRATION_ARRIVAL_SLACK_M) {
                throw Abort(CalibrationFailure.INTERRUPTED)
            }
            val count = positions.size
            // ponytail: raw frames on disk, about 0.7 GB at 1080x2340 during a run; crop or compress only if STORAGE failures show up
            return CapturedLeg(bearing, times.toDoubleArray(), positions) {
                (0 until count).map { GrayImage(width, height, File(dir, "$it.gray").readBytes()) }
            }
        }

        private companion object {
            const val TAG = "CalibrationWalk"
            const val SPOOL_DIR = "tap_calibration"
            val LEG_COUNT = CalibrationWalkMath.BEARINGS_DEG.size
            val READY_MODES = setOf(MockMode.JOYSTICK, MockMode.TELEPORT)
        }
    }
