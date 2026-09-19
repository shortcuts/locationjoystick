package com.locationjoystick.core.data

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A user-facing entry in the shared, versioned changelog. */
data class WhatsNewEntry(
    val category: String,
    val scope: String,
    val summary: String,
)

/** Reads the same authored JSON used to generate the wiki, packaged for offline use. */
@Singleton
class WhatsNewRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        suspend fun fetchEntries(version: String): List<WhatsNewEntry>? =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets
                        .open(AppConstants.WhatsNewConstants.assetFileName(version))
                        .bufferedReader()
                        .use { parseWhatsNewEntries(it.readText()) }
                }.getOrNull()
            }
    }

internal fun parseWhatsNewEntries(body: String): List<WhatsNewEntry>? =
    runCatching {
        val entries = JSONObject(body).getJSONArray("entries")
        List(entries.length()) { i ->
            val entry = entries.getJSONObject(i)
            WhatsNewEntry(entry.getString("category"), entry.getString("scope"), entry.getString("summary"))
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()
