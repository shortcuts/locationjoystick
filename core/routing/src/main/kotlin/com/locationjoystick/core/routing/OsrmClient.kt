package com.locationjoystick.core.routing

import android.content.Context
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.AppJson
import com.locationjoystick.core.common.util.TtlLruCache
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.common.util.interpolatePosition
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * Like [kotlin.runCatching], but rethrows [kotlinx.coroutines.CancellationException] instead of
 * wrapping it into a [Result.failure] — swallowing it here would let a coroutine cancelled by
 * [kotlinx.coroutines.withTimeoutOrNull] (e.g. the bisection budget) keep running instead of
 * unwinding, defeating the cancellation this call site relies on.
 */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

private const val TAG = "OsrmClient"
private const val NO_SEGMENT = "NoSegment"
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
private const val HTTP_UNAVAILABLE = 503

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

/**
 * Thrown when the OSRM HTTP response is non-2xx, carrying the status code for callers to branch on.
 * [retryAfterMs] is populated from a numeric `Retry-After` header on a 429 response, if present.
 */
class OsrmHttpException(
    val httpCode: Int,
    message: String,
    retryAfterMs: Long? = null,
) : Exception(message) {
    val reason: OsrmFailureReason =
        if (httpCode == HTTP_TOO_MANY_REQUESTS) {
            OsrmFailureReason.RateLimited(retryAfterMs)
        } else {
            OsrmFailureReason.ServerError(httpCode)
        }
}

/** Classified reason an OSRM request failed, used to build accurate user-facing messages and retry decisions. */
sealed class OsrmFailureReason {
    object Timeout : OsrmFailureReason()

    data class ServerError(
        val code: Int,
    ) : OsrmFailureReason()

    /** HTTP 429. [retryAfterMs] is the server-requested wait, if it sent a numeric `Retry-After` header. */
    data class RateLimited(
        val retryAfterMs: Long?,
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
fun osrmFailureMessage(
    context: Context,
    reason: OsrmFailureReason,
): String =
    when (reason) {
        is OsrmFailureReason.Timeout -> context.getString(R.string.osrm_failure_timeout)
        is OsrmFailureReason.ServerError -> context.getString(R.string.osrm_failure_server_error)
        is OsrmFailureReason.RateLimited -> context.getString(R.string.osrm_failure_rate_limited)
        is OsrmFailureReason.NoRouteFound -> context.getString(R.string.osrm_failure_no_route_found)
        is OsrmFailureReason.NetworkUnavailable -> context.getString(R.string.osrm_failure_network_unavailable)
        is OsrmFailureReason.Unknown -> context.getString(R.string.osrm_failure_unknown)
    }

/** Parses a numeric (seconds-form) `Retry-After` header into milliseconds; null if absent or an HTTP-date. */
private fun parseRetryAfterMs(response: Response): Long? = response.header("Retry-After")?.toLongOrNull()?.times(1_000L)

/** Profile escalation order: prefer walking routes, fall back to bike then driving routing. */
private val PROFILE_ESCALATION =
    listOf(
        AppConstants.RoamingConstants.OSRM_PROFILE_FOOT,
        AppConstants.RoamingConstants.OSRM_PROFILE_BIKE,
        AppConstants.RoamingConstants.OSRM_PROFILE_DRIVING,
    )

/**
 * One OSRM-compatible routing server. [singleGraph] marks servers that serve the same graph for
 * every profile (the demo server) — a non-transient failure there is final for all profiles, so
 * the ladder skips that server's remaining slots.
 */
internal class OsrmBackend(
    val singleGraph: Boolean,
    private val resolveBase: (String) -> String,
) {
    fun baseUrlFor(profile: String): String = resolveBase(profile)
}

/** FOSSGIS community OSRM instances — separate real foot/bike/car graphs, one per URL path. */
internal val FossgisBackend =
    OsrmBackend(singleGraph = false) { profile ->
        val graph =
            when (profile) {
                AppConstants.RoamingConstants.OSRM_PROFILE_BIKE -> "bike"
                AppConstants.RoamingConstants.OSRM_PROFILE_DRIVING -> "car"
                else -> "foot"
            }
        "${AppConstants.OsrmConstants.FOSSGIS_BASE_URL}/routed-$graph"
    }

/** OSRM demo server — no SLA, single car-ish graph regardless of the profile in the URL. */
internal val DemoBackend =
    OsrmBackend(singleGraph = true) {
        AppConstants.OsrmConstants.BASE_URL.trimEnd('/')
    }

private fun isRetryable(reason: OsrmFailureReason): Boolean =
    reason is OsrmFailureReason.Timeout ||
        reason is OsrmFailureReason.ServerError ||
        reason is OsrmFailureReason.NetworkUnavailable ||
        reason is OsrmFailureReason.RateLimited

/**
 * Suspends until [Call] completes, parsing the response via [parse] (which runs on OkHttp's own
 * callback thread, inside the response body's lifetime — this is what lets cancellation interrupt
 * a slow body read, not just the connect phase). Cancelling the calling coroutine cancels the
 * underlying OkHttp call. Unlike [Call.execute], this respects coroutine cancellation and routes
 * through the OkHttp dispatcher's per-host concurrency cap.
 */
private suspend fun <T> Call.await(parse: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (!continuation.isActive) return
                    runCatching { response.use(parse) }
                        .fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) },
                        )
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            },
        )
        continuation.invokeOnCancellation {
            cancel()
        }
    }

