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

internal interface OsrmApi {
    @GET("route/v1/{profile}/{coordinates}")
    suspend fun getRoute(
        @Path("profile") profile: String,
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = AppConstants.OsrmConstants.OVERVIEW,
        @Query("geometries") geometries: String = AppConstants.OsrmConstants.GEOMETRIES,
    ): Response<OsrmRouteResponse>
}

// ---------------------------------------------------------------------------
// Response data classes (Gson-mapped)
// ---------------------------------------------------------------------------

data class OsrmRouteResponse(
    val code: String,
    val routes: List<OsrmRoute>?,
)

data class OsrmRoute(
    val geometry: OsrmGeometry,
    val distance: Double,
    val duration: Double,
)

data class OsrmGeometry(
    val coordinates: List<List<Double>>,
    val type: String,
)

data class OsrmCoordinate(
    val latitude: Double,
    val longitude: Double,
)

@Singleton
class OsrmClient
    @Inject
    constructor() {
        companion object {
            const val PROFILE_FOOT = AppConstants.RoamingConstants.OSRM_PROFILE_FOOT
        }

        private val api: OsrmApi =
            Retrofit
                .Builder()
                .baseUrl(AppConstants.OsrmConstants.BASE_URL)
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

                    route.geometry.coordinates.map { coord ->
                        // GeoJSON coordinates are [longitude, latitude]
                        LatLng(latitude = coord[1], longitude = coord[0])
                    }
                }.onFailure { e ->
                    Log.e(TAG, "OSRM route request failed — will fall back to straight-line", e)
                }
            }

        fun straightLineRoute(
            from: LatLng,
            to: LatLng,
        ): List<LatLng> = listOf(from, to)
    }
