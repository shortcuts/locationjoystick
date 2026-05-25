package com.locationjoystick.feature.settings.impl

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.Waypoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Parses YAMLA export files. Three distinct formats are supported:
 *
 * 1. **Obfuscated settings** (`yamla*.json`): single JSON object with short keys.
 *    - `e` = walk speed (km/h), `f` = run speed (km/h), `g` = bike speed (km/h)
 *    - `w` = favorites array of `{name, lat, lon}` (decimal degrees floats)
 *    - Produces: favorites + speed profiles. Routes: empty.
 *
 * 2. **All-routes** (`all_routes.json`): top-level JSON array of route objects.
 *    - `[{name, points:[{latitude, longitude}]}]`
 *    - Coordinates are integer microdegrees (value / 1_000_000 = decimal degrees).
 *    - Produces: routes only. Favorites: empty.
 *
 * 3. **Favorites** (`favorites.json`): top-level JSON array of favorite objects.
 *    - `[{latLng:{latitude, longitude}, name}]`
 *    - Coordinates are integer microdegrees.
 *    - Produces: favorites only. Routes: empty.
 */
internal object YamlaMigrator {
    private const val TAG = "YamlaMigrator"
    private const val MICRODEGREE_DIVISOR = 1_000_000.0

    fun parse(json: String): Result<MigrationResult> =
        runCatching {
            if (json.isBlank()) {
                return Result.failure(IllegalArgumentException("YAMLA JSON is empty or blank"))
            }

            val trimmed = json.trim()
            when {
                trimmed.startsWith("{") -> parseObfuscatedSettings(JSONObject(trimmed))
                trimmed.startsWith("[") -> parseArrayFormat(JSONArray(trimmed))
                else -> return Result.failure(IllegalArgumentException("Unrecognised YAMLA JSON format"))
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to parse YAMLA JSON", e)
        }

    // -------------------------------------------------------------------------
    // Format 1: obfuscated settings object
    // -------------------------------------------------------------------------

    private fun parseObfuscatedSettings(root: JSONObject): MigrationResult {
        val walkSpeed = parseSpeedOrNull(root, "e")
        val runSpeed = parseSpeedOrNull(root, "f")
        val bikeSpeed = parseSpeedOrNull(root, "g")
        val favorites = parseObfuscatedFavorites(root)
        return MigrationResult(
            favorites = favorites,
            routes = emptyList(),
            walkSpeed = walkSpeed,
            runSpeed = runSpeed,
            bikeSpeed = bikeSpeed,
        )
    }

    /** Reads a speed in km/h from [key], converts to m/s, and clamps to valid range. */
    private fun parseSpeedOrNull(
        root: JSONObject,
        key: String,
    ): Double? {
        if (!root.has(key)) return null
        val kmh = root.getDouble(key)
        val ms = kmh / 3.6
        return ms.coerceIn(
            AppConstants.ProfileConstants.MIN_SPEED_MS,
            AppConstants.ProfileConstants.MAX_SPEED_MS,
        )
    }

    private fun parseObfuscatedFavorites(root: JSONObject): List<FavoriteLocation> {
        if (!root.has("w")) return emptyList()
        val array = root.getJSONArray("w")
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            FavoriteLocation(
                id = UUID.randomUUID().toString(),
                name = item.getString("name"),
                position =
                    LatLng(
                        latitude = item.getDouble("lat"),
                        longitude = item.getDouble("lon"),
                    ),
                createdAt = System.currentTimeMillis(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Format 2 & 3: plain array formats
    // -------------------------------------------------------------------------

    private fun parseArrayFormat(array: JSONArray): MigrationResult {
        if (array.length() == 0) {
            return MigrationResult()
        }
        val first = array.getJSONObject(0)
        return when {
            first.has("points") -> parseAllRoutes(array)
            first.has("latLng") -> parseFavoritesArray(array)
            else -> throw IllegalArgumentException("Unrecognised YAMLA array element format")
        }
    }

    private fun parseAllRoutes(array: JSONArray): MigrationResult {
        val routes =
            (0 until array.length()).map { i ->
                val routeObj = array.getJSONObject(i)
                val name = routeObj.getString("name")
                val points = routeObj.getJSONArray("points")
                val waypoints =
                    (0 until points.length()).map { j ->
                        val pt = points.getJSONObject(j)
                        Waypoint(
                            id = UUID.randomUUID().toString(),
                            position =
                                LatLng(
                                    latitude = pt.getLong("latitude") / MICRODEGREE_DIVISOR,
                                    longitude = pt.getLong("longitude") / MICRODEGREE_DIVISOR,
                                ),
                            orderIndex = j,
                        )
                    }
                Route(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    waypoints = waypoints,
                    routeType = RouteType.STRAIGHT,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        return MigrationResult(routes = routes)
    }

    private fun parseFavoritesArray(array: JSONArray): MigrationResult {
        val favorites =
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val latLng = item.getJSONObject("latLng")
                FavoriteLocation(
                    id = UUID.randomUUID().toString(),
                    name = item.getString("name"),
                    position =
                        LatLng(
                            latitude = latLng.getLong("latitude") / MICRODEGREE_DIVISOR,
                            longitude = latLng.getLong("longitude") / MICRODEGREE_DIVISOR,
                        ),
                    createdAt = System.currentTimeMillis(),
                )
            }
        return MigrationResult(favorites = favorites)
    }
}
