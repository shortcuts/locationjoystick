package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared "wiki JSON" source: serves the last good fetched copy, else the bundled seed, and
 * refreshes at most every 24h. [parse] returns null for an invalid body (never cached).
 * Used by [HotLocationsRepository] and [HotRoutesRepository].
 */
internal class WikiJsonCache<T>(
    private val cacheFile: File,
    private val seedJson: () -> String,
    private val parse: (String) -> List<T>?,
    internal var url: String,
) {
    internal var client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(AppConstants.HotLocationsConstants.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(AppConstants.HotLocationsConstants.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()

    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items

    /** Serves the cached copy, else the seed. Reads disk, so it runs on IO. */
    suspend fun load() {
        _items.value =
            withContext(Dispatchers.IO) {
                runCatching { parse(cacheFile.readText()) }.getOrNull()
                    ?: runCatching { parse(seedJson()) }.getOrNull()
                    ?: emptyList()
            }
    }

    /** True only when a fresh, valid list was fetched and stored. */
    suspend fun refreshIfStale(): Boolean =
        withContext(Dispatchers.IO) {
            load()
            val now = System.currentTimeMillis()
            val hasCache = cacheFile.exists()
            // ponytail: file mtime as clock; move to DataStore keys if a second consumer needs the timestamp.
            if (hasCache && now - cacheFile.lastModified() < AppConstants.HotLocationsConstants.CHECK_INTERVAL_MS) {
                return@withContext false
            }
            val fetched =
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("User-Agent", AppConstants.UpdateCheckConstants.userAgent())
                            .build()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string() ?: return@use null
                        parse(body)?.let { body to it }
                    }
                }.getOrNull()
            if (fetched == null) {
                // A failed check also waits 24h; with no cache a retry happens next launch.
                if (hasCache) cacheFile.setLastModified(now)
                return@withContext false
            }
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(fetched.first)
            if (!tmp.renameTo(cacheFile)) {
                tmp.delete()
                return@withContext false
            }
            _items.value = fetched.second
            true
        }
}
