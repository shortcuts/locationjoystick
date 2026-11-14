package com.locationjoystick.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.locationjoystick.core.model.LoopMode
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
            val bits = listOf(isReverse, isReturnToLocation, followRoadsToStart, teleportBetweenWaypoints).map { if (it) "1" else "0" }
            (listOf(loopMode.ordinal.toString()) + bits + teleportBetweenDelaySeconds).joinToString("|")
        }

    // Accepts the v1.0.2 six-field form and the unreleased seven-field form whose last bit meant walk back.
    private fun decode(value: String): RouteStartConfig? {
        val parts = value.split("|")
        val delay = parts.getOrNull(5)?.toIntOrNull()
        val walkBack = parts.getOrNull(6)
        val loopMode = parts[0].toIntOrNull()?.let { LoopMode.entries.getOrNull(it) }
        if (parts.size !in 6..7 || delay == null || loopMode == null || parts.subList(1, 5).any { it != "0" && it != "1" }) return null
        if (walkBack != null && walkBack != "0" && walkBack != "1") return null
        val (reverse, returnToLocation, roads, teleportBetween) = parts.subList(1, 5).map { it == "1" }
        return RouteStartConfig(
            loopMode = if (walkBack == "1" && loopMode == LoopMode.RESTART) LoopMode.WALK_BACK else loopMode,
            isReverse = reverse,
            isReturnToLocation = returnToLocation,
            followRoadsToStart = roads,
            teleportBetweenWaypoints = teleportBetween,
            teleportBetweenDelaySeconds = delay,
        )
    }
}