/**
 * HTTP client for OSRM routing API using OkHttp and kotlinx.serialization.
 *
 * Resolves routes via a slot ladder spanning two public servers (FOSSGIS, then the OSRM demo
 * server) and three profiles (foot → bike → driving), bounded by a hard time budget.
 * Falls back to straight-line routes when the whole ladder fails.
 *
 * @see AppConstants.OsrmConstants for base URLs, backoffs, and budgets
 * @see AppConstants.RoamingConstants for profile constants (foot/bike/driving)
 */
@Singleton
class OsrmClient
    internal constructor(
        private val backends: List<OsrmBackend>,
        private val cooldowns: BackendCooldowns? = null,
        nowMs: () -> Long = System::currentTimeMillis,
    ) {
        companion object {
            const val PROFILE_FOOT = AppConstants.RoamingConstants.OSRM_PROFILE_FOOT
        }

        @Inject
        constructor() : this(listOf(FossgisBackend, DemoBackend))

        internal constructor(baseUrl: String) : this(
            listOf(OsrmBackend(singleGraph = false) { baseUrl.trimEnd('/') }),
        )

        internal constructor(cooldowns: BackendCooldowns) : this(listOf(FossgisBackend, DemoBackend), cooldowns)

        /** Successful ladder results only — never a bisected or straight-line-patched route. Not persisted. */
        private val routeCache =
            TtlLruCache<String, OsrmRoute>(
                AppConstants.OsrmConstants.CACHE_MAX_ENTRIES,
                AppConstants.OsrmConstants.CACHE_TTL_MS,
                nowMs,
            )

        private val okHttpClient: OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(AppConstants.OsrmConstants.ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(AppConstants.OsrmConstants.ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(AppConstants.OsrmConstants.ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
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
         * Resolves a route via [runLadder] under the hard
         * [AppConstants.OsrmConstants.TOTAL_TIME_BUDGET_MS] budget (enforced by cancellation — a
         * polled elapsed-time check could not bound an in-flight call or a long `Retry-After` wait).
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
                val scale = AppConstants.OsrmConstants.CACHE_COORD_SCALE
                val cacheKey =
                    profile + "|" +
                        waypoints.joinToString(";") { "${Math.round(it.latitude * scale)},${Math.round(it.longitude * scale)}" }
                routeCache.getFresh(cacheKey)?.let { return@withContext Result.success(it) }
                val ladderResult =
                    withTimeoutOrNull(AppConstants.OsrmConstants.TOTAL_TIME_BUDGET_MS) {
                        runLadder(profile, waypoints)
                    } ?: Result.failure(SocketTimeoutException("OSRM total time budget exceeded"))
                ladderResult.onSuccess { routeCache.put(cacheKey, it) }
                if (ladderResult.isFailure) {
                    routeCache.getStale(cacheKey)?.let { return@withContext Result.success(it) }
                }
                ladderResult
                    .recoverCatching { e ->
                        bisectIfEligible(profile, waypoints, e)
                    }.onFailure { e ->
                        Log.e(TAG, "OSRM route request failed — will fall back to straight-line", e)
                    }
            }

        private data class LadderSlot(
            val backend: OsrmBackend,
            val profile: String,
        )

        /**
         * One slot per (backend, profile) pair: every profile from [profile] onward in
         * [PROFILE_ESCALATION] on the first backend, then the same profiles on the next.
         */
        private fun ladderSlots(profile: String): List<LadderSlot> {
            val escalation = PROFILE_ESCALATION.dropWhile { it != profile }.ifEmpty { listOf(profile) }
            return backends
                .flatMap { backend -> escalation.map { LadderSlot(backend, it) } }
                .filterNot { isCoolingDown(it) }
        }

        private fun isCoolingDown(slot: LadderSlot): Boolean = cooldowns?.isCoolingDown(slot.backend.baseUrlFor(slot.profile)) == true

        /**
         * Walks [ladderSlots] until one succeeds. Any failure — transient or NoRoute — advances to
         * the next slot after a [backoffFor] wait: consecutive slots differ in profile (covers
         * NoRoute) and eventually in backend (covers outages). Special cases:
         * - First [NO_SEGMENT] failure snaps waypoints to the nearest road and retries the same slot.
         * - A non-transient failure on a [OsrmBackend.singleGraph] backend skips that backend's
         *   remaining slots — the same graph cannot answer differently for another profile.
         */
        private suspend fun runLadder(
            profile: String,
            waypoints: List<LatLng>,
        ): Result<OsrmRoute> {
            val slots = ladderSlots(profile)
            if (slots.isEmpty()) return Result.failure(OsrmHttpException(HTTP_UNAVAILABLE, "OSRM backends cooling down"))
            var points = waypoints
            var snappedToRoad = false
            var lastError: Throwable? = null
            var index = 0
            var waits = 0
            while (index < slots.size) {
                val slot = slots[index]
                val result = requestRoute(slot.backend, slot.profile, points)
                val error = result.exceptionOrNull() ?: return result
                lastError = error
                if (!snappedToRoad && error is OsrmRouteException && error.code == NO_SEGMENT) {
                    Log.w(TAG, "OSRM route failed (NoSegment), snapping waypoints to nearest road", error)
                    snappedToRoad = true
                    points = snapToRoad(slot.profile, points)
                    continue
                }
                val reason = classifyOsrmFailure(error)
                index++
                if (slot.backend.singleGraph && !isRetryable(reason)) {
                    while (index < slots.size && slots[index].backend === slot.backend) index++
                }
                // A failure may have just cooled the next slot's backend: skip it rather than wait for nothing.
                while (index < slots.size && isCoolingDown(slots[index])) index++
                if (index < slots.size) {
                    delay(backoffFor(reason, waits))
                    waits++
                }
            }
            return Result.failure(lastError ?: IllegalStateException("OSRM ladder had no slots"))
        }

        /** Backoff before the slot following failure number [waits] (0-indexed) with classified [reason]. */
        private fun backoffFor(
            reason: OsrmFailureReason,
            waits: Int,
        ): Long =
            if (reason is OsrmFailureReason.RateLimited) {
                reason.retryAfterMs ?: AppConstants.OsrmConstants.RATE_LIMIT_BACKOFF_MS
            } else {
                val backoffs = AppConstants.OsrmConstants.LADDER_BACKOFF_MS
                val base = backoffs[waits.coerceAtMost(backoffs.size - 1)]
                val jitter = AppConstants.OsrmConstants.RETRY_JITTER_MS
                base + Random.nextLong(-jitter, jitter)
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
         * Resolves one bisection leg: one attempt per backend (no waits — the whole bisection runs
         * under its own time budget), recursively splitting on failure up to
         * [AppConstants.OsrmConstants.BISECTION_MAX_DEPTH]. No per-leaf retries — with up to 2^5
         * leaves, retrying each would multiply request volume against shared, no-SLA servers.
         */
        private suspend fun bisectLeg(
            profile: String,
            from: LatLng,
            to: LatLng,
            depth: Int,
        ): OsrmRoute =
            requestRouteAnyBackend(profile, listOf(from, to)).getOrElse { e ->
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

        /** Tries [requestRoute] on each backend in order, returning the first success or the last failure. */
        private suspend fun requestRouteAnyBackend(
            profile: String,
            waypoints: List<LatLng>,
        ): Result<OsrmRoute> {
            var result: Result<OsrmRoute> = Result.failure(IllegalStateException("No routing backends configured"))
            for (backend in backends) {
                result = requestRoute(backend, profile, waypoints)
                if (result.isSuccess) return result
            }
            return result
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
         * Snaps each waypoint to its nearest road node via the primary backend's nearest service.
         * A waypoint that fails to snap is passed through unchanged.
         */
        private suspend fun snapToRoad(
            profile: String,
            waypoints: List<LatLng>,
        ): List<LatLng> =
            if (cooldowns?.isCoolingDown(backends.first().baseUrlFor(profile)) == true) {
                waypoints
            } else {
                waypoints.map { point ->
                    runCatchingCancellable {
                        val coordinate = "${point.longitude},${point.latitude}"
                        val url = "${backends.first().baseUrlFor(profile)}/nearest/v1/$profile/$coordinate"
                        val request =
                            okhttp3.Request
                                .Builder()
                                .url(url)
                                .build()
                        okHttpClient.newCall(request).await { response ->
                            val body = response.body?.string() ?: error("OSRM nearest response body is null")
                            val parsed = AppJson.decodeFromString<OsrmNearestResponse>(body)
                            if (!response.isSuccessful || parsed.code != "Ok") error("OSRM nearest returned ${parsed.code}")
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
            }

        private suspend fun requestRoute(
            backend: OsrmBackend,
            profile: String,
            waypoints: List<LatLng>,
        ): Result<OsrmRoute> =
            runCatchingCancellable {
                require(waypoints.size >= 2) { "At least 2 waypoints required" }

                val baseUrl = backend.baseUrlFor(profile)
                if (cooldowns?.isCoolingDown(baseUrl) == true) {
                    throw OsrmHttpException(HTTP_UNAVAILABLE, "OSRM backend cooling down")
                }
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
                okHttpClient.newCall(request).await { response ->
                    if (!response.isSuccessful) {
                        val retryAfterMs = if (response.code == HTTP_TOO_MANY_REQUESTS) parseRetryAfterMs(response) else null
                        if (response.code == HTTP_TOO_MANY_REQUESTS) {
                            cooldowns?.recordRateLimited(baseUrl, retryAfterMs)
                        } else if (response.code >= HTTP_SERVER_ERROR) {
                            cooldowns?.recordServerError(baseUrl)
                        }
                        throw OsrmHttpException(response.code, "OSRM HTTP ${response.code}: ${response.message}", retryAfterMs)
                    }

                    val body =
                        response.body?.string()
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
