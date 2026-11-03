package com.locationjoystick.core.common.geocoding

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale

private const val TAG = "OfflineCityProvider"
private const val SEARCH_LIMIT = 5
private const val MIN_QUERY_LENGTH = 2
private val MARKS = Regex("\\p{Mn}+")

private class City(
    val norm: String,
    val lat: Double,
    val lon: Double,
    val displayName: String,
)

private fun normalize(text: String): String =
    MARKS.replace(Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD), "")

/**
 * Last-resort search over the bundled GeoNames list of towns with 5,000+ people, for when no online
 * provider answers. Rows arrive sorted by population, so the first prefix matches are the biggest
 * places. [open] returns null before the asset is wired.
 */
class OfflineCityProvider(
    private val open: () -> InputStream?,
) : GeocodingProvider {
    private val loadLock = Mutex()
    private var cities: List<City>? = null

    override suspend fun search(query: String): List<GeocodeResult> {
        val q = normalize(query)
        if (q.length < MIN_QUERY_LENGTH) return emptyList()
        val all = load() ?: return emptyList()
        return all
            .asSequence()
            .filter { it.norm.startsWith(q) }
            .take(SEARCH_LIMIT)
            .map { GeocodeResult(it.lat, it.lon, it.displayName) }
            .toList()
    }

    override suspend fun reverse(
        lat: Double,
        lon: Double,
    ): ReverseGeocodeResult? = null

    private suspend fun load(): List<City>? =
        loadLock.withLock {
            cities ?: withContext(Dispatchers.Default) {
                try {
                    open()?.let { stream ->
                        stream.bufferedReader().use { r -> r.lineSequence().mapNotNull(::parse).toList() }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "City list unreadable", e)
                    throw e
                }
            }?.also { cities = it }
        }

    private fun parse(line: String): City? {
        val f = line.split('\t')
        if (f.size < 4) return null
        val lat = f[1].toDoubleOrNull() ?: return null
        val lon = f[2].toDoubleOrNull() ?: return null
        val country = Locale("", f[3]).displayCountry
        return City(normalize(f[0]), lat, lon, "${f[0]}, $country")
    }
}
