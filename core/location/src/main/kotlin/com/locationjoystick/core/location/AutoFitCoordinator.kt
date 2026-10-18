package com.locationjoystick.core.location

import android.content.Context
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.di.ApplicationScope
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.CapturedLeg
import com.locationjoystick.core.model.FitHealth
import com.locationjoystick.core.model.FitMonitor
import com.locationjoystick.core.model.FrameFlow
import com.locationjoystick.core.model.GROUND_MAP_REFERENCE_HEIGHT
import com.locationjoystick.core.model.GROUND_MAP_REFERENCE_WIDTH
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.GroundCalibrationSolver
import com.locationjoystick.core.model.GroundMapFit
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Leg
import com.locationjoystick.core.model.LegMove
import com.locationjoystick.core.model.LegSegmenter
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.SolveResult
import com.locationjoystick.core.model.StoredGroundCalibration
import com.locationjoystick.core.model.WalkLeg
import com.locationjoystick.core.model.distanceTo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LearningState {
    data object Idle : LearningState

    data class Learning(
        val leg: Int,
        val legCount: Int,
    ) : LearningState
}

/**
 * Keeps the tap-to-walk ground map fitted with no user step. While Tap to Walk is on and the game is in front,
 * every straight stretch of a walk the app already makes (tap to walk, quick walk, joystick) becomes a leg of
 * screenshots with exact bearing and length. A closed leg is checked against the stored fit; a mismatch (camera
 * twist or zoom) makes the fit suspect and all four terms are re-fitted from the legs that followed. With no usable fit,
 * [learnMap] walks a short learning route. Behaviour and invariants: docs/features/tap-to-walk.md, "Automatic fit".
 */
