package com.locationjoystick.feature.widget.impl

import com.locationjoystick.core.common.constants.AppConstants.GameConstants
import com.locationjoystick.core.common.constants.AppConstants.ReconnectTapConstants
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
 * user. NOT_OFF (the control read ON) and RECONNECTED re-arm; NOT_FOUND, GATE_CLOSED and PAUSED are neutral.
 * [exhausted] is the outage tap budget: true once the failures reach MAX_FAILED_TAPS_PER_OUTAGE.
 */
internal class ReconnectFailureStreak {
    private var failures = 0
    private var notified = false

    val exhausted: Boolean get() = failures >= ReconnectTapConstants.MAX_FAILED_TAPS_PER_OUTAGE

    fun reset() {
        failures = 0
        notified = false
    }

    fun record(result: ReconnectRunResult): Boolean {
        when (result) {
            ReconnectRunResult.NOT_FOUND, ReconnectRunResult.GATE_CLOSED, ReconnectRunResult.PAUSED -> return false
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

/** Times GATE_CLOSED with the screen on; [record] returns true once when the wait reaches the threshold. */
internal class ReconnectGateClosedStreak(
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var startedAt: Long? = null
    private var notified = false

    fun reset() {
        startedAt = null
        notified = false
    }

    fun record(
        result: ReconnectRunResult,
        screenInteractive: Boolean,
    ): Boolean {
        if (result != ReconnectRunResult.GATE_CLOSED || !screenInteractive) {
            reset()
            return false
        }
        val current = now()
        val start = startedAt ?: current.also { startedAt = it }
        if (notified || current - start < ReconnectTapConstants.GATE_CLOSED_NOTIFY_MS) return false
        notified = true
        return true
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
 * One reconnect run. Taps only on a fresh OFF read: two OFF reads (a null read waits UNSURE_RETRY_DELAY_MS
 * and repeats both reads once), then one tap, preceded by a read taken after the pre-tap pause (ON ends the run,
 * null never taps) and followed by POST_TAP_WAIT_MS and POST_TAP_READS reads; no ON read is FAILED. With
 * [allowTap] false an OFF control returns PAUSED instead of tapping. A null read before a delivered tap is
 * NOT_FOUND, so NOT_OFF always means the control read ON. A SEARCHING read is polled until it settles; past
 * SEARCH_MAX_WAIT_MS it counts as null. An undelivered tap is GATE_CLOSED.
 */
internal suspend fun runReconnectTap(
    screenWidth: Int,
    screenHeight: Int,
    capture: suspend () -> ReconnectControl?,
    tap: suspend (x: Int, y: Int, durationMs: Long) -> Boolean,
    random: Random = Random.Default,
    gate: () -> Boolean = { true },
    allowTap: Boolean = true,
): ReconnectRunResult =
    try {
        runGated(
            screenWidth,
            screenHeight,
            readSettled { if (!gate()) throw GateClosed() else capture() },
            { x, y, d -> if (!gate()) throw GateClosed() else tap(x, y, d) },
            random,
            allowTap,
        )
    } catch (_: GateClosed) {
        // Left the game or screen off mid-run: neutral, so it never counts toward the failure streak.
        ReconnectRunResult.GATE_CLOSED
    }

/** Waits out the game's search ring: a read that stays SEARCHING past the cap is null, which never taps. */
private fun readSettled(capture: suspend () -> ReconnectControl?): suspend () -> ReconnectControl? =
    {
        var read = capture()
        var waited = 0L
        while (read?.state == ReconnectState.SEARCHING && waited < ReconnectTapConstants.SEARCH_MAX_WAIT_MS) {
            delay(ReconnectTapConstants.SEARCH_POLL_INTERVAL_MS)
            waited += ReconnectTapConstants.SEARCH_POLL_INTERVAL_MS
            read = capture()
        }
        read.takeUnless { it?.state == ReconnectState.SEARCHING }
    }

/** The state of the first read, or of the second read after the confirm delay. Null when either read finds no control. */
private suspend fun confirmState(capture: suspend () -> ReconnectControl?): ReconnectState? {
    val first = capture() ?: return null
    if (first.state != ReconnectState.OFF) return first.state
    delay(ReconnectTapConstants.CONFIRM_DELAY_MS)
    return capture()?.state
}

private suspend fun runGated(
    screenWidth: Int,
    screenHeight: Int,
    capture: suspend () -> ReconnectControl?,
    tap: suspend (x: Int, y: Int, durationMs: Long) -> Boolean,
    random: Random,
    allowTap: Boolean,
): ReconnectRunResult {
    val confirmed =
        confirmState(capture)
            ?: run {
                delay(ReconnectTapConstants.UNSURE_RETRY_DELAY_MS)
                confirmState(capture)
            }
            ?: return ReconnectRunResult.NOT_FOUND
    if (confirmed != ReconnectState.OFF) return ReconnectRunResult.NOT_OFF
    if (!allowTap) return ReconnectRunResult.PAUSED

    delay(
        ReconnectTapConstants.PRE_TAP_DELAY_MIN_MS +
            random.nextLong(ReconnectTapConstants.PRE_TAP_DELAY_MAX_MS - ReconnectTapConstants.PRE_TAP_DELAY_MIN_MS + 1),
    )
    // The companion may have connected during the pause: re-read so a tap never lands on ON.
    val control = capture() ?: return ReconnectRunResult.NOT_FOUND
    if (control.state == ReconnectState.ON) return ReconnectRunResult.RECONNECTED
    val (x, y) = jitteredTapPoint(control, screenWidth, screenHeight, random)
    // A cancelled or rejected gesture never touched the game: no wait, no reads, no count.
    if (!tap(x, y, random.nextLong(ReconnectTapConstants.GESTURE_MIN_MS, ReconnectTapConstants.GESTURE_MAX_MS + 1))) {
        return ReconnectRunResult.GATE_CLOSED
    }
    delay(ReconnectTapConstants.POST_TAP_WAIT_MS)
    repeat(ReconnectTapConstants.POST_TAP_READS) { read ->
        if (capture()?.state == ReconnectState.ON) return ReconnectRunResult.RECONNECTED
        if (read < ReconnectTapConstants.POST_TAP_READS - 1) delay(ReconnectTapConstants.SETTLE_POLL_INTERVAL_MS)
    }
    return ReconnectRunResult.FAILED
}

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
