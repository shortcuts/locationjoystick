package com.locationjoystick.core.location

import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RecentSearch
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedUnit

/**
 * State shared between [MapScreen][com.locationjoystick.feature.map.impl.MapScreen] and
 * [MapFloatingView][com.locationjoystick.feature.widget.impl.MapFloatingView].
 *
 * Owned by [MapController]. Both surfaces read from the same StateFlow — there is no
 * per-surface copy of this data.
 */
data class MapSharedState(
    val currentPosition: LatLng? = null,
    val mockLocationState: MockLocationState = MockLocationState.IDLE,
    val mockMode: MockMode = MockMode.JOYSTICK,
    val isWalkPaused: Boolean = false,
    val walkMode: WalkMode = WalkMode.Idle,
    val routeTrace: List<LatLng>? = null,
    val routes: List<Route> = emptyList(),
    val favorites: List<FavoriteLocation> = emptyList(),
    val favoriteCooldownStates: Map<String, CooldownState> = emptyMap(),
    val isRoaming: Boolean = false,
    val isRoamingPaused: Boolean = false,
    val mapFeatureOrder: List<AppFeature> = AppFeature.DEFAULT_MAP_ORDER,
    val enabledMapFeatures: Set<AppFeature> = AppFeature.DEFAULT_MAP_ENABLED,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val recentSearches: List<RecentSearch> = emptyList(),
    val roamingDefaults: RoamingDefaults = RoamingDefaults(),
)

val MapSharedState.walkTarget: LatLng? get() = (walkMode as? WalkMode.Walking)?.target
val MapSharedState.walkStart: LatLng? get() = (walkMode as? WalkMode.Walking)?.start
val MapSharedState.ephemeralWaypoints: List<LatLng>
    get() = (walkMode as? WalkMode.EphemeralReplay)?.waypoints ?: emptyList()
val MapSharedState.isWalkActive: Boolean get() = walkMode !is WalkMode.Idle
val MapSharedState.isRouteReplay: Boolean get() = mockMode == MockMode.ROUTE_REPLAY
val MapSharedState.isSpoofing: Boolean get() = mockLocationState == MockLocationState.RUNNING

fun MapSharedState.nonPositionKey(): Any =
    Triple(
        Triple(mockLocationState, mockMode, isWalkPaused) to
            Triple(walkMode, routeTrace, routes) to
            Triple(favorites, favoriteCooldownStates, isRoaming),
        Triple(isRoamingPaused, speedUnit, recentSearches),
        roamingDefaults,
    )
