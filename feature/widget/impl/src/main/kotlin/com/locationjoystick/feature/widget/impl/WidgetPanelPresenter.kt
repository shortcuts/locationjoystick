package com.locationjoystick.feature.widget.impl

import android.util.Log
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.sortedByAge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import android.view.WindowManager as AndroidWindowManager

/**
 * Owns the secondary floating panels shown by [FloatingWidgetService]
 * (favorites, routes, and map) plus their backing window/compose plumbing.
 *
 * Service-only operations (teleport, walk, route replay, roaming, ephemeral waypoints,
 * sending the host app to the background) are delegated back to the host service via
 * [Callbacks]. The presenter owns the panel [ComposeView] and disposes it on [hidePanelView].
 */
internal class WidgetPanelPresenter(
    private val context: android.content.Context,
    private val windowManager: AndroidWindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val serviceScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val favoriteRepository: FavoriteRepository,
    private val routeRepository: RouteRepository,
    private val locationRepository: LocationRepository,
    private val roamingRepository: RoamingRepository,
    private val teleportUseCase: TeleportUseCase,
    private val callbacks: Callbacks,
) {
    companion object {
        private const val TAG = "WidgetPanelPresenter"
    }

    /**
     * Service-only operations invoked from panel action handlers. Implemented by
     * [FloatingWidgetService] so the presenter stays free of service plumbing.
     */
    internal interface Callbacks {
        fun teleportToFavorite(favorite: FavoriteLocation)

        fun startWalkToFavorite(favorite: FavoriteLocation)

        fun startWalkViaRoadsToFavorite(favorite: FavoriteLocation)

        fun startRouteReplayWithMode(
            routeId: String,
            isLooping: Boolean,
            isReverse: Boolean,
            isReturnToLocation: Boolean,
            teleportToStart: Boolean,
        )

        fun teleport(pos: LatLng)

        fun walkTo(pos: LatLng)

        fun stopRouteAndTeleport(pos: LatLng)

        fun stopRouteAndWalkTo(pos: LatLng)

        fun finishRouteAndWalkTo(pos: LatLng)

        fun addEphemeralWaypoint(pos: LatLng)

        fun startRoamingWith(defaults: RoamingDefaults)

        fun moveAppToBack()
    }

    // Floating panel data
    private val favoritesDataFlow = MutableStateFlow<List<FavoriteLocation>>(emptyList())
    private val routesDataFlow = MutableStateFlow<List<com.locationjoystick.core.model.Route>>(emptyList())

    private var panelComposeView: ComposeView? = null

    private fun panelLayoutParams() =
        AndroidWindowManager.LayoutParams(
            AndroidWindowManager.LayoutParams.MATCH_PARENT,
            AndroidWindowManager.LayoutParams.MATCH_PARENT,
            AndroidWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE prevents stealing keyboard focus from games/apps behind the panel.
            // FLAG_NOT_TOUCH_MODAL limits touch interception to panel bounds only.
            AndroidWindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                AndroidWindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        )

    // Map panel allows keyboard focus so the Nominatim search field accepts text input.
    // SOFT_INPUT_ADJUST_RESIZE ensures the keyboard pushes the panel content up rather than
    // overlapping it — required for overlay windows where the IME otherwise stays hidden.
    private fun mapPanelLayoutParams() =
        AndroidWindowManager
            .LayoutParams(
                AndroidWindowManager.LayoutParams.MATCH_PARENT,
                AndroidWindowManager.LayoutParams.MATCH_PARENT,
                AndroidWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                AndroidWindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).also {
                @Suppress("DEPRECATION")
                it.softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

    fun hidePanelView() {
        panelComposeView?.let { view ->
            try {
                if (view.isAttachedToWindow) windowManager.removeViewImmediate(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove panel view", e)
            }
            // Dispose the composition so the panel's Recomposer and collected flows are released.
            // Without this the previous panel's composition leaks each time a new panel is shown.
            view.disposeComposition()
        }
        panelComposeView = null
    }

    private fun newComposeView(): ComposeView =
        ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        }

    private fun showPanel(
        params: AndroidWindowManager.LayoutParams = panelLayoutParams(),
        logTag: String = "panel",
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        serviceScope.launch {
            val panel = newComposeView()
            panel.setContent { LjTheme { content() } }
            hidePanelView()
            if (!isActive) {
                Log.w(TAG, "Service destroyed before $logTag panel could be shown")
                return@launch
            }
            panelComposeView = panel
            try {
                windowManager.addView(panel, params)
            } catch (e: Exception) {
                panelComposeView = null
                Log.e(TAG, "Failed to show $logTag panel", e)
            }
        }
    }

    fun showFavoritesFloatingView() {
        serviceScope.launch {
            val favSortNewestFirst = settingsRepository.getFavoritesSortNewestFirst().first()
            val rawFavorites = favoriteRepository.getFavorites().first()
            favoritesDataFlow.value = rawFavorites.sortedByAge(favSortNewestFirst)
            // mapPanelLayoutParams() drops FLAG_NOT_FOCUSABLE so the keyboard can appear
            // when the user taps "Add from current location" text field.
            showPanel(params = mapPanelLayoutParams(), logTag = "favorites") {
                val favs by favoritesDataFlow.collectAsStateWithLifecycle()
                FavoritesFloatingView(
                    favorites = favs,
                    onDismiss = { hidePanelView() },
                    onTeleport = { fav ->
                        callbacks.teleportToFavorite(fav)
                        callbacks.moveAppToBack()
                    },
                    onWalk = { fav ->
                        callbacks.startWalkToFavorite(fav)
                        callbacks.moveAppToBack()
                    },
                    onWalkViaRoads = { fav ->
                        callbacks.startWalkViaRoadsToFavorite(fav)
                        callbacks.moveAppToBack()
                    },
                    onAddFromHere = { name ->
                        serviceScope.launch {
                            val pos = locationRepository.currentPosition.value
                            if (pos != null) {
                                favoriteRepository.addFavorite(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    position = pos,
                                )
                                val refreshed = favoriteRepository.getFavorites().first()
                                favoritesDataFlow.value = refreshed.sortedByAge(favSortNewestFirst)
                            } else {
                                Log.w(TAG, "Cannot add favorite: no current position")
                            }
                        }
                    },
                )
            }
        }
    }

    fun showRoutesFloatingView() {
        serviceScope.launch {
            val routeSortNewestFirst = settingsRepository.getRoutesSortNewestFirst().first()
            val rawRoutes = routeRepository.getRoutes().first()
            routesDataFlow.value = rawRoutes.sortedByAge(routeSortNewestFirst)
            showPanel(logTag = "routes") {
                val routes by routesDataFlow.collectAsStateWithLifecycle()
                RoutesFloatingView(
                    routes = routes,
                    onDismiss = { hidePanelView() },
                    onStartRoute = { routeId, isLooping, isReverse, isReturnToLocation, teleportToStart ->
                        callbacks.startRouteReplayWithMode(routeId, isLooping, isReverse, isReturnToLocation, teleportToStart)
                        callbacks.moveAppToBack()
                    },
                )
            }
        }
    }

    fun showMapFloatingView() {
        showPanel(params = mapPanelLayoutParams(), logTag = "map") {
            val currentPosition by locationRepository.currentPosition.collectAsStateWithLifecycle()
            val initialPosition = remember { locationRepository.currentPosition.value }
            val walkTarget by locationRepository.walkTarget.collectAsStateWithLifecycle()
            val routeWaypoints by locationRepository.routeWaypoints.collectAsStateWithLifecycle()
            val mockMode by locationRepository.currentMode.collectAsStateWithLifecycle()
            val mockLocationState by locationRepository.mockLocationState.collectAsStateWithLifecycle()
            val isRoamingPaused by roamingRepository.isRoamingPaused.collectAsStateWithLifecycle(initialValue = false)
            val favorites by remember { favoriteRepository.getFavorites() }.collectAsStateWithLifecycle(initialValue = emptyList())
            val roamingDefaults by remember { settingsRepository.getRoamingDefaults() }.collectAsStateWithLifecycle(
                initialValue =
                    RoamingDefaults(
                        radiusMeters = AppConstants.RoamingConstants.DEFAULT_RADIUS_METERS,
                        distanceMeters = AppConstants.RoamingConstants.DEFAULT_DISTANCE_METERS,
                        speedProfileId = AppConstants.ProfileConstants.DEFAULT_ACTIVE_PROFILE_ID,
                        followRoads = AppConstants.RoamingConstants.DEFAULT_FOLLOW_ROADS,
                        returnToInitialLocation = AppConstants.RoamingConstants.DEFAULT_RETURN_TO_START,
                    ),
            )
            val speedUnit by remember {
                settingsRepository.getSpeedUnit()
            }.collectAsStateWithLifecycle(initialValue = SpeedUnit.KMH)
            val recentSearches by remember { settingsRepository.getRecentSearches() }.collectAsStateWithLifecycle(
                initialValue = emptyList(),
            )
            MapFloatingView(
                currentPosition = currentPosition,
                initialPosition = initialPosition,
                walkTarget = walkTarget,
                routeWaypoints = routeWaypoints,
                mockMode = mockMode,
                mockLocationState = mockLocationState,
                isRoamingPaused = isRoamingPaused,
                favorites = favorites,
                roamingDefaults = roamingDefaults,
                speedUnit = speedUnit,
                onStartSpoofing = { locationRepository.startSpoofing() },
                onStopSpoofing = { locationRepository.stopSpoofing() },
                onResumeRoaming = { roamingRepository.resumeRoaming() },
                onPauseRoaming = { roamingRepository.pauseRoaming() },
                onGeneratePreviewRoute = { center, radiusMeters, followRoads, speedProfileId ->
                    roamingRepository.generatePreviewRoute(
                        center = center,
                        radiusMeters = radiusMeters,
                        followRoads = followRoads,
                        speedProfileId = speedProfileId,
                    )
                },
                onTeleport = { pos ->
                    callbacks.teleport(pos)
                    hidePanelView()
                    callbacks.moveAppToBack()
                },
                onWalkTo = { pos ->
                    callbacks.walkTo(pos)
                    hidePanelView()
                    callbacks.moveAppToBack()
                },
                onStopRouteAndTeleport = { pos ->
                    callbacks.stopRouteAndTeleport(pos)
                    hidePanelView()
                    callbacks.moveAppToBack()
                },
                onStopRouteAndWalkTo = { pos ->
                    callbacks.stopRouteAndWalkTo(pos)
                    hidePanelView()
                    callbacks.moveAppToBack()
                },
                onFinishRouteAndWalkTo = { pos ->
                    callbacks.finishRouteAndWalkTo(pos)
                },
                onAddEphemeralWaypoint = { pos ->
                    callbacks.addEphemeralWaypoint(pos)
                },
                onStartRoaming = { defaults -> callbacks.startRoamingWith(defaults) },
                onStopRoaming = {
                    serviceScope.launch {
                        roamingRepository.stopRoaming()
                    }
                },
                onDismiss = { hidePanelView() },
                recentSearches = recentSearches,
                onSearchCommitted = { name, lat, lon ->
                    serviceScope.launch { settingsRepository.addRecentSearch(name, lat, lon) }
                },
                cooldownForPosition = { pos -> teleportUseCase.cooldownFor(pos) },
            )
        }
    }
}
