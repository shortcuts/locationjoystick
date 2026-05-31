package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Direct unit tests for the standalone pure helpers in LocationFixBuilder:
 * [gaussianLatLonOffset], [gaussianLatLonOffsetLateral], and [perturbAccuracy].
 *
 * The [buildLocation] orchestration branches are exercised in [BuildLocationTest];
 * this suite covers the leaf functions in isolation.
 */
class LocationFixBuilderTest {
    private val metersPerDeg = AppConstants.LocationConstants.METERS_PER_LATITUDE_DEGREE

    private fun displacementMeters(
        lat: Double,
        lon: Double,
        outLat: Double,
        outLon: Double,
    ): Double {
        val dLat = (outLat - lat) * metersPerDeg
        val dLon = (outLon - lon) * metersPerDeg * cos(Math.toRadians(lat))
        return sqrt(dLat * dLat + dLon * dLon)
    }

    // -------------------------------------------------------------------------
    // gaussianLatLonOffset
    // -------------------------------------------------------------------------

    @Test
    fun `gaussianLatLonOffset with zero radius returns the same point`() {
        val (lat, lon) = gaussianLatLonOffset(48.8566, 2.3522, radiusMeters = 0.0, random = Random(1))
        assertEquals(48.8566, lat, 1e-12)
        assertEquals(2.3522, lon, 1e-12)
    }

    @Test
    fun `gaussianLatLonOffset is deterministic for a fixed seed`() {
        val a = gaussianLatLonOffset(48.8566, 2.3522, 5.0, Random(7))
        val b = gaussianLatLonOffset(48.8566, 2.3522, 5.0, Random(7))
        assertEquals(a.first, b.first, 0.0)
        assertEquals(a.second, b.second, 0.0)
    }

    @Test
    fun `gaussianLatLonOffset keeps the vast majority of samples within 3x radius`() {
        val radius = 4.0
        val lat = 48.8566
        val lon = 2.3522
        val displacements =
            (1..500).map { seed ->
                val (oLat, oLon) = gaussianLatLonOffset(lat, lon, radius, Random(seed))
                displacementMeters(lat, lon, oLat, oLon)
            }
        // For a 2-D Gaussian with sigma = radius, P(magnitude > 3*sigma) is tiny (~1%).
        val withinThreeSigma = displacements.count { it <= 3.0 * radius }
        assertTrue(
            "At least 95% of samples should fall within 3x radius, got $withinThreeSigma/500",
            withinThreeSigma >= 475,
        )
        // And the spread is genuinely non-trivial (not collapsed to the center).
        assertTrue("Offset should move the point", displacements.any { it > 0.0 })
    }

    // -------------------------------------------------------------------------
    // gaussianLatLonOffsetLateral
    // -------------------------------------------------------------------------

    @Test
    fun `gaussianLatLonOffsetLateral stays near the input point`() {
        val radius = 3.0
        val lat = 48.8566
        val lon = 2.3522
        val displacements =
            (1..300).map { seed ->
                val (oLat, oLon) =
                    gaussianLatLonOffsetLateral(
                        lat,
                        lon,
                        radius,
                        bearingDeg = 90f,
                        longitudinalFraction = 0.2,
                        random = Random(seed),
                    )
                displacementMeters(lat, lon, oLat, oLon)
            }
        val withinBound = displacements.count { it <= 3.0 * radius }
        assertTrue("Lateral jitter should stay within 3x radius for most samples", withinBound >= 285)
    }

    @Test
    fun `gaussianLatLonOffsetLateral with longitudinalFraction 1 behaves isotropically`() {
        // With full longitudinal fraction, the bearing-aligned and perpendicular components
        // have equal magnitude, so total displacement magnitude is bearing-invariant.
        val radius = 5.0
        val lat = 10.0
        val lon = 20.0
        (1..50).forEach { seed ->
            val north =
                gaussianLatLonOffsetLateral(lat, lon, radius, bearingDeg = 0f, longitudinalFraction = 1.0, random = Random(seed))
            val east =
                gaussianLatLonOffsetLateral(lat, lon, radius, bearingDeg = 90f, longitudinalFraction = 1.0, random = Random(seed))
            val dNorth = displacementMeters(lat, lon, north.first, north.second)
            val dEast = displacementMeters(lat, lon, east.first, east.second)
            assertEquals(
                "Magnitude must be bearing-invariant when longitudinalFraction = 1.0 (seed $seed)",
                dNorth,
                dEast,
                1e-6,
            )
        }
    }

    @Test
    fun `gaussianLatLonOffsetLateral with longitudinalFraction 0 has no along-track component`() {
        // bearing = 0 (north). With zero longitudinal fraction, the offset is purely lateral,
        // i.e. purely east-west: latitude must not change.
        val lat = 10.0
        val lon = 20.0
        (1..50).forEach { seed ->
            val (oLat, _) =
                gaussianLatLonOffsetLateral(
                    lat,
                    lon,
                    radiusMeters = 6.0,
                    bearingDeg = 0f,
                    longitudinalFraction = 0.0,
                    random = Random(seed),
                )
            assertEquals("Latitude must be unchanged for pure lateral jitter heading north (seed $seed)", lat, oLat, 1e-9)
        }
    }

    // -------------------------------------------------------------------------
    // perturbAccuracy
    // -------------------------------------------------------------------------

    @Test
    fun `perturbAccuracy output is always within ACCURACY_MIN and ACCURACY_MAX`() {
        val base = AppConstants.LocationConstants.LOCATION_ACCURACY_FINE
        (1..500).forEach { seed ->
            val acc = perturbAccuracy(base, Random(seed))
            assertTrue(
                "Accuracy $acc should be within [${AppConstants.JitterConstants.ACCURACY_MIN}, " +
                    "${AppConstants.JitterConstants.ACCURACY_MAX}]",
                acc >= AppConstants.JitterConstants.ACCURACY_MIN && acc <= AppConstants.JitterConstants.ACCURACY_MAX,
            )
        }
    }

    @Test
    fun `perturbAccuracy stays within half the perturbation range of base when unclamped`() {
        val base = AppConstants.LocationConstants.LOCATION_ACCURACY_FINE
        val halfRange = (AppConstants.JitterConstants.ACCURACY_PERTURBATION_RANGE / 2).toFloat()
        (1..500).forEach { seed ->
            val acc = perturbAccuracy(base, Random(seed))
            // The raw perturbation is in [-halfRange, +halfRange]; the clamp only narrows it.
            assertTrue(
                "Accuracy $acc should be near base $base (within $halfRange) for seed $seed",
                acc >= base - halfRange - 1e-3 && acc <= base + halfRange + 1e-3,
            )
        }
    }

    @Test
    fun `perturbAccuracy is deterministic for a fixed seed`() {
        val base = AppConstants.LocationConstants.LOCATION_ACCURACY_FINE
        assertEquals(perturbAccuracy(base, Random(99)), perturbAccuracy(base, Random(99)), 0.0f)
    }
}
