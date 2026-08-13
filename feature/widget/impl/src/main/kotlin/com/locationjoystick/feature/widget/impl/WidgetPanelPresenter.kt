package com.locationjoystick.feature.widget.impl

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.ephemeralWaypoints
import com.locationjoystick.core.location.walkTarget
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.view.WindowManager as AndroidWindowManager

/**
 * Owns the secondary floating panels shown by [FloatingWidgetService]
 * (favorites, routes, and map) plus their backing window/compose plumbing.
 *
 * All data state is read from [MapController.sharedState] — the single source of truth shared
 * with MapScreen. Service-bound operations (move-to-back, binding-specific teleport) are
 * delegated back to the host service via [Callbacks].
 */
internal class WidgetPanelPresenter(
    private val context: android.content.Context,
    private val windowManager: AndroidWindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val serviceScope: CoroutineScope,
    private val mapController: MapController,
    private val callbacks: Callbacks,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val TAG = "WidgetPanelPresenter"
    }

    /**
     * Service-bound operations invoked from panel action handlers. Implemented by
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

        fun walkViaRoads(pos: LatLng)

        fun stopRouteAndTeleport(pos: LatLng)

        fun stopRouteAndWalkTo(pos: LatLng)

        fun finishRouteAndWalkTo(pos: LatLng)

        fun addEphemeralWaypoint(pos: LatLng)

        fun startRoamingWith(defaults: RoamingDefaults)

        fun saveCurrentLocation(name: String)

        fun moveAppToBack()
    }

    private var panelComposeView: ComposeView? = null

    /**
     * Expanded state of the floating map's route controls (pause/resume + stop).
     *
     * Owned here rather than `remember`ed inside [MapFloatingView] because [showPanel] builds a
     * fresh [ComposeView] on every open — local composable state would silently reset to collapsed
     * each time the map panel is reopened mid-replay, leaving no way back to the controls.
     */
    @VisibleForTesting
    internal val mapRouteControlsExpanded = MutableStateFlow(false)

    init {
        // Collapse once the replay ends so the next route does not start pre-expanded.
        serviceScope.launch {
            mapController.sharedState
                .map { it.mockMode == MockMode.ROUTE_REPLAY }
                .distinctUntilChanged()
                .collect { isReplaying -> if (!isReplaying) mapRouteControlsExpanded.value = false }
        }
    }

    private fun panelLayoutParams() =
        AndroidWindowManager.LayoutParams(
            AndroidWindowManager.LayoutParams.MATCH_PARENT,
            AndroidWindowManager.LayoutParams.MATCH_PARENT,
            AndroidWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            AndroidWindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                AndroidWindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        )

    // Map panel allows keyboard focus so the Nominatim search field accepts text input.
    // SOFT_INPUT_ADJUST_RESIZE ensures the keyboard pushes the panel content up.
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
        showPanel(params = mapPanelLayoutParams(), logTag = "favorites") {
            val shared by mapController.sharedState.collectAsStateWithLifecycle()
            val hideTeleportFeatures by settingsRepository.getHideTeleportFeatures().collectAsStateWithLifecycle(initialValue = false)
            FavoritesFloatingView(
                favorites = shared.favorites,
                cooldownStates = shared.favoriteCooldownStates,
                currentPosition = shared.currentPosition,
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
                onAddFromHere = { name -> callbacks.saveCurrentLocation(name) },
                hideTeleport = hideTeleportFeatures,
            )
        }
    }

    fun showRoutesFloatingView() {
        showPanel(logTag = "routes") {
            val routes by remember { mapController.sharedState.map { it.routes } }
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val hideTeleportFeatures by settingsRepository.getHideTeleportFeatures().collectAsStateWithLifecycle(initialValue = false)
            RoutesFloatingView(
                routes = routes,
                onDismiss = { hidePanelView() },
                onStartRoute = { routeId, isLooping, isReverse, isReturnToLocation, teleportToStart ->
                    callbacks.startRouteReplayWithMode(routeId, isLooping, isReverse, isReturnToLocation, teleportToStart)
                    callbacks.moveAppToBack()
                },
                hideTeleport = hideTeleportFeatures,
            )
        }
    }

    fun showMapFloatingView() {
        showPanel(params = mapPanelLayoutParams(), logTag = "map") {
            val shared by mapController.sharedState.collectAsStateWithLifecycle()
            val initialPosition = remember { mapController.sharedState.value.currentPosition }
            val quickWalk by settingsRepository.getFloatingMapQuickWalk().collectAsStateWithLifecycle(initialValue = false)
            val hideTeleportFeatures by settingsRepository.getHideTeleportFeatures().collectAsStateWithLifecycle(initialValue = false)
            val routeControlsExpanded by mapRouteControlsExpanded.collectAsStateWithLifecycle()
            MapFloatingView(
                currentPosition = shared.currentPosition,
                initialPosition = initialPosition,
                walkTarget = shared.walkTarget,
                routeWaypoints = shared.routeTrace,
                mockMode = shared.mockMode,
                mockLocationState = shared.mockLocationState,
                isRoamingPaused = shared.isRoamingPaused,
                favorites = shared.favorites,
                roamingDefaults = shared.roamingDefaults,
                speedUnit = shared.speedUnit,
                recentSearches = shared.recentSearches,
                ephemeralWaypoints = shared.ephemeralWaypoints.ifEmpty { null },
                onResumeRoaming = { mapController.resumeRoaming() },
                onPauseRoaming = { mapController.pauseRoaming() },
                onGeneratePreviewRoute = { center, radiusMeters, followRoads, speedProfileId ->
                    mapController.generateRoamingPreview(center, radiusMeters, followRoads, speedProfileId)
                },
                onTeleport = { pos ->
                    callbacks.teleport(pos)
                    hidePanelView()
                    callbacks.moveAppToBack()
                },
                onWalkTo = { pos -> callbacks.walkTo(pos) },
                onWalkViaRoads = { pos -> callbacks.walkViaRoads(pos) },
                onStopRouteAndTeleport = { pos ->
                    callbacks.stopRouteAndTeleport(pos)
                    hidePanelView()
                    callbacks.moveAppToBack()
                },
                onStopRouteAndWalkTo = { pos -> callbacks.stopRouteAndWalkTo(pos) },
                onFinishRouteAndWalkTo = { pos -> callbacks.finishRouteAndWalkTo(pos) },
                onAddEphemeralWaypoint = { pos -> callbacks.addEphemeralWaypoint(pos) },
                onStartRoaming = { defaults -> callbacks.startRoamingWith(defaults) },
                enabledMapFabFeatures = shared.enabledMapFeatures,
                onStopRoaming = { mapController.stopRoaming() },
                onStopRouteReplay = { mapController.stopRouteReplay() },
                onPauseRouteReplay = { mapController.pauseRouteReplay() },
                onResumeRouteReplay = { mapController.resumeRouteReplay() },
                isRouteControlsExpanded = routeControlsExpanded,
                onRouteControlsExpandedChange = { expanded -> mapRouteControlsExpanded.value = expanded },
                onOpenRoutes = { showRoutesFloatingView() },
                onDismiss = { hidePanelView() },
                onSearchCommitted = { name, lat, lon -> mapController.addRecentSearch(name, lat, lon) },
                cooldownForPosition = { pos -> mapController.cooldownForPosition(pos) },
                onSaveCurrentLocation = { name -> callbacks.saveCurrentLocation(name) },
                quickWalk = quickWalk,
                hideTeleportFeatures = hideTeleportFeatures,
            )
        }
    }
}
