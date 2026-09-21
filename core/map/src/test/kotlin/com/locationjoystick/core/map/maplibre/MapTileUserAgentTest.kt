package com.locationjoystick.core.map.maplibre

import com.locationjoystick.core.common.constants.AppConstants
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTileUserAgentTest {
    @Test
    fun `user agent names the app version and docs url`() {
        val ua =
            mapTileUserAgent(
                versionName = "0.18.2",
                contactUrl = "https://shortcuts.github.io/locationjoystick/",
                androidRelease = "14",
            )
        assertEquals(
            "locationjoystick/0.18.2 (+https://shortcuts.github.io/locationjoystick; Android 14)",
            ua,
        )
    }

    @Test
    fun `default user agent uses compile-time version and docs url`() {
        val ua = mapTileUserAgent()
        assertTrue(ua.startsWith("${AppConstants.MapConstants.TILE_USER_AGENT_APP}/"))
        assertTrue(ua.contains(AppConstants.AppInfo.VERSION_NAME))
        assertTrue(ua.contains(AppConstants.AppInfo.DOCS_URL.trimEnd('/')))
        assertTrue(ua.contains("Android"))
    }

    @Test
    fun `blank version falls back to zero`() {
        val ua =
            mapTileUserAgent(
                versionName = "   ",
                contactUrl = "https://example.org",
                androidRelease = "14",
            )
        assertEquals("locationjoystick/0 (+https://example.org; Android 14)", ua)
    }

    @Test
    fun `interceptor overwrites the outgoing User-Agent`() {
        val expected = "locationjoystick/1.0 (+https://example.org)"
        var seen: String? = null
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(MapTileUserAgentInterceptor(expected))
                .addInterceptor { chain ->
                    seen = chain.request().header("User-Agent")
                    okhttp3.Response
                        .Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }.build()

        client.newCall(Request.Builder().url("http://127.0.0.1/").build()).execute().close()
        assertEquals(expected, seen)
    }

    @Test
    fun `interceptor replaces MapLibre's existing User-Agent and sets Referer`() {
        val expected = "locationjoystick/1.0 (+https://example.org; Android 14)"
        var seenUa: String? = null
        var seenReferer: String? = null
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(
                    MapTileUserAgentInterceptor(
                        userAgent = expected,
                        referer = "https://example.org",
                    ),
                ).addInterceptor { chain ->
                    seenUa = chain.request().header("User-Agent")
                    seenReferer = chain.request().header("Referer")
                    okhttp3.Response
                        .Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }.build()

        client
            .newCall(
                Request
                    .Builder()
                    .url("http://127.0.0.1/")
                    .header("User-Agent", "okhttp/4.12.0")
                    .build(),
            ).execute()
            .close()
        assertEquals(expected, seenUa)
        assertEquals("https://example.org", seenReferer)
    }

    @Test
    fun `interceptor replaces an empty User-Agent`() {
        val expected = "locationjoystick/1.0 (+https://example.org; Android 14)"
        var seen: String? = null
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(MapTileUserAgentInterceptor(expected))
                .addInterceptor { chain ->
                    seen = chain.request().header("User-Agent")
                    okhttp3.Response
                        .Builder()
                        .request(chain.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ByteArray(0).toResponseBody(null))
                        .build()
                }.build()

        client
            .newCall(
                Request
                    .Builder()
                    .url("http://127.0.0.1/")
                    .header("User-Agent", "")
                    .build(),
            ).execute()
            .close()
        assertEquals(expected, seen)
    }

    @Test
    fun `tile client keeps MapLibre's per-host concurrency`() {
        val client = mapTileHttpClient(MapTileUserAgentInterceptor("ua"))
        assertEquals(
            AppConstants.MapConstants.OSM_MAX_REQUESTS_PER_HOST,
            client.dispatcher.maxRequestsPerHost,
        )
    }

    @Test
    fun `maplibre cache names are recognized for the one-shot wipe`() {
        assertTrue(isMapLibreCacheEntry("mbgl-cache.db"))
        assertTrue(isMapLibreCacheEntry("mbgl-offline.db"))
        assertTrue(isMapLibreCacheEntry(".mapbox"))
        assertTrue(isMapLibreCacheEntry("MapboxOffline"))
        assertFalse(isMapLibreCacheEntry("locationjoystick.db"))
        assertFalse(isMapLibreCacheEntry("datastore"))
    }

    private object FakeStaticHolder {
        @JvmField
        var value: String = "initial"
    }

    @Test
    fun `staticFieldEquals reads back the current reflection value, not a stale one`() {
        val field = FakeStaticHolder.javaClass.getDeclaredField("value")
        field.isAccessible = true
        field.set(null, "updated")

        assertTrue(staticFieldEquals(FakeStaticHolder.javaClass, "value", "updated"))
        assertFalse(staticFieldEquals(FakeStaticHolder.javaClass, "value", "initial"))
    }
}
