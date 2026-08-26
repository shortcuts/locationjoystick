package com.locationjoystick.core.data

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches real-world ground elevation from Open-Meteo's keyless elevation endpoint
 * (Copernicus GLO-90 DEM) — no API key, no rate-limit header, single-point lookups.
 */
@Singleton
class ElevationRepository
    @Inject
    constructor() {
        internal var client: OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(AppConstants.ElevationConstants.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(AppConstants.ElevationConstants.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .build()

        internal var baseUrl: String = AppConstants.ElevationConstants.BASE_URL

        suspend fun fetchElevationMeters(
            lat: Double,
            lon: Double,
        ): Double? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = "$baseUrl?latitude=$lat&longitude=$lon"
                    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string() ?: return@use null
                        JSONObject(body).getJSONArray("elevation").optDouble(0).takeUnless { it.isNaN() }
                    }
                }.getOrNull()
            }
    }
