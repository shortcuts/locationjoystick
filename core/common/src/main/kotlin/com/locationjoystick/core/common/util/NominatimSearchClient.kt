package com.locationjoystick.core.common.util

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NominatimResult(
    val lat: Double,
    val lon: Double,
    val displayName: String,
)

/**
 * Cached, rate-spaced wrapper around a Nominatim search call. Repeat queries are served from an
 * LRU cache, request starts are at least [AppConstants.NominatimConstants.MIN_REQUEST_INTERVAL_MS]
 * apart (usage policy: 1 request/second), and an expired entry is served only if [fetch] fails.
 *
 * @param fetch performs the request; must throw on any failure.
 */
class NominatimSearchClient(
    private val fetch: suspend (String) -> List<NominatimResult>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val cache =
        TtlLruCache<String, List<NominatimResult>>(
            AppConstants.NominatimConstants.CACHE_MAX_ENTRIES,
            AppConstants.NominatimConstants.CACHE_TTL_MS,
            nowMs,
        )
    private val mutex = Mutex()
    private var lastStartMs = Long.MIN_VALUE / 2

    /** Returns results, or null if the request failed and nothing is cached. */
    suspend fun search(query: String): List<NominatimResult>? {
        val key = query.trim().lowercase()
        cache.getFresh(key)?.let { return it }
        return mutex.withLock {
            cache.getFresh(key)?.let { return@withLock it }
            val wait = lastStartMs + AppConstants.NominatimConstants.MIN_REQUEST_INTERVAL_MS - nowMs()
            if (wait > 0) delay(wait)
            lastStartMs = nowMs()
            try {
                fetch(query).also { cache.put(key, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                cache.getStale(key)
            }
        }
    }
}
