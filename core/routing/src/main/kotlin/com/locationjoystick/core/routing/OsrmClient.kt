package com.locationjoystick.core.routing

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OsrmClient"

/**
 * Retrofit API interface for OSRM (Open Source Routing Machine) HTTP API.
 *
 * Used for road-following routes in roaming mode and guided routes.
 * See [AppConstants.OsrmConstants] for base URL and other constants.
 */
internal interface OsrmApi {
    @GET("route/v1/{profile}/{coordinates}")
    suspend fun getRoute(
        @Path("profile") profile: String,
        @Path(value = "coordinates", encoded = true) coordinates: String,
        @Query("overview") overview: String = AppConstants.OsrmConstants.OVERVIEW,
        @Query("geometries") geometries: String = AppConstants.OsrmConstants.GEOMETRIES,
    ): Response<OsrmRouteResponse>
}

// ---------------------------------------------------------------------------
// Response data classes (Gson-mapped)
// ---------------------------------------------------------------------------

/** OSRM API response wrapper. */
data class OsrmRouteResponse(
    val code: String,
    val routes: List<OsrmRoute>?,
)

/** Single route from OSRM response. */
data class OsrmRoute(
    val geometry: OsrmGeometry,
    val distance: Double,
    val duration: Double,
)

/** Route geometry containing coordinate list. */
data class OsrmGeometry(
    val coordinates: List<List<Double>>,
    val type: String,
)

/** Helper class for coordinate parsing. */
data class OsrmCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/**
 * HTTP client for OSRM routing API.
 *
 * Provides road-following routes between two points using OSRM public demo server.
 * Falls back to straight-line routes on network failure.
 *
 * @see AppConstants.OsrmConstants for base URL and configuration
 * @see AppConstants.RoamingConstants for profile constants (foot/bike)
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

        private val api: OsrmApi =
            Retrofit
                .Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OsrmApi::class.java)

        suspend fun getRoute(
            profile: String,
            waypoints: List<LatLng>,
        ): Result<List<LatLng>> =
            withContext(Dispatchers.IO) {
                runCatching {
                    require(waypoints.size >= 2) { "At least 2 waypoints required" }

                    val coordinates = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
                    val response = api.getRoute(profile = profile, coordinates = coordinates)

                    if (!response.isSuccessful) {
                        error("OSRM HTTP ${response.code()}: ${response.message()}")
                    }

                    val body =
                        response.body()
                            ?: error("OSRM response body is null")

                    if (body.code != "Ok") {
                        error("OSRM returned non-Ok code: ${body.code}")
                    }

                    val route =
                        body.routes?.firstOrNull()
                            ?: error("OSRM returned no routes")

                    route.geometry.coordinates.mapNotNull { coord ->
                        // GeoJSON coordinates are [longitude, latitude]; guard against malformed entries.
                        if (coord.size < 2) null else LatLng(latitude = coord[1], longitude = coord[0])
                    }
                }.onFailure { e ->
                    Log.e(TAG, "OSRM route request failed — will fall back to straight-line", e)
                }
            }

        /**
         * Returns a route from [from] to [to].
         * If [followRoads] is false or OSRM fails, falls back to a straight-line two-point route.
         */
        suspend fun resolveRoute(
            profile: String,
            from: LatLng,
            to: LatLng,
            followRoads: Boolean,
        ): List<LatLng> {
            if (!followRoads) return straightLineRoute(from, to)
            return getRoute(profile, listOf(from, to)).getOrElse { straightLineRoute(from, to) }
        }

        fun straightLineRoute(
            from: LatLng,
            to: LatLng,
        ): List<LatLng> = listOf(from, to)
    }
