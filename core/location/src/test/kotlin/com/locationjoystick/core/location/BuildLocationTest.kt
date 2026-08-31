package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.MockMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BuildLocationTest {
    private fun baseSnapshot(
        mode: MockMode = MockMode.TELEPORT,
        speedMs: Float = 0f,
        bearing: Float = 0f,
        lastNonZeroBearing: Float = 0f,
        hasEverMoved: Boolean = true,
        jitterIdleRadiusMeters: Double = 0.0,
        jitterMovingRadiusMeters: Double = 1.0,
        shouldApplyMovingJitter: Boolean = false,
        shouldApplyIdleJitter: Boolean = true,
        altitudeMeters: Double = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS,
        warmupStartMs: Long = 0L,
        warmupEnabled: Boolean = false,
        bearingHoldEnabled: Boolean = true,
        altitudeEnabled: Boolean = true,
        satelliteExtrasEnabled: Boolean = true,
        speedIdleVariationPct: Int = 0,
        speedMovingVariationPct: Int = 0,
        isSuspendedPhase: Boolean = false,
        cachedSatelliteCount: Int = 10,
        cachedUsedInFixCount: Int = 8,
        baseAltitudeMeters: Double = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS,
        altitudeJitterRadiusMeters: Double = AppConstants.RealismConstants.ALTITUDE_SIGMA_METERS,
    ) = LocationSnapshot(
        latitude = 48.8566,
        longitude = 2.3522,
        speedMs = speedMs,
        bearing = bearing,
        lastNonZeroBearing = lastNonZeroBearing,
        hasEverMoved = hasEverMoved,
        mode = mode,
        jitterIdleRadiusMeters = jitterIdleRadiusMeters,
        jitterMovingRadiusMeters = jitterMovingRadiusMeters,
        shouldApplyMovingJitter = shouldApplyMovingJitter,
        shouldApplyIdleJitter = shouldApplyIdleJitter,
        altitudeMeters = altitudeMeters,
        warmupStartMs = warmupStartMs,
        warmupEnabled = warmupEnabled,
        bearingHoldEnabled = bearingHoldEnabled,
        altitudeEnabled = altitudeEnabled,
        satelliteExtrasEnabled = satelliteExtrasEnabled,
        speedIdleVariationPct = speedIdleVariationPct,
        speedMovingVariationPct = speedMovingVariationPct,
        suspendedPhaseStartMs = 0L,
        isSuspendedPhase = isSuspendedPhase,
        cachedSatelliteCount = cachedSatelliteCount,
        cachedUsedInFixCount = cachedUsedInFixCount,
        baseAltitudeMeters = baseAltitudeMeters,
        altitudeJitterRadiusMeters = altitudeJitterRadiusMeters,
    )

    @Test
    fun `suspended phase reports zero speed and skips jitter`() {
        val snapshot =
            baseSnapshot(
                isSuspendedPhase = true,
                speedMs = 3.0f,
                mode = MockMode.TELEPORT,
                shouldApplyIdleJitter = true,
                jitterIdleRadiusMeters = 5.0,
            )
        val fix = buildLocation(snapshot, 1000L, Random(42))
        assertEquals(0f, fix.speedMs)
        assertEquals(48.8566, fix.latitude, 0.0)
        assertEquals(2.3522, fix.longitude, 0.0)
    }

    @Test
    fun `not suspended returns a fix`() {
        val snapshot = baseSnapshot(isSuspendedPhase = false)
        assertNotNull(buildLocation(snapshot, 1000L, Random(42)))
    }

    @Test
    fun `altitude bounded within clamp radius over 1000 ticks`() {
        val random = Random(seed = 123)
        var altitude = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS
        val min = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS - AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS
        val max = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS + AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS
        val altitudes = mutableListOf<Double>()
        repeat(1000) { tick ->
            val snap = baseSnapshot(altitudeMeters = altitude, altitudeEnabled = true)
            val fix = buildLocation(snap, tick.toLong() * 1000, random)
            assertNotNull(fix)
            fix!!
            val terrainAlt = fix.altitudeMeters
            assertTrue("Terrain altitude $terrainAlt out of bounds [$min, $max]", terrainAlt in min..max)
            altitudes.add(terrainAlt)
            altitude = terrainAlt
        }
        val variance = altitudes.map { (it - altitudes.average()) * (it - altitudes.average()) }.average()
        assertTrue("Altitude variance $variance should be > 0", variance > 0.0)
    }

    @Test
    fun `altitude disabled returns constant`() {
        val snap = baseSnapshot(altitudeEnabled = false)
        repeat(10) { tick ->
            val fix = buildLocation(snap, tick.toLong() * 1000, Random(tick))
            assertNotNull(fix)
            assertEquals(
                AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS,
                fix!!.altitudeMeters,
                0.0001,
            )
        }
    }

    @Test
    fun `altitude clamp follows a non-default base anchor`() {
        val random = Random(seed = 99)
        val base = 500.0
        var altitude = base
        val min = base - AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS
        val max = base + AppConstants.RealismConstants.ALTITUDE_CLAMP_RADIUS_METERS
        repeat(200) { tick ->
            val snap = baseSnapshot(altitudeMeters = altitude, altitudeEnabled = true, baseAltitudeMeters = base)
            val fix = buildLocation(snap, tick.toLong() * 1000, random)
            val terrainAlt = fix.altitudeMeters
            assertTrue("Terrain altitude $terrainAlt out of bounds [$min, $max]", terrainAlt in min..max)
            altitude = terrainAlt
        }
    }

    @Test
    fun `altitude disabled returns base anchor not the hardcoded default`() {
        val snap = baseSnapshot(altitudeEnabled = false, baseAltitudeMeters = 500.0)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertEquals(500.0, fix.altitudeMeters, 0.0001)
    }

    @Test
    fun `larger altitude jitter radius produces wider spread`() {
        val small = baseSnapshot(altitudeEnabled = true, altitudeJitterRadiusMeters = 0.1)
        val large = baseSnapshot(altitudeEnabled = true, altitudeJitterRadiusMeters = 5.0)
        val smallSpread =
            (1..200).map { buildLocation(small, 1000L, Random(it)).altitudeMeters - small.altitudeMeters }
        val largeSpread =
            (1..200).map { buildLocation(large, 1000L, Random(it)).altitudeMeters - large.altitudeMeters }
        val smallVariance = smallSpread.map { it * it }.average()
        val largeVariance = largeSpread.map { it * it }.average()
        assertTrue("Larger jitter radius should produce larger variance", largeVariance > smallVariance)
    }

    @Test
    fun `bearing hold when stationary with bearingHoldEnabled`() {
        val snap = baseSnapshot(speedMs = 0f, bearing = 0f, lastNonZeroBearing = 137f, bearingHoldEnabled = true)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertNotNull(fix)
        assertEquals(137f, fix!!.bearing, 0.01f)
    }

    @Test
    fun `bearing zero when stationary with bearingHoldEnabled false`() {
        val snap = baseSnapshot(speedMs = 0f, bearing = 45f, lastNonZeroBearing = 137f, bearingHoldEnabled = false)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertNotNull(fix)
        assertEquals(0f, fix!!.bearing, 0.01f)
    }

    @Test
    fun `bearing is near travel direction when moving`() {
        val snap = baseSnapshot(speedMs = 1.5f, bearing = 270f, lastNonZeroBearing = 137f)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertNotNull(fix)
        assertEquals(270f, fix!!.bearing, 5.0f)
    }

    @Test
    fun `no bearing reported before first movement`() {
        val snap = baseSnapshot(speedMs = 0f, hasEverMoved = false)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertEquals(false, fix.hasBearing)
    }

    @Test
    fun `bearing reported once moving`() {
        val snap = baseSnapshot(speedMs = 1.5f, hasEverMoved = false)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertEquals(true, fix.hasBearing)
    }

    @Test
    fun `bearing stays reported after stopping once hasEverMoved`() {
        val snap = baseSnapshot(speedMs = 0f, hasEverMoved = true)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertEquals(true, fix.hasBearing)
    }

    @Test
    fun `idle jitter with radius 0 is bit-identical`() {
        val snap = baseSnapshot(mode = MockMode.TELEPORT, jitterIdleRadiusMeters = 0.0)
        repeat(60) { tick ->
            val fix = buildLocation(snap, tick.toLong() * 1000, Random(tick))
            assertNotNull(fix)
            assertEquals(snap.latitude, fix!!.latitude, 0.0)
            assertEquals(snap.longitude, fix.longitude, 0.0)
        }
    }

    @Test
    fun `idle jitter with radius 2m deviates from center`() {
        val snap = baseSnapshot(mode = MockMode.TELEPORT, jitterIdleRadiusMeters = 2.0)
        val deviations =
            (0 until 60).map { tick ->
                val fix = buildLocation(snap, tick.toLong() * 1000, Random(tick * 17 + 3))!!
                val dlat = (fix.latitude - snap.latitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
                val dlon =
                    (fix.longitude - snap.longitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE *
                        kotlin.math.cos(Math.toRadians(snap.latitude))
                kotlin.math.sqrt(dlat * dlat + dlon * dlon)
            }
        val meanDeviation = deviations.average()
        assertTrue("Mean deviation $meanDeviation should be > 0", meanDeviation > 0.0)
        assertTrue("Mean deviation $meanDeviation should be <= 2 * 2.0m * 2", meanDeviation <= 2 * 2.0 * 2)
    }

    @Test
    fun `warmup accuracy at t=0 is near WARMUP_INITIAL_ACCURACY`() {
        val warmupStart = 1000L
        val snap = baseSnapshot(warmupEnabled = true, warmupStartMs = warmupStart)
        val fix = buildLocation(snap, warmupStart, Random(1))
        assertNotNull(fix)
        assertEquals(
            AppConstants.RealismConstants.WARMUP_INITIAL_ACCURACY_METERS.toDouble(),
            fix!!.accuracyMeters.toDouble(),
            0.1,
        )
    }

    @Test
    fun `warmup accuracy at t=30s is near LOCATION_ACCURACY_FINE`() {
        val warmupStart = 1000L
        val nowMs = warmupStart + AppConstants.RealismConstants.WARMUP_DURATION_SECONDS * 1000L
        val snap = baseSnapshot(warmupEnabled = true, warmupStartMs = warmupStart)
        val fix = buildLocation(snap, nowMs, Random(1))
        assertNotNull(fix)
        assertEquals(
            AppConstants.LocationConstants.LOCATION_ACCURACY_FINE.toDouble(),
            fix!!.accuracyMeters.toDouble(),
            0.1,
        )
    }

    @Test
    fun `bearing accuracy widens to stopped value when stationary`() {
        val snap = baseSnapshot(speedMs = 0f)
        val fix = buildLocation(snap, 1000L, Random(1))!!
        assertEquals(AppConstants.RealismConstants.BEARING_ACCURACY_STOPPED_DEGREES, fix.bearingAccuracyDegrees)
    }

    @Test
    fun `bearing accuracy tightens toward min at high speed`() {
        val snap = baseSnapshot(speedMs = AppConstants.ProfileConstants.RUN_SPEED_MPS.toFloat() * 2f)
        val results = (1..50).map { buildLocation(snap, 1000L, Random(it))!!.bearingAccuracyDegrees }
        assertTrue(
            "High-speed bearing accuracy should stay near MIN",
            results.all { it <= AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_MIN_DEGREES + 5f },
        )
    }

    @Test
    fun `bearing accuracy stays within moving bounds`() {
        val snap = baseSnapshot(speedMs = 0.5f)
        val results = (1..200).map { buildLocation(snap, 1000L, Random(it))!!.bearingAccuracyDegrees }
        val min = AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_MIN_DEGREES
        val max = AppConstants.RealismConstants.BEARING_ACCURACY_MOVING_MAX_DEGREES
        assertTrue(
            "Moving bearing accuracy should stay within [MIN, MAX]",
            results.all { it in min..max },
        )
    }

    @Test
    fun `sub-accuracy fields are populated`() {
        val snap = baseSnapshot()
        val fix = buildLocation(snap, 1000L, Random(1))
        assertNotNull(fix)
        assertTrue(fix!!.verticalAccuracyMeters > 0f)
        assertTrue(fix.bearingAccuracyDegrees > 0f)
        assertTrue(fix.speedAccuracyMps > 0f)
    }

    @Test
    fun `satellite extras present when enabled`() {
        val snap = baseSnapshot(satelliteExtrasEnabled = true, cachedSatelliteCount = 10, cachedUsedInFixCount = 8)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertNotNull(fix)
        assertNotNull(fix!!.satelliteCount)
        assertNotNull(fix.usedInFixCount)
    }

    @Test
    fun `satellite count from snapshot is used verbatim in fix`() {
        val snap = baseSnapshot(satelliteExtrasEnabled = true, cachedSatelliteCount = 13, cachedUsedInFixCount = 9)
        val fix = buildLocation(snap, 1000L, Random(1))!!
        assertEquals(13, fix.satelliteCount)
        assertEquals(9, fix.usedInFixCount)
    }

    @Test
    fun `satellite extras null when disabled`() {
        val snap = baseSnapshot(satelliteExtrasEnabled = false)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertNotNull(fix)
        assertNull(fix!!.satelliteCount)
        assertNull(fix.usedInFixCount)
    }

    @Test
    fun `moving jitter applied when shouldApplyMovingJitter is true and speed is positive`() {
        val snap =
            baseSnapshot(
                mode = MockMode.JOYSTICK,
                speedMs = 1.4f,
                jitterMovingRadiusMeters = 5.0,
                jitterIdleRadiusMeters = 0.0,
                shouldApplyMovingJitter = true,
            )
        val deviations =
            (1..40).map { seed ->
                val fix = buildLocation(snap, 1000L, Random(seed))!!
                val dlat = (fix.latitude - snap.latitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
                val dlon =
                    (fix.longitude - snap.longitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE *
                        kotlin.math.cos(Math.toRadians(snap.latitude))
                kotlin.math.sqrt(dlat * dlat + dlon * dlon)
            }
        assertTrue("Moving jitter must produce non-zero deviation", deviations.any { it > 0.0 })
        assertTrue("Moving jitter deviation must not exceed 2×radius", deviations.all { it <= 2 * 5.0 * 2 })
    }

    @Test
    fun `moving jitter not applied when shouldApplyMovingJitter is false`() {
        val snap =
            baseSnapshot(
                mode = MockMode.JOYSTICK,
                speedMs = 1.4f,
                jitterMovingRadiusMeters = 5.0,
                jitterIdleRadiusMeters = 0.0,
                shouldApplyMovingJitter = false,
            )
        val fix = buildLocation(snap, 1000L, Random(1))!!
        val dlat = (fix.latitude - snap.latitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
        val dlon =
            (fix.longitude - snap.longitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE *
                kotlin.math.cos(Math.toRadians(snap.latitude))
        val deviation = kotlin.math.sqrt(dlat * dlat + dlon * dlon)
        assertEquals("Moving jitter must not be applied when flag is false", 0.0, deviation, 1e-9)
    }

    @Test
    fun `warmup accuracy after warmup period varies with random seed`() {
        val warmupStart = 1000L
        // 5 seconds past the end of warmup
        val nowMs = warmupStart + AppConstants.RealismConstants.WARMUP_DURATION_SECONDS * 1000L + 5_000L
        val snap = baseSnapshot(warmupEnabled = true, warmupStartMs = warmupStart)
        val accuracies =
            (1..20).map { seed ->
                buildLocation(snap, nowMs, Random(seed))!!.accuracyMeters
            }
        // All within the coerced accuracy range
        accuracies.forEach { acc ->
            assertTrue(
                "Post-warmup accuracy $acc should be in [ACCURACY_MIN, ACCURACY_MAX]",
                acc >= AppConstants.JitterConstants.ACCURACY_MIN && acc <= AppConstants.JitterConstants.ACCURACY_MAX,
            )
        }
        // Values vary across seeds (perturbAccuracy is called, not the raw lerp value)
        assertTrue("Post-warmup accuracy should vary across seeds", accuracies.toSet().size > 1)
    }

    @Test
    fun `suspended proportion of stationary fixes matches push-pause duty cycle`() {
        val pushMs = AppConstants.RealismConstants.SUSPENDED_PUSH_DURATION_MS
        val pauseMs = AppConstants.RealismConstants.SUSPENDED_PAUSE_DURATION_MS
        val tickMs = 1_000L
        // Simulate 5 full cycles (external phase management, like updateSuspendedPhase())
        val totalMs = (pushMs + pauseMs) * 5
        var stationaryCount = 0
        var totalCount = 0
        var phaseStartMs = 0L
        var isSuspendedPhase = false
        var t = 0L
        while (t < totalMs) {
            val elapsed = t - phaseStartMs
            if (!isSuspendedPhase && elapsed >= pushMs) {
                isSuspendedPhase = true
                phaseStartMs = t
            } else if (isSuspendedPhase && elapsed >= pauseMs) {
                isSuspendedPhase = false
                phaseStartMs = t
            }
            val snap = baseSnapshot(isSuspendedPhase = isSuspendedPhase, speedMs = 5f)
            val fix = buildLocation(snap, t, Random(t.toInt()))
            if (fix.speedMs == 0f) stationaryCount++
            totalCount++
            t += tickMs
        }
        val stationaryRatio = stationaryCount.toDouble() / totalCount
        val expectedRatio = pauseMs.toDouble() / (pushMs + pauseMs)
        assertTrue(
            "Stationary ratio $stationaryRatio should be within 0.1 of expected $expectedRatio",
            kotlin.math.abs(stationaryRatio - expectedRatio) < 0.1,
        )
    }

    @Test
    fun `idle speed variation is sparse and decoupled from speed profile`() {
        val snap = baseSnapshot(speedMs = 0f, speedIdleVariationPct = 10)
        val results = (1..2000).map { buildLocation(snap, 1000L, Random(it))!!.speedMs }
        assertTrue("Most idle ticks should report exactly 0", results.count { it == 0f } > results.size / 2)
        assertTrue("Some idle ticks should still wobble", results.any { it > 0f })
        val maxExpected = AppConstants.JitterConstants.IDLE_SPEED_WOBBLE_MAX_MPS * 10 / 100.0
        assertTrue("Wobble speeds should be <= maxExpected", results.all { it <= maxExpected + 0.001f })
    }

    @Test
    fun `idle speed variation off produces zero speed when idle`() {
        val snap = baseSnapshot(speedMs = 0f, speedIdleVariationPct = 0)
        val fix = buildLocation(snap, 1000L, Random(42))!!
        assertEquals(0f, fix.speedMs, 0.001f)
    }

    @Test
    fun `moving speed variation clamps to non-negative`() {
        // Use very large variation pct to force many negative draws
        val snap = baseSnapshot(speedMs = 0.1f, speedMovingVariationPct = 50)
        val results = (1..500).map { buildLocation(snap, 1000L, Random(it))!!.speedMs }
        assertTrue("Moving speed variation must always be >= 0", results.all { it >= 0f })
    }

    @Test
    fun `moving speed variation off preserves original speed`() {
        val snap = baseSnapshot(speedMs = 1.5f, speedMovingVariationPct = 0)
        val fix = buildLocation(snap, 1000L, Random(42))!!
        assertEquals(1.5f, fix.speedMs, 0.001f)
    }

    @Test
    fun `follower stationary uses idle jitter radius not moving radius`() {
        // speedMs == 0 + FOLLOWER → idle jitter branch, governed by jitterIdleRadiusMeters
        val snap =
            baseSnapshot(
                mode = MockMode.FOLLOWER,
                speedMs = 0f,
                jitterIdleRadiusMeters = 3.0,
                // moving jitter disabled — ensures idle branch fires
                jitterMovingRadiusMeters = 0.0,
                shouldApplyIdleJitter = true,
                shouldApplyMovingJitter = false,
            )
        val deviations =
            (0 until 60).map { tick ->
                val fix = buildLocation(snap, tick.toLong() * 1000, Random(tick * 31 + 7))!!
                val dlat = (fix.latitude - snap.latitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
                val dlon =
                    (fix.longitude - snap.longitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE *
                        kotlin.math.cos(Math.toRadians(snap.latitude))
                kotlin.math.sqrt(dlat * dlat + dlon * dlon)
            }
        assertTrue("Stationary follower should deviate via idle jitter", deviations.average() > 0.0)
    }

    @Test
    fun `follower moving uses moving jitter`() {
        // speedMs > 0 + FOLLOWER → moving jitter branch
        val snap =
            baseSnapshot(
                mode = MockMode.FOLLOWER,
                speedMs = 1.4f,
                bearing = 90f,
                jitterIdleRadiusMeters = 0.0,
                jitterMovingRadiusMeters = 3.0,
                shouldApplyIdleJitter = false,
                shouldApplyMovingJitter = true,
            )
        val deviations =
            (0 until 60).map { tick ->
                val fix = buildLocation(snap, tick.toLong() * 1000, Random(tick * 13 + 5))!!
                val dlat = (fix.latitude - snap.latitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE
                val dlon =
                    (fix.longitude - snap.longitude) * AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE *
                        kotlin.math.cos(Math.toRadians(snap.latitude))
                kotlin.math.sqrt(dlat * dlat + dlon * dlon)
            }
        assertTrue("Moving follower should deviate via moving jitter", deviations.average() > 0.0)
    }
}
