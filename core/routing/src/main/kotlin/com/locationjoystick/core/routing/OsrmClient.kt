package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.AppJson
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.common.util.interpolatePosition
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OsrmClient"
private const val NO_SEGMENT = "NoSegment"

// ---------------------------------------------------------------------------
// Response data classes (kotlinx.serialization)
// ---------------------------------------------------------------------------

/** OSRM API response wrapper. */
@Serializable
data class OsrmRouteResponse(
    val code: String,
    val routes: List<OsrmRoute>?,
)

/** Single route from OSRM response. */
@Serializable
data class OsrmRoute(
    val geometry: OsrmGeometry,
    val distance: Double,
    val duration: Double,
)

/** Route geometry containing coordinate list. */
@Serializable
data class OsrmGeometry(
    val coordinates: List<List<Double>>,
    val type: String,
)

/** Helper class for coordinate parsing. */
@Serializable
data class OsrmCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/** Route result including waypoints and total road distance. */
data class OsrmRouteResult(
    val waypoints: List<LatLng>,
    val distanceMeters: Double,
)

/** OSRM nearest-service response wrapper. */
@Serializable
data class OsrmNearestResponse(
    val code: String,
    val waypoints: List<OsrmNearestWaypoint>?,
)

/** Single snapped point from OSRM nearest service. */
@Serializable
data class OsrmNearestWaypoint(
    val location: List<Double>,
)

/** Thrown when OSRM returns a non-"Ok" status code, carrying that code for callers to branch on. */
class OsrmRouteException(
    val code: String,
    message: String,
) : Exception(message) {
    val reason: OsrmFailureReason =
        if (code == NO_SEGMENT) OsrmFailureReason.Unknown else OsrmFailureReason.NoRouteFound
}

/** Thrown when the OSRM HTTP response is non-2xx, carrying the status code for callers to branch on. */
class OsrmHttpException(
    val httpCode: Int,
    message: String,
) : Exception(message) {
    val reason: OsrmFailureReason = OsrmFailureReason.ServerError(httpCode)
}

/** Classified reason an OSRM request failed, used to build accurate user-facing messages and retry decisions. */
sealed class OsrmFailureReason {
    object Timeout : OsrmFailureReason()

    data class ServerError(
        val code: Int,
    ) : OsrmFailureReason()

    object NoRouteFound : OsrmFailureReason()

    object NetworkUnavailable : OsrmFailureReason()

    object Unknown : OsrmFailureReason()
}

/** Classifies a failure from an OSRM request by exception type/field — never by parsing message strings. */
fun classifyOsrmFailure(e: Throwable): OsrmFailureReason =
    when (e) {
        is SocketTimeoutException -> OsrmFailureReason.Timeout
        is OsrmHttpException -> e.reason
        is OsrmRouteException -> e.reason
        is UnknownHostException -> OsrmFailureReason.NetworkUnavailable
        is IOException -> OsrmFailureReason.NetworkUnavailable
        else -> OsrmFailureReason.Unknown
    }

/** Short, user-facing description of [reason] (no trailing punctuation — callers append their own context). */
fun osrmFailureMessage(reason: OsrmFailureReason): String =
    when (reason) {
        is OsrmFailureReason.Timeout -> "Routing server timed out"
        is OsrmFailureReason.ServerError -> "Routing server unavailable"
        is OsrmFailureReason.NoRouteFound -> "No road route found"
        is OsrmFailureReason.NetworkUnavailable -> "No network connection"
        is OsrmFailureReason.Unknown -> "Road routing unavailable"
    }

private fun isRetryable(reason: OsrmFailureReason): Boolean =
    reason is OsrmFailureReason.Timeout || reason is OsrmFailureReason.ServerError || reason is OsrmFailureReason.NetworkUnavailable

