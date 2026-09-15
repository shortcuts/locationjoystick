package com.locationjoystick.core.map.maplibre

import android.content.Context
import android.os.Build
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import okhttp3.Call
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File

/**
 * OSM's tile usage policy requires a User-Agent that names this app. MapLibre 13 builds each
 * tile request with its own `User-Agent` (`HttpRequestImpl.addHeader`) on a default OkHttp
 * client; generic values (`okhttp/…`, `Dalvik/…`, empty) are answered with a blocked
 * placeholder tile (blank/black map). A 200 of that placeholder is also stored in MapLibre's
 * native HTTP cache, so a later UA fix still shows black until that cache is wiped.
 * Full recipe: docs/features/map-tiles.md — do not add a second getInstance path.
 *
 * [install] is the **only** supported `MapLibre.getInstance` entry point: it inits MapLibre,
 * swaps in a client whose interceptors **replace** User-Agent (and set Referer), overwrites
 * `HttpRequestImpl`'s static UA/client fields, and once per cache-bust marker
 * (`AppConstants.MapConstants.OSM_TILE_CACHE_BUST_MARKER`) deletes cached blocked tiles.
 * The OkHttp client is built once per process with MapLibre's per-host concurrency
 * ([AppConstants.MapConstants.OSM_MAX_REQUESTS_PER_HOST], not OkHttp's default of 5).
 * Call it again from every map screen — it is idempotent.
 */
fun mapTileUserAgent(
    versionName: String = AppConstants.AppInfo.VERSION_NAME,
    contactUrl: String = AppConstants.AppInfo.DOCS_URL,
    androidRelease: String = Build.VERSION.RELEASE.orEmpty(),
): String {
    val version = versionName.trim().ifBlank { "0" }
    val contact = contactUrl.trim().trimEnd('/').ifBlank { AppConstants.AppInfo.DOCS_URL.trimEnd('/') }
    val android = androidRelease.trim().ifBlank { "unknown" }
    return "${AppConstants.MapConstants.TILE_USER_AGENT_APP}/$version (+$contact; Android $android)"
}

internal class MapTileUserAgentInterceptor(
    private val userAgent: String = mapTileUserAgent(),
    private val referer: String = AppConstants.AppInfo.DOCS_URL.trimEnd('/'),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain
                .request()
                .newBuilder()
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .build()
        val response = chain.proceed(request)
        if (response.header("x-blocked") != null) {
            Log.e(
                TAG,
                "OSM blocked a tile request (User-Agent=${request.header("User-Agent")})",
            )
        }
        return response
    }
}

internal fun mapTileHttpClient(
    interceptor: Interceptor,
    maxRequestsPerHost: Int = AppConstants.MapConstants.OSM_MAX_REQUESTS_PER_HOST,
): OkHttpClient {
    val dispatcher = Dispatcher().apply { this.maxRequestsPerHost = maxRequestsPerHost }
    return OkHttpClient
        .Builder()
        .dispatcher(dispatcher)
        .addInterceptor(interceptor)
        .addNetworkInterceptor(interceptor)
        .build()
}

object MapTileHttp {
    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        val app = context.applicationContext
        purgePoisonedTileCacheIfNeeded(app)
        MapLibre.getInstance(app)
        if (installed) return
        val userAgent = mapTileUserAgent()
        val client = mapTileHttpClient(MapTileUserAgentInterceptor(userAgent))
        HttpRequestUtil.setOkHttpClient(client)
        overwriteMapLibreHttp(client, userAgent)
        installed = true
        Log.i(TAG, "OSM tile User-Agent installed: $userAgent")
    }
}

internal fun isMapLibreCacheEntry(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("mbgl") || n.contains("mapbox")
}

internal fun purgePoisonedTileCacheIfNeeded(context: Context) {
    val marker = File(context.cacheDir, AppConstants.MapConstants.OSM_TILE_CACHE_BUST_MARKER)
    if (marker.exists()) return
    val dirs =
        buildList {
            add(context.filesDir)
            add(context.cacheDir)
            context.getExternalFilesDir(null)?.let { add(it) }
        }
    var failed = false
    for (dir in dirs) {
        val children = dir.listFiles() ?: continue
        for (child in children) {
            if (!isMapLibreCacheEntry(child.name)) continue
            if (!child.deleteRecursively()) {
                Log.e(TAG, "Failed to delete MapLibre cache ${child.absolutePath}")
                failed = true
            }
        }
    }
    if (failed) return
    try {
        marker.parentFile?.mkdirs()
        if (!marker.createNewFile() && !marker.exists()) {
            Log.e(TAG, "Failed to write OSM tile cache-bust marker")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to write OSM tile cache-bust marker", e)
    }
}

internal fun overwriteMapLibreHttp(
    client: Call.Factory,
    userAgent: String,
) {
    try {
        val clazz = Class.forName("org.maplibre.android.module.http.HttpRequestImpl")
        setStaticField(clazz, "client", client)
        setStaticField(clazz, "userAgentString", userAgent)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to overwrite MapLibre HTTP User-Agent", e)
    }
}

private fun setStaticField(
    clazz: Class<*>,
    name: String,
    value: Any,
) {
    val field = clazz.getDeclaredField(name)
    field.isAccessible = true
    field.set(null, value)
}

private const val TAG = "MapTileHttp"
