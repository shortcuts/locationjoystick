package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.AppSettings
import com.locationjoystick.core.model.ExportData
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.model.WidgetFeature
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes and deserializes app data for export/import.
 *
 * Used by Settings screen for:
 * - JSON export via share intent
 * - QR code chunking via [QrChunker]
 * - JSON import from file picker
 *
 * Schema:
 * ```
 * {
 *   "schemaVersion": 1,
 *   "exportedAt": 1699999999999,
 *   "settings": { ... },
 *   "speedProfiles": [ ... ],
 *   "routes": [ ... ],
 *   "favoriteLocations": [ ... ],
 *   "jitterIdleRadius": 0.5,
 *   "jitterMovingRadius": 1.5,
 *   "jitterIntervalSeconds": 3
 * }
 * ```
 *
 * @see AppConstants.ExportConstants.SCHEMA_VERSION for current version
 * @see ExportData for the domain model
 */
internal object SettingsExportCodec {
    /**
     * Serializes export data to JSON string.
     *
     * @param data Complete export data to serialize
     * @return JSON string ready for sharing or chunking
     */
    fun serializeExportData(data: ExportData): String {
        val root = JSONObject()
        root.put("schemaVersion", data.schemaVersion)
        root.put("exportedAt", data.exportedAt)

        val settingsObj = JSONObject()
        settingsObj.put("speedUnit", data.settings.speedUnit.name)
        settingsObj.put("enabledWidgetFeatures", JSONArray(data.settings.enabledWidgetFeatures.map { it.name }))
        settingsObj.put("realismBearingHoldIdle", data.settings.bearingHoldOnIdle)
        settingsObj.put("realismAltitudeEnabled", data.settings.altitudeEnabled)
        settingsObj.put("realismWarmupEnabled", data.settings.warmupEnabled)
        settingsObj.put("realismSatelliteExtrasEnabled", data.settings.satelliteExtrasEnabled)
        settingsObj.put("realismSuspendedMockingEnabled", data.settings.suspendedMockingEnabled)
        root.put("settings", settingsObj)

        val profilesArray = JSONArray()
        data.speedProfiles.forEach { profile ->
            val obj = JSONObject()
            obj.put("id", profile.id)
            obj.put("name", profile.name)
            obj.put("speedMetersPerSecond", profile.speedMetersPerSecond)
            profilesArray.put(obj)
        }
        root.put("speedProfiles", profilesArray)

        val routesArray = JSONArray()
        data.routes.forEach { route ->
            val obj = JSONObject()
            obj.put("id", route.id)
            obj.put("name", route.name)
            obj.put("isLooping", route.isLooping)
            obj.put("routeType", route.routeType.name)
            obj.put("createdAt", route.createdAt)
            val wpArray = JSONArray()
            route.waypoints.forEach { wp ->
                val wpObj = JSONObject()
                wpObj.put("id", wp.id)
                wpObj.put("lat", wp.position.latitude)
                wpObj.put("lon", wp.position.longitude)
                wpObj.put("orderIndex", wp.orderIndex)
                wpArray.put(wpObj)
            }
            obj.put("waypoints", wpArray)
            routesArray.put(obj)
        }
        root.put("routes", routesArray)

        val favsArray = JSONArray()
        data.favoriteLocations.forEach { fav ->
            val obj = JSONObject()
            obj.put("id", fav.id)
            obj.put("name", fav.name)
            obj.put("lat", fav.position.latitude)
            obj.put("lon", fav.position.longitude)
            obj.put("createdAt", fav.createdAt)
            favsArray.put(obj)
        }
        root.put("favoriteLocations", favsArray)

        root.put("jitterIdleRadius", data.jitterIdleRadius)
        root.put("jitterMovingRadius", data.jitterMovingRadius)
        root.put("jitterIntervalSeconds", data.jitterIntervalSeconds)

        return root.toString()
    }

