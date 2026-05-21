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
        jitterIdleRadiusMeters: Double = 0.0,
        jitterMovingRadiusMeters: Double = 1.0,
        shouldApplyMovingJitter: Boolean = false,
        altitudeMeters: Double = AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS,
        warmupStartMs: Long = 0L,
        spoofingStartMs: Long = 0L,
        warmupEnabled: Boolean = false,
        bearingHoldEnabled: Boolean = true,
        altitudeEnabled: Boolean = true,
        satelliteExtrasEnabled: Boolean = true,
        suspendedMockingEnabled: Boolean = false,
        isSuspendedPhase: Boolean = false,
        cachedSatelliteCount: Int = 10,
        cachedUsedInFixCount: Int = 8,
    ) = LocationSnapshot(
        latitude = 48.8566,
        longitude = 2.3522,
        speedMs = speedMs,
        bearing = bearing,
        lastNonZeroBearing = lastNonZeroBearing,
        mode = mode,
        jitterIdleRadiusMeters = jitterIdleRadiusMeters,
        jitterMovingRadiusMeters = jitterMovingRadiusMeters,
        shouldApplyMovingJitter = shouldApplyMovingJitter,
        altitudeMeters = altitudeMeters,
        warmupStartMs = warmupStartMs,
        spoofingStartMs = spoofingStartMs,
        warmupEnabled = warmupEnabled,
        bearingHoldEnabled = bearingHoldEnabled,
        altitudeEnabled = altitudeEnabled,
        satelliteExtrasEnabled = satelliteExtrasEnabled,
        suspendedMockingEnabled = suspendedMockingEnabled,
        suspendedPhaseStartMs = 0L,
        isSuspendedPhase = isSuspendedPhase,
        cachedSatelliteCount = cachedSatelliteCount,
        cachedUsedInFixCount = cachedUsedInFixCount,
    )

    @Test
    fun `suspended phase returns null`() {
        val snapshot = baseSnapshot(isSuspendedPhase = true)
        assertNull(buildLocation(snapshot, 1000L, Random(42)))
    }

    @Test
    fun `not suspended returns non-null`() {
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
            assertTrue("Altitude ${fix.altitudeMeters} out of bounds [$min, $max]", fix.altitudeMeters in min..max)
            altitudes.add(fix.altitudeMeters)
            altitude = fix.altitudeMeters
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
            assertEquals(AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS, fix!!.altitudeMeters, 0.0001)
        }
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
    fun `bearing passthrough when moving`() {
        val snap = baseSnapshot(speedMs = 1.5f, bearing = 270f, lastNonZeroBearing = 137f)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertNotNull(fix)
        assertEquals(270f, fix!!.bearing, 0.01f)
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
    fun `satellite extras null when disabled`() {
        val snap = baseSnapshot(satelliteExtrasEnabled = false)
        val fix = buildLocation(snap, 1000L, Random(1))
        assertNotNull(fix)
        assertNull(fix!!.satelliteCount)
        assertNull(fix.usedInFixCount)
    }

    @Test
    fun `warmup accuracy after warmup period varies with random seed`() {
        val warmupStart = 1000L
        // 5 seconds past the end of warmup
        val nowMs = warmupStart + AppConstants.RealismConstants.WARMUP_DURATION_SECONDS * 1000L + 5_000L
        val snap = baseSnapshot(warmupEnabled = true, warmupStartMs = warmupStart)
        val accuracies = (1..20).map { seed ->
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
    fun `suspended null proportion matches push-pause duty cycle`() {
        val pushMs = AppConstants.RealismConstants.SUSPENDED_PUSH_DURATION_MS
        val pauseMs = AppConstants.RealismConstants.SUSPENDED_PAUSE_DURATION_MS
        val tickMs = 1_000L
        // Simulate 5 full cycles (external phase management, like updateSuspendedPhase())
        val totalMs = (pushMs + pauseMs) * 5
        var nullCount = 0
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
            val snap = baseSnapshot(suspendedMockingEnabled = true, isSuspendedPhase = isSuspendedPhase)
            if (buildLocation(snap, t, Random(t.toInt())) == null) nullCount++
            totalCount++
            t += tickMs
        }
        val nullRatio = nullCount.toDouble() / totalCount
        val expectedRatio = pauseMs.toDouble() / (pushMs + pauseMs)
        assertTrue(
            "Null ratio $nullRatio should be within 0.1 of expected $expectedRatio",
            kotlin.math.abs(nullRatio - expectedRatio) < 0.1,
        )
    }
}
