package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NominatimSearchClientTest {
    private val hit = listOf(NominatimResult(1.0, 2.0, "Paris"))

    @Test
    fun `repeat query ignoring case and whitespace is served from cache`() =
        runTest {
            var calls = 0
            val client =
                NominatimSearchClient({
                    calls++
                    hit
                }, { currentTime })
            client.search("Paris")
            assertEquals(hit, client.search("  paris "))
            assertEquals(1, calls)
        }

    @Test
    fun `distinct queries start at least the min interval apart`() =
        runTest {
            val starts = mutableListOf<Long>()
            val client =
                NominatimSearchClient({
                    starts += currentTime
                    hit
                }, { currentTime })
            client.search("paris")
            client.search("london")
            assertEquals(2, starts.size)
            assertEquals(true, starts[1] - starts[0] >= AppConstants.NominatimConstants.MIN_REQUEST_INTERVAL_MS)
        }

    @Test
    fun `failure serves stale after ttl, null when nothing cached, and is not cached`() =
        runTest {
            var fail = false
            var calls = 0
            val client =
                NominatimSearchClient({
                    calls++
                    if (fail) throw IOException("down")
                    hit
                }, { currentTime })
            assertNull(
                run {
                    fail = true
                    client.search("rome")
                },
            )
            assertEquals(
                2,
                run {
                    client.search("rome")
                    calls
                },
            )
            fail = false
            client.search("paris")
            fail = true
            kotlinx.coroutines.delay(AppConstants.NominatimConstants.CACHE_TTL_MS + 1)
            assertEquals(hit, client.search("paris"))
        }
}