/**
 * HTTP client for OSRM routing API using OkHttp and kotlinx.serialization.
 *
 * Provides road-following routes between two points using OSRM public demo server.
 * Falls back to straight-line routes on network failure.
 *
 * @see AppConstants.OsrmConstants for base URL and configuration
 * @see AppConstants.RoamingConstants for profile constants (foot/driving)
 */
@Singleton
class OsrmClient
    internal constructor(
        baseUrl: String,
    ) {
        companion object {
            const val PROFILE_FOOT = AppConstants.RoamingConstants.OSRM_PROFILE_FOOT
        }

        @Inject
        constructor() : this(AppConstants.OsrmConstants.BASE_URL)

        private val baseUrl: String = baseUrl
        private val okHttpClient: OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()

        suspend fun getRoute(
            profile: String,
            waypoints: List<LatLng>,
        ): Result<List<LatLng>> =
            fetchRoute(profile, waypoints).map { route ->
                route.geometry.coordinates.mapNotNull { coord ->
                    // GeoJSON coordinates are [longitude, latitude]; guard against malformed entries.
                    if (coord.size < 2) null else LatLng(latitude = coord[1], longitude = coord[0])
                }
            }

        suspend fun getRouteWithDistance(
            profile: String,
            waypoints: List<LatLng>,
        ): Result<OsrmRouteResult> =
            fetchRoute(profile, waypoints).map { route ->
                val routeWaypoints =
                    route.geometry.coordinates.mapNotNull { coord ->
                        if (coord.size < 2) null else LatLng(latitude = coord[1], longitude = coord[0])
                    }
                OsrmRouteResult(waypoints = routeWaypoints, distanceMeters = route.distance)
            }

        /**
         * Requests a route for [profile]. Transient failures (timeout/5xx/network) are retried up to
         * [AppConstants.OsrmConstants.RETRY_COUNT] times before any of the fallback chains below run.
         *
         * If the failure is [NO_SEGMENT] (a waypoint is too far from any road), snaps every waypoint
         * to its nearest road node and retries once before falling back further. If [profile] is
         * [PROFILE_FOOT] and the request still fails, retries once with
         * [AppConstants.RoamingConstants.OSRM_PROFILE_DRIVING] — the app should always prefer walking
         * directions, falling back to driving only when walking routing is unavailable (e.g. a
         * self-hosted OSRM instance without a foot profile graph).
         *
         * As a last resort for a single long A→B leg (exactly 2 waypoints, beyond
         * [AppConstants.OsrmConstants.BISECTION_MIN_DISTANCE_METERS]), bisects the leg and resolves
         * each half independently — see [bisectLeg].
         */
        private suspend fun fetchRoute(
            profile: String,
            waypoints: List<LatLng>,
        ): Result<OsrmRoute> =
            withContext(Dispatchers.IO) {
                requestRouteWithRetry(profile, waypoints)
                    .recoverCatching { e ->
                        if (e is OsrmRouteException && e.code == NO_SEGMENT) {
                            Log.w(TAG, "OSRM route failed (NoSegment), snapping waypoints to nearest road", e)
                            val snapped = snapToRoad(profile, waypoints)
                            requestRouteWithRetry(profile, snapped).getOrThrow()
                        } else {
                            throw e
                        }
                    }.recoverCatching { e ->
                        if (profile != PROFILE_FOOT) throw e
                        Log.w(TAG, "OSRM foot route failed, retrying with driving profile", e)
                        requestRouteWithRetry(AppConstants.RoamingConstants.OSRM_PROFILE_DRIVING, waypoints).getOrThrow()
                    }.recoverCatching { e ->
                        bisectIfEligible(profile, waypoints, e)
                    }.onFailure { e ->
                        Log.e(TAG, "OSRM route request failed — will fall back to straight-line", e)
                    }
            }

        /**
         * Retries [requestRoute] up to [maxRetries] times with a fixed backoff
         * ([AppConstants.OsrmConstants.RETRY_BACKOFF_MS]), but only for retryable reasons
         * ([isRetryable]) — a genuinely-no-route response is never retried.
         */
        private suspend fun requestRouteWithRetry(
            profile: String,
            waypoints: List<LatLng>,
            maxRetries: Int = AppConstants.OsrmConstants.RETRY_COUNT,
        ): Result<OsrmRoute> {
            var result = requestRoute(profile, waypoints)
            var attempt = 0
            while (attempt < maxRetries) {
                val reason = classifyOsrmFailure(result.exceptionOrNull() ?: return result)
                if (!isRetryable(reason)) break
                delay(AppConstants.OsrmConstants.RETRY_BACKOFF_MS[attempt])
                attempt++
                result = requestRoute(profile, waypoints)
            }
            return result
        }

        /**
         * Bisects a failed single-leg ([waypoints].size == 2) request beyond
         * [AppConstants.OsrmConstants.BISECTION_MIN_DISTANCE_METERS], otherwise rethrows [cause].
         * The whole operation is bounded by [AppConstants.OsrmConstants.BISECTION_TIME_BUDGET_MS] via
         * cancellation — a polled elapsed-time check cannot bound a single in-flight call that blocks
         * for up to the underlying callTimeout.
         */
        private suspend fun bisectIfEligible(
            profile: String,
            waypoints: List<LatLng>,
            cause: Throwable,
        ): OsrmRoute {
            if (waypoints.size != 2) throw cause
            val (from, to) = waypoints[0] to waypoints[1]
            if (haversineDistance(from, to) <= AppConstants.OsrmConstants.BISECTION_MIN_DISTANCE_METERS) throw cause
            Log.w(TAG, "OSRM route failed past bisection distance threshold, bisecting", cause)
            val mid = interpolatePosition(from, to, 0.5)
            val result =
                withTimeoutOrNull(AppConstants.OsrmConstants.BISECTION_TIME_BUDGET_MS) {
                    coroutineScope {
                        val left = async { bisectLeg(profile, from, mid, depth = 1) }
                        val right = async { bisectLeg(profile, mid, to, depth = 1) }
                        combineRoutes(left.await(), right.await())
                    }
                }
            return result ?: throw cause
        }

        /**
         * Resolves one bisection leg: a single one-shot/retried request, recursively splitting on
         * failure up to [AppConstants.OsrmConstants.BISECTION_MAX_DEPTH]. Leaves beyond
         * [AppConstants.OsrmConstants.BISECTION_RETRY_DEPTH_CUTOFF] skip retry to keep total request
         * volume bounded — depth 5 with retries at every level could reach ~96 HTTP calls for one tap
         * against a shared, no-SLA demo server.
         */
        private suspend fun bisectLeg(
            profile: String,
            from: LatLng,
            to: LatLng,
            depth: Int,
        ): OsrmRoute {
            val retries =
                if (depth <=
                    AppConstants.OsrmConstants.BISECTION_RETRY_DEPTH_CUTOFF
                ) {
                    AppConstants.OsrmConstants.RETRY_COUNT
                } else {
                    0
                }
            return requestRouteWithRetry(profile, listOf(from, to), retries).getOrElse { e ->
                if (depth >= AppConstants.OsrmConstants.BISECTION_MAX_DEPTH) {
                    Log.w(TAG, "Bisection leg failed at max depth, using straight-line fallback", e)
                    straightLineSubRoute(from, to)
                } else {
                    val mid = interpolatePosition(from, to, 0.5)
                    coroutineScope {
                        val left = async { bisectLeg(profile, from, mid, depth + 1) }
                        val right = async { bisectLeg(profile, mid, to, depth + 1) }
                        combineRoutes(left.await(), right.await())
                    }
                }
            }
        }

        private fun straightLineSubRoute(
            from: LatLng,
            to: LatLng,
        ): OsrmRoute =
            OsrmRoute(
                geometry =
                    OsrmGeometry(
                        coordinates = listOf(listOf(from.longitude, from.latitude), listOf(to.longitude, to.latitude)),
                        type = "LineString",
                    ),
                distance = haversineDistance(from, to),
                duration = 0.0,
            )

        private fun combineRoutes(
            a: OsrmRoute,
            b: OsrmRoute,
        ): OsrmRoute =
            OsrmRoute(
                geometry =
                    OsrmGeometry(
                        coordinates = a.geometry.coordinates + b.geometry.coordinates.drop(1),
                        type = "LineString",
                    ),
                distance = a.distance + b.distance,
                duration = a.duration + b.duration,
            )

        /**
         * Snaps each waypoint to its nearest road node via the OSRM nearest service.
         * A waypoint that fails to snap is passed through unchanged.
         */
        private suspend fun snapToRoad(
            profile: String,
            waypoints: List<LatLng>,
        ): List<LatLng> =
            waypoints.map { point ->
                runCatching {
                    val coordinate = "${point.longitude},${point.latitude}"
                    val url = "$baseUrl/nearest/v1/$profile/$coordinate"
                    val request =
                        okhttp3.Request
                            .Builder()
                            .url(url)
                            .build()
                    val response = okHttpClient.newCall(request).execute()
                    response.use {
                        val body = it.body?.string() ?: error("OSRM nearest response body is null")
                        val parsed = AppJson.decodeFromString<OsrmNearestResponse>(body)
                        if (!it.isSuccessful || parsed.code != "Ok") error("OSRM nearest returned ${parsed.code}")
                        val location =
                            parsed.waypoints?.firstOrNull()?.location
                                ?: error("OSRM nearest returned no waypoints")
                        LatLng(latitude = location[1], longitude = location[0])
                    }
                }.getOrElse { e ->
                    Log.w(TAG, "OSRM nearest snap failed for $point, using original point", e)
                    point
                }
            }

        private suspend fun requestRoute(
            profile: String,
            waypoints: List<LatLng>,
        ): Result<OsrmRoute> =
            runCatching {
                require(waypoints.size >= 2) { "At least 2 waypoints required" }

                val coordinates = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
                val overview = AppConstants.OsrmConstants.OVERVIEW
                val geometries = AppConstants.OsrmConstants.GEOMETRIES
                val url =
                    "$baseUrl/route/v1/$profile/$coordinates?overview=$overview&geometries=$geometries"
                val request =
                    okhttp3.Request
                        .Builder()
                        .url(url)
                        .build()
                val response = okHttpClient.newCall(request).execute()

                response.use {
                    if (!it.isSuccessful) {
                        throw OsrmHttpException(it.code, "OSRM HTTP ${it.code}: ${it.message}")
                    }

                    val body =
                        it.body?.string()
                            ?: error("OSRM response body is null")

                    val parsed = AppJson.decodeFromString<OsrmRouteResponse>(body)

                    if (parsed.code != "Ok") {
                        throw OsrmRouteException(parsed.code, "OSRM returned non-Ok code: ${parsed.code}")
                    }

                    parsed.routes?.firstOrNull()
                        ?: error("OSRM returned no routes")
                }
            }

        /**
         * Returns a route from [from] to [to].
         * If [followRoads] is false or OSRM fails, falls back to a straight-line two-point route,
         * calling [onFallback] with the classified failure reason in the latter case.
         */
        suspend fun resolveRoute(
            profile: String,
            from: LatLng,
            to: LatLng,
            followRoads: Boolean,
            onFallback: (OsrmFailureReason) -> Unit = {},
        ): List<LatLng> {
            if (!followRoads) return straightLineRoute(from, to)
            return getRoute(profile, listOf(from, to)).getOrElse { e ->
                onFallback(classifyOsrmFailure(e))
                straightLineRoute(from, to)
            }
        }

        fun straightLineRoute(
            from: LatLng,
            to: LatLng,
        ): List<LatLng> = listOf(from, to)
    }