@Singleton
class AutoFitCoordinator
    @Inject
    constructor(
        private val mapController: MapController,
        private val locationRepository: LocationRepository,
        private val screenCaptureSource: ScreenCaptureSource,
        private val settingsRepository: SettingsRepository,
        @param:ApplicationContext private val context: Context,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) {
        // Seams for tests: wall clock, the (slow, pure) analysis steps and where they run.
        internal var nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
        internal var analysisDispatcher: CoroutineDispatcher = Dispatchers.Default
        internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        internal var spoolCapBytes = AppConstants.TapToWalkConstants.AUTOFIT_SPOOL_CAP_BYTES
        internal var freeBytes: () -> Long = { context.cacheDir.usableSpace }
        internal var measure: (CapturedLeg, Double, Double, Int, Int) -> List<LegMove> = ::measureMoves
        internal var fullFit: (List<CapturedLeg>, Int, Int, () -> Unit) -> SolveResult =
            { legs, w, h, checkCancelled -> GroundCalibrationSolver.solve(legs, w, h, checkCancelled = checkCancelled) }

        @Suppress("ktlint:standard:property-naming")
        private val _health = MutableStateFlow<FitHealth>(FitHealth.None)
        val health: StateFlow<FitHealth> = _health.asStateFlow()

        @Suppress("ktlint:standard:property-naming")
        private val _learning = MutableStateFlow<LearningState>(LearningState.Idle)
        val learning: StateFlow<LearningState> = _learning.asStateFlow()

        /** Legs waiting for or in analysis: the learning walk waits for it to reach zero. */
        private val pending = MutableStateFlow(0)
        private val analysisLock = Mutex()
        private var scope: CoroutineScope = appScope
        private var tapToWalkOn: StateFlow<Boolean> = MutableStateFlow(false)
        private var active: StateFlow<Boolean> = MutableStateFlow(false) // every switch that lets a frame exist
        private var learningJob: Job? = null

        private val spool get() = File(context.cacheDir, SPOOL_DIR)
        private val segmenter = LegSegmenter()
        private var current = Spool(File(spool, "0"))
        private var legSeq = 1
        private var lastTick: LatLng? = null
        private var lastSampleAtMs = 0L
        private var lastSample: LatLng? = null

        /** Frames of the leg being captured (or buffered), on disk because a leg does not fit in the heap. */
        private class Spool(
            val dir: File,
            var count: Int = 0,
            var width: Int = 0,
            var height: Int = 0,
        )

        private class Buffered(
            val leg: Leg,
            val spool: Spool,
        ) {
            fun captured() =
                CapturedLeg(leg.bearingDeg, leg.timesS, leg.positions) {
                    (0 until spool.count).map { GrayImage(spool.width, spool.height, File(spool.dir, "$it.gray").readBytes()) }
                }
        }

        private val buffer = ArrayList<Buffered>()

        /**
         * Starts watching walks on [scope] (the service scope: the loop and its spool end with the service).
         * Health starts as Valid when a fit is stored, None otherwise.
         */
        fun observe(scope: CoroutineScope) {
            this.scope = scope
            tapToWalkOn =
                combine(settingsRepository.getTapToWalkOverlayEnabled(), settingsRepository.getGuidedPath()) { on, path ->
                    on && path.showsTapToWalk
                }.stateIn(scope, SharingStarted.Eagerly, false)
            active = screenCaptureSource.captureAllowed.stateIn(scope, SharingStarted.Eagerly, false)
            scope.launch { active.collect { if (!it) cancel() } } // frames must not outlive the switch that allowed them
            scope.launch {
                settingsRepository.getGroundCalibration().collect { stored ->
                    if (stored == null) {
                        _health.value = FitHealth.None
                    } else if (_health.value == FitHealth.None) {
                        _health.value = FitHealth.Valid
                    }
                }
            }
            // A kill or crash skips loop()'s finally, so frames of the last session may still be on disk.
            scope.launch {
                withContext(ioDispatcher) { spool.deleteRecursively() }
                loop()
            }
        }

        /**
         * Walks the learning route and waits for the fit. True when the fit is valid at the end; false when it
         * could not start (not ready, game not in front), was cancelled, or the legs gave no fit.
         */
        suspend fun learnMap(): Boolean {
            val started =
                synchronized(this) {
                    if (_learning.value != LearningState.Idle || !canLearn) return@synchronized false
                    _learning.value = LearningState.Learning(1, LEG_COUNT)
                    learningJob = appScope.launch { walkLearningRoute() }
                    true
                }
            if (!started) return false
            _learning.first { it == LearningState.Idle }
            return _health.value == FitHealth.Valid
        }

        /** Stops the learning walk and drops the buffered legs; the stored fit is untouched. */
        fun cancel() {
            synchronized(this) {
                learningJob?.cancel()
                learningJob = null
                if (_learning.value != LearningState.Idle) mapController.stopWalk()
            }
            appScope.launch { analysisLock.withLock { clearBuffer() } }
        }

        /** True when a learning walk could start and capture: spoofing runs idle in a joystick or teleport mode. */
        val canLearn: Boolean
            get() {
                val blockers =
                    listOfNotNull(
                        "tap to walk off".takeIf { !tapToWalkOn.value },
                        "game not in front".takeIf { !screenCaptureSource.gameInFront },
                        "spoofing not running".takeIf { locationRepository.mockLocationState.value != MockLocationState.RUNNING },
                        "mode ${locationRepository.currentMode.value}".takeIf { locationRepository.currentMode.value !in IDLE_MODES },
                        "walking".takeIf { locationRepository.walkTarget.value != null },
                        "no position".takeIf { locationRepository.currentPosition.value == null },
                    )
                if (blockers.isNotEmpty()) Log.d(TAG, "cannot learn: $blockers")
                return blockers.isEmpty()
            }

        private suspend fun walkLearningRoute() {
            try {
                for ((index, bearing) in CalibrationWalkMath.LEARNING_BEARINGS_DEG.withIndex()) {
                    _learning.value = LearningState.Learning(index + 1, LEG_COUNT)
                    val from = locationRepository.currentPosition.value ?: return
                    val target = CalibrationWalkMath.commandedTarget(from, bearing, CalibrationWalkMath.LEARNING_LEG_METERS)
                    mapController.walkTo(target, AppConstants.ProfileConstants.WALK_SPEED_MPS)
                    if (!awaitArrival(target)) return
                }
                // The last leg closes on the first idle tick and is analysed after that: wait it out, then the fit is valid or not.
                delay(2 * AppConstants.TapToWalkConstants.AUTOFIT_FRAME_INTERVAL_MS)
                withTimeoutOrNull(ANALYSIS_WAIT_MS) { pending.first { it == 0 } }
            } finally {
                _learning.value = LearningState.Idle
            }
        }

        /** Waits for the walk target to clear; false when the walk was cancelled short of the target or timed out. */
        private suspend fun awaitArrival(target: LatLng): Boolean {
            val timeoutMs =
                (
                    AppConstants.TapToWalkConstants.AUTOFIT_LEG_TIMEOUT_FACTOR *
                        CalibrationWalkMath.LEARNING_LEG_METERS / AppConstants.ProfileConstants.WALK_SPEED_MPS * 1000
                ).toLong()
            val start = nowMs()
            var seen = false
            while (nowMs() - start < timeoutMs) {
                val walking = locationRepository.walkTarget.value != null
                seen = seen || walking
                if (seen && !walking) {
                    // Arrival and cancellation both clear the target; only distance tells them apart.
                    val end = locationRepository.currentPosition.value ?: return false
                    return end.distanceTo(target) <= AppConstants.TapToWalkConstants.AUTOFIT_ARRIVAL_SLACK_M
                }
                delay(POLL_MS)
            }
            mapController.stopWalk()
            return false
        }

        private suspend fun loop() {
            try {
                while (true) {
                    val tick = nowMs()
                    runCatching { tick() }.onFailure {
                        if (it is CancellationException) {
                            throw it
                        } else {
                            Log.e(
                                TAG,
                                "tick failed",
                                it,
                            )
                        }
                    }
                    delay(maxOf(0L, AppConstants.TapToWalkConstants.AUTOFIT_FRAME_INTERVAL_MS - (nowMs() - tick)))
                }
            } finally {
                withContext(NonCancellable) {
                    cancel() // learning ends with the service
                    discardSpool()
                }
            }
        }

        /** One 3 s tick: take a frame only while the gate is open and the player moved since the last tick. */
        private suspend fun tick() {
            if (!active.value) return discardOpenLeg()
            val pos = locationRepository.currentPosition.value
            val gate =
                tapToWalkOn.value &&
                    locationRepository.mockLocationState.value == MockLocationState.RUNNING &&
                    locationRepository.currentMode.value in CAPTURE_MODES
            val moved = gate && pos != null && lastTick?.let { it.distanceTo(pos) >= MIN_STEP_M } == true
            lastTick = pos
            // No capture while a leg is analysed: the screenshot and the flow code do not fit in the heap together (measured: OOM).
            if (!moved || pending.value > 0) return endLeg()
            val before = pos!!
            if (!makeRoom()) return endLeg()
            val frame = screenCaptureSource.captureGrayFrame() ?: return endLeg()
            val after = locationRepository.currentPosition.value ?: return endLeg()
            val at = LatLng((before.latitude + after.latitude) / 2, (before.longitude + after.longitude) / 2)
            val now = nowMs()
            if (current.count > 0 && (frame.width != current.width || frame.height != current.height)) endLeg()
            val last = lastSample
            if (last != null &&
                last.distanceTo(at) / maxOf((now - lastSampleAtMs) / 1000.0, 1e-6) > AppConstants.TapToWalkConstants.AUTOFIT_MAX_SPEED_MPS
            ) {
                return endLeg()
            }
            lastSample = at
            lastSampleAtMs = now
            val closed = segmenter.add(now / 1000.0, at)
            val belongsToClosed = closed != null && segmenter.size == 0
            if (closed == null || belongsToClosed) store(frame) // the sample is part of the open or just-closed leg
            if (closed != null) {
                val dir = current
                current = newSpool()
                onClosed(closed, dir)
                if (!belongsToClosed) store(frame)
            }
        }

        private fun endLeg() {
            lastSample = null
            val leg = segmenter.close()
            val dir = current
            current = newSpool()
            onClosed(leg, dir)
        }

        // endLeg() would queue the open leg for analysis and keep its frames on disk.
        private fun discardOpenLeg() {
            if (current.count == 0 && lastSample == null) return
            segmenter.close()
            current.dir.deleteRecursively()
            current = newSpool()
            lastSample = null
            lastTick = null
        }

        /**
         * Drops the oldest buffered legs until one more frame fits under the byte cap and the free-space floor.
         * Runs before the capture: a failed write after segmenter.add would desync frames from samples.
         * False when nothing is left to drop. No lock: no analysis runs while pending is zero.
         */
        private fun makeRoom(): Boolean {
            val next = current.width.toLong() * current.height
            while (true) {
                val used = spool.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                if (used + next <= spoolCapBytes &&
                    freeBytes() >= AppConstants.TapToWalkConstants.AUTOFIT_MIN_FREE_BYTES + next
                ) {
                    return true
                }
                if (buffer.isEmpty()) return false
                buffer
                    .removeAt(0)
                    .spool.dir
                    .deleteRecursively()
            }
        }

        private fun newSpool() = Spool(File(spool, (legSeq++).toString()))

        private suspend fun store(frame: GrayImage) {
            current.width = frame.width
            current.height = frame.height
            val file = File(current.dir, "${current.count}.gray")
            withContext(ioDispatcher) {
                current.dir.mkdirs()
                file.writeBytes(frame.pixels)
            }
            current.count++
        }

        private fun onClosed(
            leg: Leg?,
            sp: Spool,
        ) {
            if (leg == null || pending.value >= AppConstants.TapToWalkConstants.AUTOFIT_BUFFER_LEGS) {
                sp.dir.deleteRecursively()
                return
            }
            pending.value++
            // ATOMIC: a cancel before the first run must still reach the finally, or pending never returns to zero.
            scope.launch(analysisDispatcher, start = CoroutineStart.ATOMIC) {
                try {
                    analysisLock.withLock {
                        runCatching { analyse(Buffered(leg, sp)) }.onFailure {
                            if (it is CancellationException) throw it
                            Log.e(TAG, "analysis failed", it)
                        }
                        if (!active.value) clearBuffer() // the gate closed during the analysis
                    }
                } finally {
                    pending.value--
                }
            }
        }

        /**
         * Checks one closed leg against the stored fit. A valid fit that the leg contradicts becomes suspect; a
         * suspect or missing fit just collects the leg. The legs since the fit stopped matching are re-fitted together.
         */
        private suspend fun analyse(b: Buffered) {
            val w = b.spool.width
            val h = b.spool.height
            val stored = settingsRepository.getGroundCalibration().first()
            if (stored == null || !stored.matches(w, h)) {
                _health.value = FitHealth.None
            } else if (_health.value == FitHealth.Valid) {
                val cal = stored.calibration
                val moves =
                    measure(b.captured(), cal.feetX * w / GROUND_MAP_REFERENCE_WIDTH, cal.feetY * h / GROUND_MAP_REFERENCE_HEIGHT, w, h)
                val alarm = FitMonitor.alarmIndex(GroundMapFit.moveResidualsPx(cal, moves, w, h), stored.medianAbsPx)
                if (alarm == null) {
                    b.spool.dir.deleteRecursively()
                    return
                }
                Log.d(TAG, "leg ${b.leg.bearingDeg.toInt()} deg: alarm at move $alarm, fit is suspect")
                clearBuffer() // legs from before the change must not mix with the ones after it
                _health.value = FitHealth.Suspect
            }
            buffer += b
            while (buffer.size > AppConstants.TapToWalkConstants.AUTOFIT_BUFFER_LEGS) {
                buffer
                    .removeAt(0)
                    .spool.dir
                    .deleteRecursively()
            }
            fullRefit(w, h)
        }

        /** All four terms from the buffered legs (those since the fit stopped matching), when they span two bearings. */
        private suspend fun fullRefit(
            w: Int,
            h: Int,
        ) {
            val legs = buffer.filter { it.spool.width == w && it.spool.height == h }
            val spread = legs.maxOfOrNull { a -> legs.maxOf { bearingGap(a.leg.bearingDeg, it.leg.bearingDeg) } } ?: 0.0
            if (spread < AppConstants.TapToWalkConstants.AUTOFIT_MIN_BEARING_SPREAD_DEG) return
            val started = nowMs()
            val ctx = currentCoroutineContext()
            val result = fullFit(legs.map { it.captured() }, w, h) { ctx.ensureActive() }
            Log.d(TAG, "full fit took ${(nowMs() - started) / 1000} s")
            if (result is SolveResult.Ok) {
                val stored =
                    StoredGroundCalibration(
                        result.fit.calibration,
                        w,
                        h,
                        System.currentTimeMillis(),
                        result.fit.flowCount,
                        result.fit.medianAbsPx,
                    )
                Log.d(TAG, "full fit from ${legs.size} legs, median ${result.fit.medianAbsPx} px")
                settingsRepository.setGroundCalibration(stored)
                markValid()
            } else {
                Log.d(TAG, "full fit from ${legs.size} legs failed: $result")
            }
        }

        private fun markValid() {
            _health.value = FitHealth.Valid
            clearBuffer()
        }

        private fun clearBuffer() {
            buffer.forEach { it.spool.dir.deleteRecursively() }
            buffer.clear()
        }

        // Under the lock: analyse mutates the buffer and reads the spool. The index reset keeps the next leg's frame numbers at 0.
        private suspend fun discardSpool() {
            analysisLock.withLock {
                segmenter.close()
                lastSample = null
                lastTick = null
                buffer.clear()
                spool.deleteRecursively()
                current = newSpool()
            }
        }

        private fun bearingGap(
            a: Double,
            b: Double,
        ): Double {
            val d = Math.abs(a - b) % 360.0
            return if (d > 180.0) 360.0 - d else d
        }

        private fun measureMoves(
            leg: CapturedLeg,
            feetX: Double,
            feetY: Double,
            w: Int,
            h: Int,
        ): List<LegMove> {
            val frames = leg.frames()
            val lat0 = leg.positions[0].latitude
            val offsets = leg.positions.map { CalibrationWalkMath.alongTrackOffset(leg.positions[0], it, leg.bearingDeg, lat0) }
            val walk = WalkLeg(frames, CalibrationWalkMath.movingFlags(leg.timesS, leg.positions), offsets)
            return FrameFlow.bandMoves(listOf(walk), feetX, feetY, MONITOR_KS)
        }

        private companion object {
            const val TAG = "AutoFit"
            const val SPOOL_DIR = "tap_autofit"
            const val POLL_MS = 500L
            const val MIN_STEP_M = 1.0 // moved less than this since the last tick: standing still
            const val ANALYSIS_WAIT_MS = 600_000L // the two-leg full fit measured about 4 minutes on a Pixel (investigation doc)
            val LEG_COUNT = CalibrationWalkMath.LEARNING_BEARINGS_DEG.size
            val IDLE_MODES = setOf(MockMode.JOYSTICK, MockMode.TELEPORT) // a learning walk may start from these
            val CAPTURE_MODES = IDLE_MODES + MockMode.WALK_TO // legs come from joystick moves and walks; replay and roaming are not legs
            val MONITOR_KS = listOf(4)
        }
    }