    fun parseExportData(json: String): ExportData {
        val root = JSONObject(json)
        val schemaVersion = root.optInt("schemaVersion", 1)
        if (schemaVersion != 1) throw IllegalArgumentException("Unsupported schema version: $schemaVersion")

        val settingsObj = root.optJSONObject("settings") ?: JSONObject()
        val speedUnit =
            try {
                SpeedUnit.valueOf(settingsObj.optString("speedUnit", "KMH"))
            } catch (e: IllegalArgumentException) {
                SpeedUnit.KMH
            }
        val enabledFeatures = mutableListOf<WidgetFeature>()
        settingsObj.optJSONArray("enabledWidgetFeatures")?.let { arr ->
            for (i in 0 until arr.length()) {
                try {
                    enabledFeatures.add(WidgetFeature.valueOf(arr.getString(i)))
                } catch (e: IllegalArgumentException) {
                    // skip unknown features
                }
            }
        }
        val bearingHoldOnIdle = settingsObj.optBoolean("realismBearingHoldIdle", AppConstants.RealismConstants.BEARING_HOLD_ON_IDLE_DEFAULT)
        val altitudeEnabled = settingsObj.optBoolean("realismAltitudeEnabled", AppConstants.RealismConstants.ALTITUDE_ENABLED_DEFAULT)
        val warmupEnabled = settingsObj.optBoolean("realismWarmupEnabled", AppConstants.RealismConstants.WARMUP_ENABLED_DEFAULT)
        val satelliteExtrasEnabled =
            settingsObj.optBoolean(
                "realismSatelliteExtrasEnabled",
                AppConstants.RealismConstants.SATELLITE_EXTRAS_ENABLED_DEFAULT,
            )
        val suspendedMockingEnabled =
            settingsObj.optBoolean(
                "realismSuspendedMockingEnabled",
                AppConstants.RealismConstants.SUSPENDED_MOCKING_ENABLED_DEFAULT,
            )
        val settings =
            AppSettings(
                speedUnit = speedUnit,
                enabledWidgetFeatures = enabledFeatures,
                bearingHoldOnIdle = bearingHoldOnIdle,
                altitudeEnabled = altitudeEnabled,
                warmupEnabled = warmupEnabled,
                satelliteExtrasEnabled = satelliteExtrasEnabled,
                suspendedMockingEnabled = suspendedMockingEnabled,
            )

        val speedProfiles = mutableListOf<SpeedProfile>()
        root.optJSONArray("speedProfiles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                speedProfiles.add(
                    SpeedProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        speedMetersPerSecond = obj.getDouble("speedMetersPerSecond"),
                    ),
                )
            }
        }

        val routes = mutableListOf<Route>()
        root.optJSONArray("routes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val routeType =
                    try {
                        RouteType.valueOf(obj.optString("routeType", RouteType.STRAIGHT.name))
                    } catch (e: IllegalArgumentException) {
                        RouteType.STRAIGHT
                    }
                val waypoints = mutableListOf<Waypoint>()
                obj.optJSONArray("waypoints")?.let { wpArr ->
                    for (j in 0 until wpArr.length()) {
                        val wpObj = wpArr.getJSONObject(j)
                        waypoints.add(
                            Waypoint(
                                id = wpObj.getString("id"),
                                position =
                                    LatLng(
                                        latitude = wpObj.getDouble("lat"),
                                        longitude = wpObj.getDouble("lon"),
                                    ),
                                orderIndex = wpObj.getInt("orderIndex"),
                            ),
                        )
                    }
                }
                routes.add(
                    Route(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        waypoints = waypoints,
                        isLooping = obj.optBoolean("isLooping", false),
                        routeType = routeType,
                        createdAt = obj.optLong("createdAt", 0),
                    ),
                )
            }
        }

        val favorites = mutableListOf<FavoriteLocation>()
        root.optJSONArray("favoriteLocations")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                favorites.add(
                    FavoriteLocation(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        position =
                            LatLng(
                                latitude = obj.getDouble("lat"),
                                longitude = obj.getDouble("lon"),
                            ),
                        createdAt = obj.optLong("createdAt", 0),
                    ),
                )
            }
        }

        return ExportData(
            schemaVersion = schemaVersion,
            exportedAt = root.optLong("exportedAt", 0),
            settings = settings,
            speedProfiles = speedProfiles,
            routes = routes,
            favoriteLocations = favorites,
            jitterIdleRadius = root.optDouble("jitterIdleRadius", 0.0),
            jitterMovingRadius = root.optDouble("jitterMovingRadius", 1.0),
            jitterIntervalSeconds = root.optInt("jitterIntervalSeconds", 3),
        )
    }
}
