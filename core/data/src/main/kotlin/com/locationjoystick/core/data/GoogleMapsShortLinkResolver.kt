package com.locationjoystick.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Maps "Share" links are often shortened (e.g. https://goo.gl/maps/xxxx or
 * https://maps.app.goo.gl/xxxx) and carry no coordinates themselves — the real lat/lon only
 * appears in the URL the shortener redirects to. This follows that redirect chain to recover it.
 */
@Singleton
class GoogleMapsShortLinkResolver
    @Inject
    constructor() {
        private val client =
            OkHttpClient
                .Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

        fun isShortLink(url: String): Boolean {
            val host = url.toHttpUrlOrNull()?.host ?: return false
            return host == "goo.gl" || host == "maps.app.goo.gl"
        }

        suspend fun resolve(url: String): String? =
            withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(Request.Builder().url(url).build()).execute().use { it.request.url.toString() }
                }.getOrNull()
            }
    }
