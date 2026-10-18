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
 * user. NOT_OFF is neutral (an absent control looks the same as connected); RECONNECTED re-arms.
 */
internal class ReconnectFailureStreak {
    private var failures = 0
    private var notified = false

    fun record(result: ReconnectRunResult): Boolean {
        when (result) {
            ReconnectRunResult.NOT_OFF, ReconnectRunResult.NOT_FOUND, ReconnectRunResult.GATE_CLOSED -> return false
            ReconnectRunResult.RECONNECTED -> {
                failures = 0
                notified = false
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

    fun record(result: ReconnectRunResult): Boolean {
        if (result != ReconnectRunResult.NOT_FOUND) {
            misses = 0
            notified = false
            return false
        }
        misses++
        if (misses < ReconnectTapConstants.NOT_FOUND_NOTIFY_THRESHOLD || notified) return false
        notified = true
        return true
    }
}

/** Control center plus a Gaussian offset, clamped to stay inside the ball. Screen pixels. */
internal fun jitteredTapPoint(
    control: ReconnectControl,
    screenWidth: Int,
    random: Random,
): Pair<Int, Int> {
    val sigma = ReconnectTapConstants.TAP_SIGMA_WIDTH_FRACTION * screenWidth
    val clamp = ReconnectTapConstants.TAP_CLAMP_WIDTH_FRACTION * screenWidth

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
 * a poll window; a retap needs every poll read OFF. An absent or unknown control (null) never taps.
 */
internal suspend fun runReconnectTap(
    screenWidth: Int,
    capture: suspend () -> ReconnectControl?,
    tap: suspend (x: Int, y: Int, durationMs: Long) -> Boolean,
    random: Random = Random.Default,
    gate: () -> Boolean = { true },
): ReconnectRunResult =
    try {
        runGated(
            screenWidth,
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
    capture: suspend () -> ReconnectControl?,
    tap: suspend (x: Int, y: Int, durationMs: Long) -> Boolean,
    random: Random,
): ReconnectRunResult {
    val first = capture() ?: return ReconnectRunResult.NOT_FOUND
    if (first.state != ReconnectState.OFF) return ReconnectRunResult.NOT_OFF
    delay(ReconnectTapConstants.CONFIRM_DELAY_MS)
    capture()?.takeIf { it.state == ReconnectState.OFF } ?: return ReconnectRunResult.NOT_OFF

    repeat(ReconnectTapConstants.MAX_TAPS) { attempt ->
        delay(random.nextLong(ReconnectTapConstants.PRE_TAP_DELAY_MIN_MS, ReconnectTapConstants.PRE_TAP_DELAY_MAX_MS + 1))
        // The companion may have connected during the pause: re-read so a tap never lands on ON.
        val control =
            capture()
                ?: return if (attempt == 0) ReconnectRunResult.NOT_OFF else ReconnectRunResult.FAILED
        if (control.state == ReconnectState.ON) return ReconnectRunResult.RECONNECTED
        val (x, y) = jitteredTapPoint(control, screenWidth, random)
        tap(x, y, random.nextLong(ReconnectTapConstants.GESTURE_MIN_MS, ReconnectTapConstants.GESTURE_MAX_MS + 1))
        delay(ReconnectTapConstants.SETTLE_DELAY_MS)
        // A slow connect still reads OFF for a few seconds; retap only if every poll read is OFF.
        repeat(ReconnectTapConstants.SETTLE_POLL_READS) { read ->
            val after = capture() ?: return ReconnectRunResult.FAILED
            if (after.state == ReconnectState.ON) return ReconnectRunResult.RECONNECTED
            if (read < ReconnectTapConstants.SETTLE_POLL_READS - 1) delay(ReconnectTapConstants.SETTLE_POLL_INTERVAL_MS)
        }
    }
    return ReconnectRunResult.FAILED
}

/** Loop gate: setting, both disclosures, and a guided path that still shows the feature (docs/features/guided-paths.md). */
internal fun reconnectLoopActive(
    enabled: Boolean,
    reconnectOk: Boolean,
    accessibilityOk: Boolean,
    path: GuidedPath,
): Boolean = enabled && reconnectOk && accessibilityOk && path.showsReconnectAccessory

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
