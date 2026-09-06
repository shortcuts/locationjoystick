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

/** One changelog entry: category is "feat" or "fix", scope matches an AGENTS.md feature area or "General". */
data class WhatsNewEntry(
    val category: String,
    val scope: String,
    val summary: String,
)

/**
 * Fetches the current version's "what's new" entries from the wiki (see
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

        internal var baseUrl: String = AppConstants.WhatsNewConstants.BASE_URL

        suspend fun fetchEntries(version: String): List<WhatsNewEntry>? =
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = "$baseUrl${version.substringBefore("-")}.json"
                    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val body = resp.body?.string() ?: return@use null
                        val entriesJson = JSONObject(body).getJSONArray("entries")
                        List(entriesJson.length()) { i ->
                            val e = entriesJson.getJSONObject(i)
                            WhatsNewEntry(e.getString("category"), e.getString("scope"), e.getString("summary"))
                        }.takeIf { it.isNotEmpty() }
                    }
                }.getOrNull()
            }
    }
