package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.common.constants.AppConstants.GameConstants
import com.locationjoystick.core.common.constants.AppConstants.ReconnectTapConstants
import com.locationjoystick.core.model.GuidedPath
import com.locationjoystick.core.model.ReconnectRunResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Counts consecutive FAILED runs; [record] returns true once per streak when it is time to tell the
 * user. NOT_OFF (the control read ON) and RECONNECTED re-arm; NOT_FOUND and GATE_CLOSED are neutral.
 */
internal class ReconnectFailureStreak {
    private var failures = 0
    private var notified = false

    fun reset() {
        failures = 0
        notified = false
    }

    fun record(result: ReconnectRunResult): Boolean {
        when (result) {
            ReconnectRunResult.NOT_FOUND, ReconnectRunResult.GATE_CLOSED -> return false
            ReconnectRunResult.RECONNECTED, ReconnectRunResult.NOT_OFF -> {
                reset()
                return false
            }
            ReconnectRunResult.FAILED -> failures++
        }
        if (failures < ReconnectTapConstants.FAILURE_NOTIFY_THRESHOLD || notified) return false
        notified = true
        return true
    }
}

/**
 * Counts consecutive NOT_FOUND runs; [record] returns true once per streak. Any other result found
 * the control (or the gate was closed, so the game was not in front) and re-arms.
 */
internal class ReconnectNotFoundStreak {
    private var misses = 0
    private var notified = false

    fun reset() {
        misses = 0
        notified = false
    }

    fun record(result: ReconnectRunResult): Boolean {
        if (result != ReconnectRunResult.NOT_FOUND) {
            reset()
            return false
        }
        misses++
        if (misses < ReconnectTapConstants.NOT_FOUND_NOTIFY_THRESHOLD || notified) return false
        notified = true
        return true
    }
}

/**
 * Delay before the next check. A FAILED run doubles it up to [maxMs]: a companion that stays
 * unreachable after a tap is likely away, so retrying at the base rate wastes taps and battery.
 * RECONNECTED and NOT_OFF reset it; NOT_FOUND and GATE_CLOSED are neutral, as in the streaks.
 */
internal class ReconnectBackoff(
    private val baseMs: Long,
    private val maxMs: Long,
) {
    var delayMs = baseMs
        private set

    fun record(result: ReconnectRunResult) {
        when (result) {
            ReconnectRunResult.FAILED -> delayMs = (delayMs * 2).coerceAtMost(maxMs)
            ReconnectRunResult.RECONNECTED, ReconnectRunResult.NOT_OFF -> delayMs = baseMs
            ReconnectRunResult.NOT_FOUND, ReconnectRunResult.GATE_CLOSED -> Unit
        }
    }
}

/** Control center plus a Gaussian offset, clamped to stay inside the ball. Screen pixels. */
internal fun jitteredTapPoint(
    control: ReconnectControl,
    screenWidth: Int,
    screenHeight: Int,
    random: Random,
): Pair<Int, Int> {
    val shortSide = minOf(screenWidth, screenHeight)
    val sigma = ReconnectTapConstants.TAP_SIGMA_SHORT_SIDE_FRACTION * shortSide
    val clamp = ReconnectTapConstants.TAP_CLAMP_SHORT_SIDE_FRACTION * shortSide

    fun offset(): Int {
        // Box-Muller; 1 - nextDouble() keeps ln() away from zero.
        val g = sqrt(-2.0 * ln(1.0 - random.nextDouble())) * cos(2.0 * PI * random.nextDouble())
        return (g * sigma).toFloat().coerceIn(-clamp, clamp).roundToInt()
    }
    return control.centerX + offset() to control.centerY + offset()
}

/** No screenshot and no tap unless the screen is on and the game window is in front. */
internal fun reconnectGateOpen(
    screenInteractive: Boolean,
    foregroundPackage: String?,
): Boolean = screenInteractive && foregroundPackage == GameConstants.PACKAGE

/** The reconnect run also stays out while our Tap to Walk overlay is up: it takes every touch, so a gesture would walk the player. */
internal fun reconnectRunGateOpen(
    screenInteractive: Boolean,
    foregroundPackage: String?,
    ownTouchOverlayShowing: Boolean,
): Boolean = reconnectGateOpen(screenInteractive, foregroundPackage) && !ownTouchOverlayShowing

/**
 * The foreground package after a window-state event. Our own overlay windows (widget panel, tap overlay) raise events
 * with our package and a view class; they sit on top of the game without taking it off screen, so they must not count.
 * Our own activity (class name starts with the package) does cover the game and does count.
 */
