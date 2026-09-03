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
 * Fetches the current version's "what's new" highlights from the wiki (see
 * docs/features/whats-new.md) — the app never carries its own copy, so the in-app popup and
 * the website changelog can never drift apart.
 */
@Singleton
class WhatsNewRepository
    @Inject
    constructor() {
        internal var client: OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(AppConstants.WhatsNewConstants.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(AppConstants.WhatsNewConstants.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .build()

        suspend fun fetchHighlights(version: String): List<String>? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = AppConstants.WhatsNewConstants.buildUrl(version)
                    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string() ?: return@use null
                        val highlights = JSONObject(body).getJSONArray("highlights")
                        List(highlights.length()) { i -> highlights.getString(i) }.takeIf { it.isNotEmpty() }
                    }
                }.getOrNull()
            }
    }
