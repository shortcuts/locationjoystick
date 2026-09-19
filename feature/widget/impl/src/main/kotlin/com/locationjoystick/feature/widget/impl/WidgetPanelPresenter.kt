package com.locationjoystick.feature.widget.impl

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.orderedCapturedPoints
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.RouteStartConfig
import com.locationjoystick.core.location.ephemeralWaypoints
import com.locationjoystick.core.location.walkStart
import com.locationjoystick.core.location.walkTarget
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.ThemeMode
import com.locationjoystick.core.model.Waypoint
import com.locationjoystick.core.model.isRoutePlaying
import com.locationjoystick.core.model.toConfig
import com.locationjoystick.feature.widget.impl.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
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
    private val captureRepository: CaptureCoordinatesRepository,
    private val favoriteRepository: FavoriteRepository,
    private val routeRepository: RouteRepository,
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
            config: RouteStartConfig,
        )

        fun teleport(pos: LatLng)

        fun walkTo(pos: LatLng)

        fun walkViaRoads(pos: LatLng)

        fun stopRouteAndTeleport(pos: LatLng)

        fun stopRouteAndWalkTo(pos: LatLng)

        fun finishRouteAndWalkTo(pos: LatLng)

        fun addEphemeralWaypoint(
            pos: LatLng,
            followRoads: Boolean,
        )

        fun startRoamingWith(defaults: RoamingDefaults)

        fun saveCurrentLocation(name: String)

        fun saveFavorite(
            name: String,
            position: LatLng,
        )

        fun moveAppToBack()
    }

    private var panelComposeView: ComposeView? = null
    private var compactMapX = 0
    private var compactMapY = 0

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

    // Favorites, routes, and map panels allow keyboard focus so search / Nominatim
    // fields can take IME input. SOFT_INPUT_ADJUST_RESIZE pushes panel content above the keyboard.
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

    private fun floatingMapLayoutParams(expanded: Boolean): AndroidWindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val width =
            if (expanded) {
                AndroidWindowManager.LayoutParams.MATCH_PARENT
            } else {
                compactFloatingMapWidth(
                    metrics.widthPixels,
                    metrics.density,
                )
            }
        val height =
            if (expanded) {
                AndroidWindowManager.LayoutParams.MATCH_PARENT
            } else {
                compactFloatingMapHeight(
                    metrics.heightPixels,
                    metrics.density,
                )
            }
        return AndroidWindowManager
            .LayoutParams(
                width,
                height,
                AndroidWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                floatingMapWindowFlags(),
                android.graphics.PixelFormat.TRANSLUCENT,
            ).also {
                it.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                it.x = if (expanded) 0 else compactMapX
                it.y = if (expanded) 0 else compactMapY
                @Suppress("DEPRECATION")
                it.softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
    }

    private fun updateFloatingMapLayout(
        expanded: Boolean,
        dragX: Int = 0,
        dragY: Int = 0,
    ) {
        val view = panelComposeView ?: return
        val metrics = context.resources.displayMetrics
        if (!expanded) {
            val width = compactFloatingMapWidth(metrics.widthPixels, metrics.density)
            val height = compactFloatingMapHeight(metrics.heightPixels, metrics.density)
            compactMapX = (compactMapX + dragX).coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
            compactMapY = (compactMapY + dragY).coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))
        }
        try {
            windowManager.updateViewLayout(view, floatingMapLayoutParams(expanded))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resize floating map", e)
        }
    }

    /** Bottom sheet: apps above the paste box stay tappable; minimize drops focus so copy works. */
    private fun pasteOverlayLayoutParams() =
        AndroidWindowManager
            .LayoutParams(
                pasteOverlayWidth(),
                pasteOverlayHeight(),
                AndroidWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                pasteOverlayWindowFlags(minimized = false),
                android.graphics.PixelFormat.TRANSLUCENT,
            ).also {
                it.gravity = pasteOverlayGravity()
                @Suppress("DEPRECATION")
                it.softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

    private fun applyPasteOverlayMinimized(minimized: Boolean) {
        val view = panelComposeView ?: return
        val params = view.layoutParams as? AndroidWindowManager.LayoutParams ?: return
        params.flags = pasteOverlayWindowFlags(minimized)
        params.gravity = pasteOverlayGravity()
        try {
            view.requestLayout()
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update paste overlay flags", e)
        }
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
            panel.setContent {
                val themeMode by settingsRepository.getThemeMode().collectAsStateWithLifecycle(
                    initialValue = ThemeMode.DARK,
                )
                LjTheme(darkTheme = themeMode == ThemeMode.DARK) { content() }
            }
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
            val sortMode by settingsRepository.getFavoritesSortMode().collectAsStateWithLifecycle(
                initialValue = com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST,
            )
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
                onRename = { favorite, name ->
                    serviceScope.launch { favoriteRepository.updateFavorite(favorite.copy(name = name)) }
                },
                onDelete = { favorite ->
                    serviceScope.launch { favoriteRepository.deleteFavorite(favorite.id) }
                },
                onAddFromHere = { name -> callbacks.saveCurrentLocation(name) },
                hideTeleport = hideTeleportFeatures,
                sortMode = sortMode,
                onSortModeSelected = { mode -> serviceScope.launch { settingsRepository.setFavoritesSortMode(mode) } },
            )
        }
    }

    fun showPasteCoordinatesFloatingView() {
        showPanel(params = pasteOverlayLayoutParams(), logTag = "paste-coordinates") {
            var minimized by remember { mutableStateOf(false) }
            LaunchedEffect(minimized) { applyPasteOverlayMinimized(minimized) }
            val hideTeleportFeatures by settingsRepository.getHideTeleportFeatures().collectAsStateWithLifecycle(initialValue = false)
            PasteCoordinatesFloatingView(
                minimized = minimized,
                onToggleMinimize = { minimized = !minimized },
                onDismiss = { hidePanelView() },
                onTeleport = { pos ->
                    callbacks.teleport(pos)
                    callbacks.moveAppToBack()
                },
                onWalk = { pos ->
                    callbacks.walkTo(pos)
                    callbacks.moveAppToBack()
                },
                onWalkViaRoads = { pos ->
                    callbacks.walkViaRoads(pos)
                    callbacks.moveAppToBack()
                },
                onSaveFavorite = { name, pos -> callbacks.saveFavorite(name, pos) },
                onSaveRoute = { name, points -> mapController.savePastedRoute(name, points) },
                onStartRoute = {
                    points,
                    loop,
                    reverse,
                    returnToLocation,
                    followRoads,
                    planting,
                    teleportBetweenWaypoints,
                    delaySeconds,
                    ->
                    mapController.startPastedRouteReplay(
                        points = points,
                        config =
                            RouteStartConfig(
                                isLooping = loop,
                                isReverse = reverse,
                                isReturnToLocation = returnToLocation,
                                followRoadsToStart = followRoads,
                                isPlanting = planting,
                                teleportBetweenWaypoints = teleportBetweenWaypoints,
                                teleportBetweenDelaySeconds = delaySeconds,
                            ),
                    )
                    hidePanelView()
                },
                hideTeleport = hideTeleportFeatures,
            )
        }
    }

    fun showRoamingFloatingView() {
        showPanel(params = mapPanelLayoutParams(), logTag = "roaming") {
            val shared by mapController.sharedState.collectAsStateWithLifecycle()
            val initialDefaults = remember { shared.roamingDefaults }
            var draft by remember { mutableStateOf(initialDefaults) }
            RoamingFloatingView(
                draft = draft,
                speedUnit = shared.speedUnit,
                hasCurrentPosition = shared.currentPosition != null,
                isSpoofingActive =
                    shared.mockLocationState == MockLocationState.RUNNING ||
                        shared.mockLocationState == MockLocationState.PAUSED,
                routePlaying = isRoutePlaying(shared.mockMode, shared.mockLocationState),
                onDismiss = { hidePanelView() },
                onDraftChange = { draft = it },
                onGenerate = { defaults ->
                    val pos = mapController.sharedState.value.currentPosition
                    if (pos == null) {
                        null
                    } else {
                        mapController.generateRoamingPreview(defaults.toConfig(pos))
                    }
                },
                onStart = { defaults ->
                    callbacks.startRoamingWith(defaults)
                    callbacks.moveAppToBack()
                },
            )
        }
    }

    fun showRoutesFloatingView() {
        showPanel(params = mapPanelLayoutParams(), logTag = "routes") {
            val routes by remember { mapController.sharedState.map { it.routes } }
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val hideTeleportFeatures by settingsRepository.getHideTeleportFeatures().collectAsStateWithLifecycle(initialValue = false)
            val sortMode by settingsRepository.getRoutesSortMode().collectAsStateWithLifecycle(
                initialValue = com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST,
            )
            RoutesFloatingView(
                routes = routes,
                onDismiss = { hidePanelView() },
                onStartRoute = {
                    routeId,
                    isLooping,
                    isReverse,
                    isReturnToLocation,
                    followRoadsToStart,
                    isPlanting,
                    teleportBetweenWaypoints,
                    teleportBetweenDelaySeconds,
                    ->
                    callbacks.startRouteReplayWithMode(
                        routeId,
                        RouteStartConfig(
                            isLooping = isLooping,
                            isReverse = isReverse,
                            isReturnToLocation = isReturnToLocation,
                            followRoadsToStart = followRoadsToStart,
                            isPlanting = isPlanting,
                            teleportBetweenWaypoints = teleportBetweenWaypoints,
                            teleportBetweenDelaySeconds = teleportBetweenDelaySeconds,
                        ),
                    )
                    callbacks.moveAppToBack()
                },
                onTeleport = { pos -> callbacks.teleport(pos) },
                onRename = { route, name ->
                    serviceScope.launch { routeRepository.renameRoute(route.id, name) }
                },
                onDelete = { route ->
                    serviceScope.launch { routeRepository.deleteRoute(route.id) }
                },
                onShareOpened = { hidePanelView() },
                hideTeleport = hideTeleportFeatures,
                sortMode = sortMode,
                onSortModeSelected = { mode -> serviceScope.launch { settingsRepository.setRoutesSortMode(mode) } },
            )
        }
    }

    fun showMapFloatingView() {
        compactMapX = (12 * context.resources.displayMetrics.density).toInt()
        compactMapY = (48 * context.resources.displayMetrics.density).toInt()
        showPanel(params = floatingMapLayoutParams(expanded = false), logTag = "map") {
            var mapExpanded by remember { mutableStateOf(false) }
            val shared by mapController.sharedState.collectAsStateWithLifecycle()
            val initialPosition = remember { mapController.sharedState.value.currentPosition }
            val quickWalk by settingsRepository.getFloatingMapQuickWalk().collectAsStateWithLifecycle(initialValue = false)
            val hideTeleportFeatures by settingsRepository.getHideTeleportFeatures().collectAsStateWithLifecycle(initialValue = false)
            val showRouteJumpButtons by settingsRepository.getShowRouteJumpButtons().collectAsStateWithLifecycle(
                initialValue = AppConstants.ProfileConstants.SHOW_ROUTE_JUMP_BUTTONS_DEFAULT,
            )
            val routeControlsExpanded by mapRouteControlsExpanded.collectAsStateWithLifecycle()
            val captureEnabled by captureRepository.captureEnabled.collectAsStateWithLifecycle(initialValue = false)
            val capturedPoints by captureRepository.points.collectAsStateWithLifecycle(initialValue = emptyList())
            val favoritesSortMode by settingsRepository.getFavoritesSortMode().collectAsStateWithLifecycle(
                initialValue = com.locationjoystick.core.model.SavedItemSortMode.NEWEST_FIRST,
            )
            var captureRouteName by remember { mutableStateOf("") }
            var captureSaved by remember { mutableStateOf(false) }
            var captureSaveError by remember { mutableStateOf<String?>(null) }
            var captureOptimizeProximity by remember { mutableStateOf(true) }
            val captureInvalidRouteMessage = stringResource(R.string.widget_panel_presenter_capture_invalid_route)
            MapFloatingView(
                compact = !mapExpanded,
                onToggleExpanded = {
                    mapExpanded = !mapExpanded
                    updateFloatingMapLayout(expanded = mapExpanded)
                },
                onMoveCompact = { x, y -> updateFloatingMapLayout(expanded = false, dragX = x, dragY = y) },
                currentPosition = shared.currentPosition,
                initialPosition = initialPosition,
                walkTarget = shared.walkTarget,
                walkStart = shared.walkStart,
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
                onGeneratePreviewRoute = { config ->
                    mapController.generateRoamingPreview(config)
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
                onAddEphemeralWaypoint = { pos, followRoads -> callbacks.addEphemeralWaypoint(pos, followRoads) },
                onStartRoaming = { defaults -> callbacks.startRoamingWith(defaults) },
                enabledMapFabFeatures = shared.enabledMapFeatures,
                onStopRoaming = { mapController.stopRoaming() },
                onStopRouteReplay = { mapController.stopRouteReplay() },
                onPauseRouteReplay = { mapController.pauseRouteReplay() },
                onResumeRouteReplay = { mapController.resumeRouteReplay() },
                onJumpToNextWaypoint = { mapController.jumpToNextWaypoint() },
                onJumpToPreviousWaypoint = { mapController.jumpToPreviousWaypoint() },
                isRouteControlsExpanded = routeControlsExpanded,
                onRouteControlsExpandedChange = { expanded -> mapRouteControlsExpanded.value = expanded },
                onOpenRoutes = { showRoutesFloatingView() },
                onDismiss = { hidePanelView() },
                onSearchCommitted = { name, lat, lon -> mapController.addRecentSearch(name, lat, lon) },
                cooldownForPosition = { pos -> mapController.cooldownForPosition(pos) },
                onSaveCurrentLocation = { name -> callbacks.saveCurrentLocation(name) },
                onRenameFavorite = { favorite, name ->
                    serviceScope.launch { favoriteRepository.updateFavorite(favorite.copy(name = name)) }
                },
                onDeleteFavorite = { favorite ->
                    serviceScope.launch { favoriteRepository.deleteFavorite(favorite.id) }
                },
                onSaveFavorite = { name, pos -> callbacks.saveFavorite(name, pos) },
                onSavePastedRoute = { name, points -> mapController.savePastedRoute(name, points) },
                onStartPastedRoute = {
                    points,
                    loop,
                    reverse,
                    returnToLocation,
                    followRoads,
                    planting,
                    teleportBetweenWaypoints,
                    delaySeconds,
                    ->
                    mapController.startPastedRouteReplay(
                        points = points,
                        config =
                            RouteStartConfig(
                                isLooping = loop,
                                isReverse = reverse,
                                isReturnToLocation = returnToLocation,
                                followRoadsToStart = followRoads,
                                isPlanting = planting,
                                teleportBetweenWaypoints = teleportBetweenWaypoints,
                                teleportBetweenDelaySeconds = delaySeconds,
                            ),
                    )
                },
                quickWalk = quickWalk,
                hideTeleportFeatures = hideTeleportFeatures,
                showRouteJumpButtons = showRouteJumpButtons,
                routeProgress = shared.routeProgress,
                captureEnabled = captureEnabled,
                capturedPoints = capturedPoints,
                captureRouteName = captureRouteName,
                captureSaved = captureSaved,
                captureSaveError = captureSaveError,
                onCaptureEnabledChange = { enabled ->
                    serviceScope.launch { captureRepository.setCaptureEnabled(enabled) }
                },
                onCaptureRouteNameChange = { name ->
                    captureRouteName = name
                    captureSaved = false
                    captureSaveError = null
                },
                captureOptimizeProximity = captureOptimizeProximity,
                onCaptureOptimizeProximityChange = { optimize ->
                    captureOptimizeProximity = optimize
                    captureSaved = false
                },
                onSaveCapturedRoute = {
                    val name = captureRouteName.trim()
                    if (name.isEmpty() || capturedPoints.size < 2) {
                        captureSaveError = captureInvalidRouteMessage
                    } else {
                        serviceScope.launch {
                            val now = System.currentTimeMillis()
                            val positions = orderedCapturedPoints(capturedPoints, captureOptimizeProximity)
                            routeRepository.insertRoute(
                                Route(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    waypoints =
                                        positions.mapIndexed { index, latLng ->
                                            Waypoint(
                                                id = UUID.randomUUID().toString(),
                                                position = latLng,
                                                orderIndex = index,
                                            )
                                        },
                                    isLooping = false,
                                    routeType = RouteType.STRAIGHT,
                                    createdAt = now,
                                    updatedAt = now,
                                ),
                            )
                            captureSaved = true
                            captureSaveError = null
                        }
                    }
                },
                onClearCapturedPoints = { serviceScope.launch { captureRepository.clearPoints() } },
                onRemoveLastCaptured = { serviceScope.launch { captureRepository.removeLast() } },
                onRememberPreviousBrowser = { pkg ->
                    serviceScope.launch { captureRepository.setPreviousBrowserPackage(pkg) }
                },
                favoritesSortMode = favoritesSortMode,
                onFavoritesSortModeSelected = { mode ->
                    serviceScope.launch { settingsRepository.setFavoritesSortMode(mode) }
                },
            )
        }
    }
}