internal fun foregroundAfterWindowEvent(
    current: String?,
    eventPackage: String?,
    eventClass: String?,
    ownPackage: String,
): String? = if (eventPackage == ownPackage && eventClass?.startsWith(ownPackage) != true) current else eventPackage

private class GateClosed : RuntimeException()

/**
 * One reconnect run. Taps only on a fresh OFF read: two OFF reads, then up to MAX_TAPS taps, each
 * preceded by a read taken after the pre-tap pause (ON ends the run, null never taps) and followed by
 * a poll window; a retap needs every poll read OFF. A null read before a delivered tap is NOT_FOUND, so NOT_OFF always
 * means the control read ON. A null poll is unknown: it never retaps. Undelivered taps do not count; a run with none is GATE_CLOSED.
 */
internal suspend fun runReconnectTap(
    screenWidth: Int,
    screenHeight: Int,
    capture: suspend () -> ReconnectControl?,
    tap: suspend (x: Int, y: Int, durationMs: Long) -> Boolean,
    random: Random = Random.Default,
    gate: () -> Boolean = { true },
): ReconnectRunResult =
    try {
        runGated(
            screenWidth,
            screenHeight,
            { if (!gate()) throw GateClosed() else capture() },
            { x, y, d -> if (!gate()) throw GateClosed() else tap(x, y, d) },
            random,
        )
    } catch (_: GateClosed) {
        // Left the game or screen off mid-run: neutral, so it never counts toward the failure streak.
        ReconnectRunResult.GATE_CLOSED
    }

private suspend fun runGated(
    screenWidth: Int,
    screenHeight: Int,
    capture: suspend () -> ReconnectControl?,
    tap: suspend (x: Int, y: Int, durationMs: Long) -> Boolean,
    random: Random,
): ReconnectRunResult {
    val first = capture() ?: return ReconnectRunResult.NOT_FOUND
    if (first.state != ReconnectState.OFF) return ReconnectRunResult.NOT_OFF
    delay(ReconnectTapConstants.CONFIRM_DELAY_MS)
    val second = capture() ?: return ReconnectRunResult.NOT_FOUND
    if (second.state != ReconnectState.OFF) return ReconnectRunResult.NOT_OFF

    var delivered = false
    repeat(ReconnectTapConstants.MAX_TAPS) { _ ->
        delay(random.nextLong(ReconnectTapConstants.PRE_TAP_DELAY_MIN_MS, ReconnectTapConstants.PRE_TAP_DELAY_MAX_MS + 1))
        // The companion may have connected during the pause: re-read so a tap never lands on ON.
        val control =
            capture()
                ?: return if (delivered) ReconnectRunResult.FAILED else ReconnectRunResult.NOT_FOUND
        if (control.state == ReconnectState.ON) return ReconnectRunResult.RECONNECTED
        val (x, y) = jitteredTapPoint(control, screenWidth, screenHeight, random)
        // A cancelled or rejected gesture never touched the game: no settle, no polls, no count.
        if (!tap(x, y, random.nextLong(ReconnectTapConstants.GESTURE_MIN_MS, ReconnectTapConstants.GESTURE_MAX_MS + 1))) return@repeat
        delivered = true
        delay(ReconnectTapConstants.SETTLE_DELAY_MS)
        // A slow connect still reads OFF for a few seconds; retap only if every poll read is OFF.
        var unknown = false
        repeat(ReconnectTapConstants.SETTLE_POLL_READS) { read ->
            val after = capture()
            if (after == null) {
                unknown = true
            } else if (after.state == ReconnectState.ON) {
                return ReconnectRunResult.RECONNECTED
            }
            if (read < ReconnectTapConstants.SETTLE_POLL_READS - 1) delay(ReconnectTapConstants.SETTLE_POLL_INTERVAL_MS)
        }
        // An unknown poll may hide an ON control: stop instead of tapping it.
        if (unknown) return ReconnectRunResult.FAILED
    }
    return if (delivered) ReconnectRunResult.FAILED else ReconnectRunResult.GATE_CLOSED
}

/** Loop gate: setting, accessibility disclosure, and a guided path that still shows the feature (docs/features/guided-paths.md). */
internal fun reconnectLoopActive(
    enabled: Boolean,
    accessibilityOk: Boolean,
    path: GuidedPath,
): Boolean = enabled && accessibilityOk && path.showsReconnectAccessory

/**
 * Runs one periodic check so a throwing screenshot, bitmap copy, detector or gesture only skips this
 * interval instead of ending the loop. Cancellation still propagates.
 */
internal suspend fun guardedReconnectCheck(
    onError: (Exception) -> Unit,
    check: suspend () -> Unit,
) {
    try {
        check()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
}
