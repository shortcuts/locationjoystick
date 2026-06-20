package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportSyncClient
    @Inject
    constructor() {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(AppConstants.SyncConstants.EXPORT_FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(AppConstants.SyncConstants.EXPORT_FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

        /** Fetches the export JSON from a leader started by [ExportSyncServer]. Throws on failure. */
        suspend fun fetch(
            host: String,
            port: Int,
            token: String,
        ): String =
            withContext(Dispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url("http://$host:$port/export?token=$token")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Export fetch failed: HTTP ${response.code}" }
                    response.body?.string() ?: error("Empty export response")
                }
            }
    }
