package com.locationjoystick.feature.settings.impl

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import org.json.JSONObject
import java.util.UUID

/**
 * Parses a YAMLA settings JSON export (single-line obfuscated JSON).
 *
 * Extracts:
 * - Speed profiles from keys: e (walk km/h), f (run km/h), g (bike km/h)
 * - Favorites from key: w (array of {name, lat, lon})
 *
 * Routes are not present in YAMLA exports.
 */
internal object YamlaMigrator {
    private const val TAG = "YamlaMigrator"

    fun parse(json: String): Result<MigrationResult> =
        runCatching {
            if (json.isBlank()) {
                return Result.failure(IllegalArgumentException("YAMLA JSON is empty or blank"))
            }

            val root = JSONObject(json)

            val walkSpeed = parseSpeedOrNull(root, "e")
            val runSpeed = parseSpeedOrNull(root, "f")
            val bikeSpeed = parseSpeedOrNull(root, "g")

            val favorites = parseFavorites(root)

            MigrationResult(
                favorites = favorites,
                routes = emptyList(),
                walkSpeed = walkSpeed,
                runSpeed = runSpeed,
                bikeSpeed = bikeSpeed,
            )
        }.onFailure { e ->
            Log.e(TAG, "Failed to parse YAMLA JSON", e)
        }

    /**
     * Reads a speed in km/h from [key], converts to m/s, and clamps to valid range.
     * Returns null if the key is absent.
     */
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

    private fun parseFavorites(root: JSONObject): List<FavoriteLocation> {
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
}
