package com.locationjoystick.core.location

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.CalibrationWalkMath
import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.GroundFit
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.GroundOffset
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

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
        private var lastTick: Pair<LatLng, Long>? = null
        private var last: Sample? = null
        private val moves = ArrayDeque<LegMove>()
        private var runningFit: GroundFit? = null
        private val _walkFit = MutableStateFlow<GroundCalibration?>(null)

        /**
         * The latest measured map when it overrode the map a tap would use and is newer than the stored map, else null.
         * A camera turned between walks leaves the stored map pointing the wrong way; this corrects the walked tap
         * within seconds, before the stored map may be replaced. It outlives the walk: it is still the freshest guess.
         */
        val walkFit: StateFlow<GroundCalibration?> = _walkFit.asStateFlow()

        private var aim: Aim = Aim.Off
        private val _aiming = MutableStateFlow(false)

        /** True from [startWalk] until two headings agree or the walk ends: the tap's direction is a guess. */
        val aiming: StateFlow<Boolean> = _aiming.asStateFlow()

        private var lastHeading: Double? = null
        private val wake = Channel<Unit>(Channel.CONFLATED)

        /**
         * A tap starts a new walk: the camera may have turned since any earlier frame, so the next tick drops them and takes
         * a reference frame instead of waiting for the player to move.
         */
        fun startWalk() {
            transition { Aim.Due }
            wake.trySend(Unit)
        }

        private sealed interface Aim {
            data object Off : Aim

            /** A tap was seen; the next tick takes its reference frame. */
            data object Due : Aim

            /** The tap came mid-walk: the game's camera still trails the old direction until [untilMs]. */
            data class Settling(
                val untilMs: Long,
            ) : Aim

            /** [ref] is trusted only once the next frame shows no more map motion than the walk since explains. */
            data class Checking(
                val ref: Sample,
            ) : Aim

            /** Each frame is measured against [ref], so the flow grows with the walk instead of restarting per move. */
            data class Anchored(
                val ref: Sample,
                val heading: Double?,
            ) : Aim
        }

        // startWalk runs on the caller's thread and the tick on the loop's, so every change goes through this lock.
        @Synchronized
        private fun transition(next: (Aim) -> Aim?) {
            val to = next(aim)
            if (to == null) {
                Log.w(TAG, "ignored aim transition from $aim")
                return
            }
            aim = to
            _aiming.value = to != Aim.Off
        }

        /** Moves [from] to [to] only if no tap replaced [from] meanwhile. */
        private fun advance(
            from: Aim,
            to: Aim,
        ) = transition { if (it === from) to else null }

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
                    withTimeoutOrNull(maxOf(0L, tickIntervalMs() - (nowMs() - started))) { wake.receive() }
                }
            } finally {
                endWalk()
                transition { Aim.Off }
            }
        }

        // The first moves of a walk decide whether a tap walks the right way, so they are sampled fast; later ones slowly, for battery.
        internal fun tickIntervalMs(): Long =
            if (moves.size < FAST_MOVES) {
                AppConstants.TapToWalkConstants.FLOW_FAST_INTERVAL_MS
            } else {
                AppConstants.TapToWalkConstants.FLOW_FRAME_INTERVAL_MS
            }

        /** One tick: a screenshot only while the gate is open and the player moved since the last tick. */
        internal suspend fun tick() {
            val pos = locationRepository.currentPosition.value
            val from = shouldSample(pos)
            lastTick = pos?.let { it to nowMs() }
            when (val a = aim) {
                Aim.Due -> return startAim(a, pos, moving = from != null)
                is Aim.Settling -> return if (from == null) {
                    endWalk()
                } else if (nowMs() >= a.untilMs) {
                    takeReference(a, from)
                } else {
                    Unit
                }
                is Aim.Checking -> return if (from == null) endWalk() else checkReference(a, from)
                is Aim.Anchored -> return if (from == null) endWalk() else aimTick(a, from)
                Aim.Off -> Unit
            }
            if (from == null) return endWalk()
            // Too short a move to measure: keep the earlier frame as the reference and spare the screenshot.
            if (last?.let { it.at.distanceTo(from) < GroundFlowFit.MIN_MOVE_M } == true) return
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
                clearMoves() // a teleport or a vehicle: the camera may have changed, and the flow is too large to measure
                return
            }
            val move = nextMove(prev, cur) ?: return
            // The game's camera trails the mock position, so a move across a turn (a re-aim, a new tap) measures neither leg.
            if (moves.lastOrNull()?.let { turnDeg(it.d, move.d) > TURN_MAX_DEG } == true) return clearMoves()
            if (runningFit?.let { GroundFlowFit.changesCamera(it, move, crops.width, crops.height) } == true) {
                clearMoves() // a pinch or twist: the moves before it belong to another camera
            }
            moves.addLast(move)
            while (moves.size > AppConstants.TapToWalkConstants.FLOW_WINDOW_MOVES) moves.removeFirst()
            val window = moves.toList()
            val stored = settingsRepository.getGroundCalibration().first()?.takeIf { it.matches(crops.width, crops.height) }
            val tapMap = _walkFit.value ?: stored?.calibration ?: GroundFlowFit.provisional()
            val heading = withContext(analysisDispatcher) { GroundFlowFit.heading(window, crops.width, crops.height, tapMap.lateralPxPerM) }
            // One move's heading was over 90 degrees off on real frames, so a heading counts only once the next window agrees.
            val agreed = heading != null && lastHeading?.let { GroundFlowFit.headingGap(it, heading) <= AGREE_DEG } == true
            if (heading != null) lastHeading = heading
            if (agreed && GroundFlowFit.headingGap(heading!!, tapMap.headingDeg) > OVERRIDE_DEG) {
                Log.d(TAG, "walk heading $heading deg overrides the tap map ${tapMap.headingDeg} deg")
                _walkFit.value = GroundFlowFit.calibration(heading, tapMap.lateralPxPerM)
            }
            val fit = withContext(analysisDispatcher) { GroundFlowFit.fit(window, crops.width, crops.height) } ?: return
            runningFit = fit
            // One move's crops can fit, but one bad pair of screenshots must not replace a good stored map.
            if (moves.size < MIN_MOVES) return
            // Burst catch-ups can pull the two-term fit off; the flow direction must back its heading before it is stored.
            if (heading == null || GroundFlowFit.headingGap(fit.calibration.headingDeg, heading) > FIT_BACKED_DEG) return
            val storedMedian =
                stored?.let {
                    withContext(
                        analysisDispatcher,
                    ) { GroundFlowFit.medianResidual(it.calibration, window, crops.width, crops.height) }
                }
            if (maybePersist(fit, crops, stored, storedMedian)) _walkFit.value = null
        }

        private suspend fun startAim(
            due: Aim,
            pos: LatLng?,
            moving: Boolean,
        ) {
            last = null
            clearMoves()
            when {
                pos == null || !active.value || locationRepository.mockLocationState.value != MockLocationState.RUNNING ->
                    advance(
                        due,
                        Aim.Off,
                    )
                // A frame taken now would mix the old walk's trailing camera into every flow measured against it.
                moving -> advance(due, Aim.Settling(nowMs() + SETTLE_MS))
                else -> takeReference(due, pos)
            }
        }

        // The gate still applies: captureCrops returns null unless consent, screen and the game in front allow a frame.
        private suspend fun takeReference(
            from: Aim,
            pos: LatLng,
        ) {
            val crops = withContext(analysisDispatcher) { screenCaptureSource.captureCrops() } ?: return advance(from, Aim.Off)
            advance(from, Aim.Checking(Sample(crops, pos, nowMs())))
        }

        // A camera still animating after the game resumed confirmed headings far off on real taps.
        private suspend fun checkReference(
            c: Aim.Checking,
            from: LatLng,
        ) {
            val crops = withContext(analysisDispatcher) { screenCaptureSource.captureCrops() } ?: return advance(c, Aim.Off)
            val scale = tapMap(crops).lateralPxPerM
            val steady = withContext(analysisDispatcher) { GroundFlowFit.steady(c.ref.crops, crops, c.ref.at.distanceTo(from), scale) }
            val cur = Sample(crops, from, nowMs())
            if (!steady) return advance(c, Aim.Checking(cur))
            val anchored = Aim.Anchored(c.ref, null)
            advance(c, anchored)
            if (c.ref.at.distanceTo(from) >= GroundFlowFit.MIN_MOVE_M) measure(anchored, cur)
        }

        private suspend fun tapMap(crops: GroundFlowFit.Crops): GroundCalibration =
            _walkFit.value
                ?: settingsRepository
                    .getGroundCalibration()
                    .first()
                    ?.takeIf { it.matches(crops.width, crops.height) }
                    ?.calibration
                ?: GroundFlowFit.provisional()

        private suspend fun aimTick(
            a: Aim.Anchored,
            from: LatLng,
        ) {
            if (a.ref.at.distanceTo(from) < GroundFlowFit.MIN_MOVE_M) return
            val crops = withContext(analysisDispatcher) { screenCaptureSource.captureCrops() } ?: return endWalk()
            measure(a, Sample(crops, from, nowMs()))
        }

        private suspend fun measure(
            a: Aim.Anchored,
            cur: Sample,
        ) {
            val crops = cur.crops
            val from = cur.at
            if (isTeleport(a.ref, cur)) return endWalk()
            val move = nextMove(a.ref, cur) ?: return
            val tapMap = tapMap(crops)
            val heading =
                withContext(analysisDispatcher) { GroundFlowFit.heading(listOf(move), crops.width, crops.height, tapMap.lateralPxPerM) }
            val agreed = heading != null && a.heading?.let { GroundFlowFit.headingGap(it, heading) <= AGREE_DEG } == true
            if (!agreed) {
                // Past a few metres the flow nears the crop size, so the walk is measured from a newer frame.
                val ref = if (a.ref.at.distanceTo(from) >= REANCHOR_M) cur else a.ref
                return advance(a, Aim.Anchored(ref, heading ?: a.heading))
            }
            if (GroundFlowFit.headingGap(heading!!, tapMap.headingDeg) > OVERRIDE_DEG) {
                Log.d(TAG, "walk heading $heading deg overrides the tap map ${tapMap.headingDeg} deg")
                _walkFit.value = GroundFlowFit.calibration(heading, tapMap.lateralPxPerM)
            }
            Log.d(TAG, "walk heading $heading deg confirmed")
            // The measured walk goes on from here, so the fit starts with the move that confirmed the tap.
            last = cur
            moves.addLast(move)
            lastHeading = heading
            advance(a, Aim.Off)
        }

        private fun turnDeg(
            a: GroundOffset,
            b: GroundOffset,
        ): Double {
            val d = abs(Math.toDegrees(atan2(a.eastMeters, a.northMeters) - atan2(b.eastMeters, b.northMeters))) % 360.0
            return min(d, 360.0 - d)
        }

        private fun shouldSample(pos: LatLng?): LatLng? {
            val gate =
                active.value &&
                    locationRepository.mockLocationState.value == MockLocationState.RUNNING &&
                    locationRepository.currentMode.value in CAPTURE_MODES
            val moved =
                gate &&
                    pos != null &&
                    lastTick?.let { (at, ms) -> at.distanceTo(pos) >= MIN_SPEED_MPS * maxOf(nowMs() - ms, MIN_TICK_MS) / 1000.0 } == true
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

        /** Whether [fit] replaced the stored map [keep]. */
        private suspend fun maybePersist(
            fit: GroundFit,
            crops: GroundFlowFit.Crops,
            keep: StoredGroundCalibration?,
            keepMedian: Double?,
        ): Boolean {
            if (!GroundFlowFit.replaces(keep?.calibration, fit, moves.size, keepMedian)) return false
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
            return true
        }

        // Crops must not outlive the walk or the switch that allowed them (storage and consent).
        private fun endWalk() {
            last = null
            clearMoves()
            transition { if (it == Aim.Due) null else Aim.Off } // a tap that came during this tick is still due
        }

        private fun clearMoves() {
            moves.clear()
            runningFit = null
            lastHeading = null
        }

        private companion object {
            const val TAG = "AutoFit"
            const val MIN_SPEED_MPS = 0.5 / 3 // slower is standing still; Slow Walk is 0.3 m/s
            const val MIN_MOVES = 2
            const val MIN_TICK_MS = AppConstants.TapToWalkConstants.FLOW_FAST_INTERVAL_MS // a tick sooner than this still needs a full step
            const val AGREE_DEG = 20.0 // headings of one camera from consecutive windows spread about 15 degrees on real frames
            const val OVERRIDE_DEG = 15.0 // a smaller gap is within that spread; re-aiming on it only zig-zags the walk
            const val FIT_BACKED_DEG = 22.5
            const val SETTLE_MS = 1000L // the game's camera caught up with a new direction within a second on most real taps
            const val REANCHOR_M = 2.5
            const val TURN_MAX_DEG = 30.0 // a straight or gently curving walk turns less between two samples
            const val FAST_MOVES = 3 // moves sampled at the fast cadence before the walk's heading is settled
            val CAPTURE_MODES = setOf(MockMode.JOYSTICK, MockMode.TELEPORT, MockMode.WALK_TO) // replay and roaming are not measured
        }
    }
