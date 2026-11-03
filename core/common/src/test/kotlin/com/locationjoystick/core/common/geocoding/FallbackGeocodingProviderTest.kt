package com.locationjoystick.core.common.geocoding

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class FallbackGeocodingProviderTest {
    private class Fake(
        val hit: GeocodeResult?,
        var failure: Exception? = null,
    ) : GeocodingProvider {
        var calls = 0

        override suspend fun search(query: String): List<GeocodeResult> {
            calls++
            failure?.let { throw it }
            return listOfNotNull(hit)
        }

        override suspend fun reverse(
            lat: Double,
            lon: Double,
        ): ReverseGeocodeResult? {
            calls++
            failure?.let { throw it }
            return hit?.let { ReverseGeocodeResult(it.displayName, null, null) }
        }
    }

    private val cooldown = AppConstants.GeocodingConstants.PROVIDER_COOLDOWN_MS
    private var now = 0L
    private val a = Fake(GeocodeResult(1.0, 1.0, "A"), IOException("down"))
    private val b = Fake(GeocodeResult(2.0, 2.0, "B"))
    private val sut = FallbackGeocodingProvider(listOf(a, b)) { now }

    @Test
    fun `failure falls through to next provider in the same call`() =
        runTest {
            assertEquals("B", sut.search("x").single().displayName)
            assertEquals(1, a.calls)
        }

    @Test
    fun `failed provider is skipped during cooldown and retried after it expires`() =
        runTest {
            sut.search("x")
            now = cooldown - 1
            sut.search("x")
            assertEquals(1, a.calls)
            now = cooldown
            a.failure = null
            assertEquals("A", sut.search("x").single().displayName)
            assertEquals(2, a.calls)
            assertEquals("A", sut.search("x").single().displayName)
        }

    @Test
    fun `cooldown is shared between search and reverse`() =
        runTest {
            sut.search("x")
            assertEquals("B", sut.reverse(0.0, 0.0)?.locality)
            assertEquals(1, a.calls)
        }

    @Test
    fun `empty result is not a failure`() =
        runTest {
            val empty = Fake(null)
            val s = FallbackGeocodingProvider(listOf(empty, b)) { now }
            assertEquals(emptyList<GeocodeResult>(), s.search("x"))
            assertNull(s.reverse(0.0, 0.0))
            assertEquals(0, b.calls)
        }

    @Test
    fun `all on cooldown are tried anyway and all failing yields nothing`() =
        runTest {
            b.failure = IOException("down")
            assertEquals(emptyList<GeocodeResult>(), sut.search("x"))
            assertNull(sut.reverse(0.0, 0.0))
            assertEquals(2, a.calls)
            assertEquals(2, b.calls)
        }

    @Test
    fun `cancellation propagates and sets no cooldown`() =
        runTest {
            a.failure = CancellationException("cancelled")
            try {
                sut.search("x")
                fail("expected CancellationException")
            } catch (_: CancellationException) {
            }
            a.failure = null
            assertEquals("A", sut.search("x").single().displayName)
        }

    @Test
    fun `disabled provider is never called`() =
        runTest {
            a.failure = null
            val s = FallbackGeocodingProvider(listOf(a, b), { it !== a }) { now }
            assertEquals("B", s.search("x").single().displayName)
            assertEquals("B", s.reverse(0.0, 0.0)?.locality)
            assertEquals(0, a.calls)
        }

    @Test
    fun `all disabled behaves as all enabled`() =
        runTest {
            a.failure = null
            val s = FallbackGeocodingProvider(listOf(a, b), { false }) { now }
            assertEquals("A", s.search("x").single().displayName)
        }

    @Test
    fun `cooldown fallback does not revive a disabled provider`() =
        runTest {
            a.failure = null
            b.failure = IOException("down")
            val s = FallbackGeocodingProvider(listOf(a, b), { it !== a }) { now }
            assertEquals(emptyList<GeocodeResult>(), s.search("x"))
            assertEquals(emptyList<GeocodeResult>(), s.search("x"))
            assertEquals(0, a.calls)
        }

    @Test
    fun `offline provider answers when both online services fail`() =
        runTest {
            a.failure = IOException("network unreachable")
            b.failure = IOException("network unreachable")
            val offline = Fake(GeocodeResult(48.0, 2.0, "Paris, France"))
            val s = FallbackGeocodingProvider(listOf(a, b, offline)) { now }
            val result = s.search("paris")
            assertEquals("Paris, France", result.single().displayName)
            assertEquals(1, a.calls)
            assertEquals(1, b.calls)
            assertEquals(1, offline.calls)
        }
}
