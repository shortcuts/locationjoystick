package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.locationjoystick.core.model.RouteStartConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface RouteStartPreferences {
    /** Options of the last route Start from a start sheet, or null when none or unreadable. */
    fun getRouteStartConfig(): Flow<RouteStartConfig?>

    /** Saves the sheet options. Planting and teleport-to-start are not stored. */
    suspend fun setRouteStartConfig(config: RouteStartConfig)
}

internal class RouteStartStore(
    private val dataStore: DataStore<Preferences>,
) : RouteStartPreferences {
    override fun getRouteStartConfig(): Flow<RouteStartConfig?> =
        dataStore.safeData("route start config").map { prefs -> prefs[Keys.ROUTE_START_CONFIG]?.let(::decode) }

    override suspend fun setRouteStartConfig(config: RouteStartConfig) = dataStore.setPref(Keys.ROUTE_START_CONFIG, encode(config))

    private fun encode(config: RouteStartConfig): String =
        with(config) {
            listOf(isLooping, isReverse, isReturnToLocation, followRoadsToStart, teleportBetweenWaypoints)
                .joinToString("|") { if (it) "1" else "0" } + "|$teleportBetweenDelaySeconds|${if (loopWalksBack) "1" else "0"}"
        }

    private fun decode(value: String): RouteStartConfig? {
        val parts = value.split("|")
        val delay = parts.getOrNull(5)?.toIntOrNull()
        val walkBack = parts.getOrNull(6)
        if (parts.size !in 6..7 || delay == null || parts.take(5).any { it != "0" && it != "1" }) return null
        if (walkBack != null && walkBack != "0" && walkBack != "1") return null
        val (loop, reverse, returnToLocation, roads, teleportBetween) = parts.take(5).map { it == "1" }
        return RouteStartConfig(
            isLooping = loop,
            isReverse = reverse,
            isReturnToLocation = returnToLocation,
            followRoadsToStart = roads,
            teleportBetweenWaypoints = teleportBetween,
            teleportBetweenDelaySeconds = delay,
            loopWalksBack = walkBack == "1",
        )
    }
}
