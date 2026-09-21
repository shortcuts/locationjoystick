package com.locationjoystick.core.routing

import android.content.SharedPreferences
import com.locationjoystick.core.common.constants.AppConstants
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Per-backend cooldowns after HTTP 429 (keyed by host — the rate limit is per client per host) or
 * 5xx (keyed by base URL — `/routed-foot` and `/routed-bike` are independent services). While a
 * key is cooling, callers skip that backend instead of sending a request. Expiry is wall-clock
 * epoch ms and persisted in [prefs] so an app restart does not immediately re-hit a throttling server.
 */
internal class BackendCooldowns(
    private val prefs: SharedPreferences? = null,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val until = mutableMapOf<String, Long>()

    init {
        val now = nowMs()
        prefs?.all?.forEach { (key, value) -> if (value is Long && value > now) until[key] = value }
    }

    fun isCoolingDown(baseUrl: String): Boolean {
        val now = nowMs()
        val host = hostOf(baseUrl)
        return synchronized(until) { (until[host] ?: 0L) > now || (until[baseUrl] ?: 0L) > now }
    }

    fun recordRateLimited(
        baseUrl: String,
        retryAfterMs: Long?,
    ) = set(hostOf(baseUrl), retryAfterMs ?: AppConstants.OsrmConstants.COOLDOWN_RATE_LIMITED_MS)

    fun recordServerError(baseUrl: String) = set(baseUrl, AppConstants.OsrmConstants.COOLDOWN_SERVER_ERROR_MS)

    private fun set(
        key: String,
        durationMs: Long,
    ) {
        val expiry = nowMs() + durationMs.coerceIn(0L, AppConstants.OsrmConstants.COOLDOWN_MAX_MS)
        val kept =
            synchronized(until) {
                maxOf(until[key] ?: 0L, expiry).also { until[key] = it }
            }
        prefs?.edit()?.putLong(key, kept)?.apply()
    }

    // ponytail: unparseable base URL keys by itself, so a bad URL only cools that exact string.
    private fun hostOf(baseUrl: String): String = runCatching { baseUrl.toHttpUrl().host }.getOrDefault(baseUrl)
}
