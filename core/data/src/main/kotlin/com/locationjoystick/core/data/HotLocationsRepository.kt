package com.locationjoystick.core.data

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A curated hot location entry. The identity key is [name] + [city] (the favorite ID is
 * derived from both): renaming either is a remove + add. [country] and [city] also drive
 * UI grouping.
 */
data class HotLocation(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val city: String,
)

/**
 * Serves the hot-location list: the last good copy fetched from the wiki, else the bundled
 * seed. Refreshes at most every 24h (see docs/features/favorites.md).
 */
@Singleton
class HotLocationsRepository
    internal constructor(
        cacheFile: File,
        seedJson: () -> String,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(
            File(context.filesDir, AppConstants.HotLocationsConstants.CACHE_FILE_NAME),
            {
                context.assets
                    .open(AppConstants.HotLocationsConstants.ASSET_FILE_NAME)
                    .bufferedReader()
                    .use { it.readText() }
            },
        )

        private val cache = WikiJsonCache(cacheFile, seedJson, ::parseHotLocations, AppConstants.HotLocationsConstants.URL)

        internal var client: OkHttpClient
            get() = cache.client
            set(v) {
                cache.client = v
            }

        internal var url: String
            get() = cache.url
            set(v) {
                cache.url = v
            }

        val locations: StateFlow<List<HotLocation>> = cache.items

        suspend fun load() = cache.load()

        /** True only when a fresh, valid list was fetched and stored. */
        suspend fun refreshIfStale(): Boolean = cache.refreshIfStale()
    }

/** Parses the published hot-locations JSON; null unless the body is a valid, non-empty schema-1 list. */
internal fun parseHotLocations(body: String): List<HotLocation>? =
    runCatching {
        val root = JSONObject(body)
        if (root.getInt("schema") != 1) return@runCatching null
        val items = root.getJSONArray("locations")
        List(items.length()) { i ->
            val o = items.getJSONObject(i)
            HotLocation(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getString("country"), o.getString("city"))
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()
