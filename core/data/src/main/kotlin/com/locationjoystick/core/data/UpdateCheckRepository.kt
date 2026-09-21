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

/** Checks GitHub Releases for the latest published version (see docs/features/update-check.md). */
@Singleton
class UpdateCheckRepository
    @Inject
    constructor() {
        internal var client: OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(AppConstants.UpdateCheckConstants.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(AppConstants.UpdateCheckConstants.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .build()

        internal var apiUrl: String = AppConstants.UpdateCheckConstants.GITHUB_API_URL

        /** Returns the latest release's version (no "v" prefix), or null on any failure. */
        suspend fun fetchLatestVersion(): String? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url(apiUrl)
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", AppConstants.UpdateCheckConstants.userAgent())
                            .build()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string() ?: return@use null
                        JSONObject(body).getString("tag_name").removePrefix("v")
                    }
                }.getOrNull()
            }
    }
