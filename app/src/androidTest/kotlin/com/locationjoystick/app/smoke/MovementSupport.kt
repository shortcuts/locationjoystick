package com.locationjoystick.app.smoke

import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteProgress
import com.locationjoystick.core.model.bearingTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Collections

/** The movement tests run at the speed cap, so one 1 s engine tick moves at most this far. */
const val STEP_M = 15.0
const val BACKTRACK_TOLERANCE_M = 2 * STEP_M
const val ARRIVE_M = 2.0
const val PASS_M = 8.0
const val ARRIVE_TIMEOUT_MS = 90_000L
const val LEG_M = 300.0

val ORIGIN = LatLng(48.8566, 2.3522)
val STOP_1 = offset(ORIGIN, 0.0, LEG_M)
val STOP_2 = offset(STOP_1, 90.0, LEG_M)
val STOP_3 = offset(STOP_2, 180.0, LEG_M)

fun offset(
    from: LatLng,
    bearingDeg: Double,
    meters: Double,
): LatLng = advancePosition(from.latitude, from.longitude, bearingDeg, meters).let { LatLng(it.first, it.second) }

fun distance(
    a: LatLng,
    b: LatLng,
): Double = haversineDistance(a, b)

const val DETOUR_M = 20.0

fun detourPoint(
    from: LatLng,
    to: LatLng,
): LatLng = offset(midpoint(from, to), from.bearingTo(to) + 90.0, DETOUR_M)

private fun midpoint(
    a: LatLng,
    b: LatLng,
) = LatLng((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2)

/** A road per leg A to B that visits the midpoint M twice, with a [DETOUR_M] spur S between the visits. */
fun selfOverlappingRoad(requested: List<LatLng>): List<LatLng> =
    requested.zipWithNext().flatMapIndexed { i, (a, b) ->
        val m = midpoint(a, b)
        val road = listOf(a, m, detourPoint(a, b), m, b)
        if (i == 0) road else road.drop(1)
    }

/** Collects every position and every distinct stop counter the app publishes while a test runs. */
class MovementRecorder(
    private val locationRepository: LocationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val _positions = Collections.synchronizedList(mutableListOf<LatLng>())
    private val _progress = Collections.synchronizedList(mutableListOf<RouteProgress>())

    val positions: List<LatLng> get() = synchronized(_positions) { _positions.toList() }
    val progress: List<RouteProgress> get() = synchronized(_progress) { _progress.toList() }

    fun start() {
        scope.launch { locationRepository.currentPosition.collect { p -> p?.let { _positions.add(it) } } }
        scope.launch {
            locationRepository.routeProgress.collect { p ->
                if (p != null && _progress.lastOrNull() != p) _progress.add(p)
            }
        }
    }

    /** Drops the setup samples but keeps the current position, so a test judges only the movement it starts. */
    fun clear() {
        synchronized(_positions) {
            val here = _positions.lastOrNull()
            _positions.clear()
            here?.let(_positions::add)
        }
        _progress.clear()
    }

    fun stop() = scope.cancel()

    fun waitUntil(
        timeoutMs: Long = ARRIVE_TIMEOUT_MS,
        what: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timeout waiting for $what; last position ${positions.lastOrNull()}" }
            Thread.sleep(POLL_MS)
        }
    }

    /** The replay publishes the last stop one tick after the position gets within [ARRIVE_M] of it. */
    fun waitForCounterEnd() = waitUntil(what = "counter at its last stop") { progress.lastOrNull()?.let { it.current == it.total } == true }

    fun waitForArrival(stop: LatLng) =
        waitUntil(what = "arrival at $stop") {
            positions.lastOrNull()?.let { distance(it, stop) <= ARRIVE_M } ==
                true
        }

    private companion object {
        const val POLL_MS = 200L
    }
}

/** Walking the [stops] in order never lets the distance to the next stop grow by more than [tolerance]. */
fun assertNoBacktrack(
    positions: List<LatLng>,
    stops: List<LatLng>,
    tolerance: Double = BACKTRACK_TOLERANCE_M,
) {
    var next = 0
    var closest = Double.MAX_VALUE
    positions.forEachIndexed { i, p ->
        if (next >= stops.size) return
        val d = distance(p, stops[next])
        assertTrue(
            "backtrack at sample $i $p: ${"%.1f".format(d)} m from stop ${next + 1}, closest so far ${"%.1f".format(closest)} m",
            d <= closest + tolerance,
        )
        closest = minOf(closest, d)
        if (d <= PASS_M) {
            next++
            closest = Double.MAX_VALUE
        }
    }
}

fun assertVisitedInOrder(
    positions: List<LatLng>,
    stops: List<LatLng>,
    radius: Double = PASS_M,
) {
    var from = 0
    stops.forEachIndexed { i, stop ->
        val hit = positions.drop(from).indexOfFirst { distance(it, stop) <= radius }
        assertTrue("stop ${i + 1} never visited within $radius m", hit >= 0)
        from += hit
    }
}

/** The counter never counts down for a given total and ends complete. */
fun assertCounterCompletes(progress: List<RouteProgress>) {
    assertTrue("no stop counter published", progress.isNotEmpty())
    progress.zipWithNext().forEach { (a, b) ->
        if (a.total == b.total) assertTrue("counter went back from ${a.label} to ${b.label}", b.current >= a.current)
    }
    assertEquals("counter did not finish: ${progress.map { it.label }}", progress.last().total, progress.last().current)
}
