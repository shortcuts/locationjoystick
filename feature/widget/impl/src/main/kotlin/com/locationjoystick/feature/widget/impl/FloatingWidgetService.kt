package com.locationjoystick.feature.widget.impl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.WalkCoordinator
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.location.EphemeralReplayController
import com.locationjoystick.core.location.MockLocationIntentBuilder
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.ElevationMode
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RoamingConfig
import com.locationjoystick.core.model.WidgetFeature
import com.locationjoystick.core.overlay.OverlayService
import com.locationjoystick.core.overlay.OverlayServiceHelper
import com.locationjoystick.feature.joystick.impl.JoystickOverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import android.view.WindowManager as AndroidWindowManager

/**
 * Floating widget overlay service.
 *
 * Displays a compact FAB that expands to a panel with quick-access controls:
 * - Joystick toggle and lock
 * - Routes list with replay controls
 * - Favorites list with teleport buttons
 * - Speed profile switcher
 *
 * The widget is configured via [WidgetFeature] items stored in DataStore.
 * Each feature can be enabled/disabled independently in Settings.
 *
 * Lifecycle:
 * - Starts collapsed (FAB only)
 * - Tap expands to full panel
 * - Drag to reposition (persisted via WindowManager params)
 *
 * Requires SYSTEM_ALERT_WINDOW permission (enforced by [OverlayService]).
 *
 * @see WidgetFeature for available features
 * @see SettingsRepository.getWidgetFeatures for configuration
 */
@AndroidEntryPoint
class FloatingWidgetService :
    OverlayService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    companion object {
        private const val TAG = "FloatingWidgetService"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "FloatingWidgetService coroutine crashed", throwable)
        }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
    private val overlayHelper = OverlayServiceHelper(TAG)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    @Inject lateinit var routeRepository: RouteRepository

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var favoriteRepository: FavoriteRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var roamingRepository: com.locationjoystick.core.data.RoamingRepository

    @Inject lateinit var walkCoordinator: WalkCoordinator

    @Inject lateinit var ephemeralReplayController: EphemeralReplayController

    @Inject lateinit var osrmClient: com.locationjoystick.core.routing.OsrmClient

    private var composeView: ComposeView? = null
    private var panelComposeView: ComposeView? = null

    // Joystick state
    private val joystickVisibleFlow = MutableStateFlow(false)
    private val joystickLockedFlow = MutableStateFlow(false)
    private val activeProfileIdFlow = MutableStateFlow("walk")

    // Activity state — driven entirely by locationRepository.currentMode via isActivityActive/isActivityPausable
    private val routeExpandedFlow = MutableStateFlow(false)

    // Master panel expand/collapse
    private val isPanelExpandedFlow = MutableStateFlow(false)

    private val elevationModeFlow = MutableStateFlow<ElevationMode?>(null)

    // Drag position — class-level so onConfigurationChanged can re-clamp them after rotation.
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    // Floating panel data
    private val favoritesDataFlow = MutableStateFlow<List<FavoriteLocation>>(emptyList())
    private val routesDataFlow = MutableStateFlow<List<com.locationjoystick.core.model.Route>>(emptyList())

    private var mockLocationService: MockLocationService? = null

    private var joystickService: JoystickOverlayService? = null
    private val joystickConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                val svc = (binder as JoystickOverlayService.LocalBinder).getService()
                joystickService = svc
                serviceScope.launch { svc.isVisible.collect { joystickVisibleFlow.value = it } }
                serviceScope.launch { svc.isLocked.collect { joystickLockedFlow.value = it } }
                Log.d(TAG, "Bound to JoystickOverlayService")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                joystickService = null
                joystickBound = false
                joystickVisibleFlow.value = false
                joystickLockedFlow.value = false
                Log.d(TAG, "Unbound from JoystickOverlayService")
            }
        }
    private var joystickBound = false

    private val mockLocationServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                mockLocationService = (binder as MockLocationService.LocalBinder).getService()
                Log.d(TAG, "Bound to MockLocationService")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mockLocationService = null
                Log.d(TAG, "Unbound from MockLocationService")
            }
        }

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        overlayHelper.registerOverlayVisibilityReceiver(this, this)
        overlayHelper.bindTrackedService(this, Intent(this, MockLocationService::class.java), mockLocationServiceConnection)
        val joystickIntent =
            Intent().apply {
                setClassName(packageName, "com.locationjoystick.feature.joystick.impl.JoystickOverlayService")
            }
        joystickBound = bindService(joystickIntent, joystickConnection, Context.BIND_AUTO_CREATE)
        lifecycleScope.launch {
            settingsRepository.getActiveSpeedProfile().collect { profile ->
                activeProfileIdFlow.value = profile.id
            }
        }
        lifecycleScope.launch {
            locationRepository.isActivityActive.collect { active ->
                if (!active) routeExpandedFlow.value = false
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        walkCoordinator.cancel()
        serviceScope.cancel()
        hidePanelView()
        // Tear down the FAB composition so the Recomposer and any captured state holders are
        // released. The base OverlayService.onDestroy() removes the view from the WindowManager.
        composeView?.disposeComposition()
        composeView = null
        overlayHelper.cleanupOverlayBindings(this)
        if (joystickBound) {
            try {
                unbindService(joystickConnection)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Joystick service was not bound when attempting to unbind", e)
            }
        }
        super.onDestroy()
    }

    override fun getWindowManagerParams(view: View): AndroidWindowManager.LayoutParams {
        dragOffsetY = resources.displayMetrics.heightPixels / 2f
        return AndroidWindowManager
            .LayoutParams(
                AndroidWindowManager.LayoutParams.WRAP_CONTENT,
                AndroidWindowManager.LayoutParams.WRAP_CONTENT,
                AndroidWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                AndroidWindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    AndroidWindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.START or Gravity.TOP
                x = 0
                y = dragOffsetY.toInt()
            }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val params = currentParams ?: return
        dragOffsetX = params.x.toFloat()
        dragOffsetY = params.y.toFloat()
    }

    override fun createOverlayView(): View {
        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWidgetService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            }
        composeView = view

        view.setContent {
            val features by settingsRepository.getWidgetFeatures().collectAsStateWithLifecycle(initialValue = emptyList())
            val joystickVisible by joystickVisibleFlow.collectAsStateWithLifecycle()
            val joystickLocked by joystickLockedFlow.collectAsStateWithLifecycle()
            val activeProfileId by activeProfileIdFlow.collectAsStateWithLifecycle()
            val isActivityActive by locationRepository.isActivityActive.collectAsStateWithLifecycle(initialValue = false)
            val isActivityPausable by locationRepository.isActivityPausable.collectAsStateWithLifecycle(initialValue = false)
            val mockMode by locationRepository.currentMode.collectAsStateWithLifecycle()
            val mockLocationState by locationRepository.mockLocationState.collectAsStateWithLifecycle()
            val isWalkPaused by locationRepository.isWalkPaused.collectAsStateWithLifecycle()
            val isRoamingPausedWidget by roamingRepository.isRoamingPaused.collectAsStateWithLifecycle(initialValue = false)
            val isActivityPaused =
                isWalkPaused ||
                    (
                        mockMode == com.locationjoystick.core.model.MockMode.ROUTE_REPLAY &&
                            mockLocationState == com.locationjoystick.core.model.MockLocationState.PAUSED
                    ) ||
                    (mockMode == com.locationjoystick.core.model.MockMode.ROAMING && isRoamingPausedWidget)
            val routeExpanded by routeExpandedFlow.collectAsStateWithLifecycle()
            val isPanelExpanded by isPanelExpandedFlow.collectAsStateWithLifecycle()
            val elevationMode by elevationModeFlow.collectAsStateWithLifecycle()

            LjTheme {
                WidgetPanel(
                    features = features,
                    joystickVisible = joystickVisible,
                    joystickLocked = joystickLocked,
                    activeProfileId = activeProfileId,
                    isActivityActive = isActivityActive,
                    isActivityPaused = isActivityPaused,
                    isActivityPausable = isActivityPausable,
                    routeExpanded = routeExpanded,
                    isPanelExpanded = isPanelExpanded,
                    elevationMode = elevationMode,
                    onToggleMaster = { isPanelExpandedFlow.value = !isPanelExpandedFlow.value },
                    onFeatureClicked = { feature -> onFeatureButtonClicked(feature) },
                    onElevationModeSelected = { mode -> onElevationModeSelected(mode) },
                    onRouteClicked = { onRouteIconClicked() },
                    onRoutePauseResume = { onRoutePauseResumeClicked() },
                    onRouteStop = { onRouteStopClicked() },
                    onDrag = { dx, dy ->
                        dragOffsetX += dx
                        dragOffsetY += dy
                        updateOverlayPosition(dragOffsetX.toInt(), dragOffsetY.toInt())
                    },
                )
            }
        }

        return view
    }

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
    private fun mapPanelLayoutParams() =
        AndroidWindowManager.LayoutParams(
            AndroidWindowManager.LayoutParams.MATCH_PARENT,
            AndroidWindowManager.LayoutParams.MATCH_PARENT,
            AndroidWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            AndroidWindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        )

    private fun hidePanelView() {
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

    private fun showPanel(
        params: AndroidWindowManager.LayoutParams = panelLayoutParams(),
        logTag: String = "panel",
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        serviceScope.launch {
            val panel =
                ComposeView(this@FloatingWidgetService).apply {
                    setViewTreeLifecycleOwner(this@FloatingWidgetService)
                    setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
                }
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

    private fun showFavoritesFloatingView() {
        serviceScope.launch {
            val favSortNewestFirst = settingsRepository.getFavoritesSortNewestFirst().first()
            val rawFavorites = favoriteRepository.getFavorites().first()
            favoritesDataFlow.value =
                if (favSortNewestFirst) rawFavorites.sortedByDescending { it.createdAt } else rawFavorites.sortedBy { it.createdAt }
            showPanel(logTag = "favorites") {
                val favs by favoritesDataFlow.collectAsStateWithLifecycle()
                FavoritesFloatingView(
                    favorites = favs,
                    onDismiss = { hidePanelView() },
                    onTeleport = { fav ->
                        teleportToFavorite(fav)
                        moveAppToBack()
                    },
                    onWalk = { fav ->
                        startWalkToFavorite(fav)
                        moveAppToBack()
                    },
                    onWalkViaRoads = { fav ->
                        startWalkViaRoadsToFavorite(fav)
                        moveAppToBack()
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
                                favoritesDataFlow.value =
                                    if (favSortNewestFirst) {
                                        refreshed.sortedByDescending {
                                            it.createdAt
                                        }
                                    } else {
                                        refreshed.sortedBy { it.createdAt }
                                    }
                            } else {
                                Log.w(TAG, "Cannot add favorite: no current position")
                            }
                        }
                    },
                )
            }
        }
    }

    private fun showRoutesFloatingView() {
        serviceScope.launch {
            val routeSortNewestFirst = settingsRepository.getRoutesSortNewestFirst().first()
            val rawRoutes = routeRepository.getRoutes().first()
            routesDataFlow.value =
                if (routeSortNewestFirst) rawRoutes.sortedByDescending { it.createdAt } else rawRoutes.sortedBy { it.createdAt }
            showPanel(logTag = "routes") {
                val routes by routesDataFlow.collectAsStateWithLifecycle()
                RoutesFloatingView(
                    routes = routes,
                    onDismiss = { hidePanelView() },
                    onStartRoute = { routeId, isLooping, isReverse, isReturnToLocation, teleportToStart ->
                        startRouteReplayWithMode(routeId, isLooping, isReverse, isReturnToLocation, teleportToStart)
                        moveAppToBack()
                    },
                )
            }
        }
    }

    private fun onFeatureButtonClicked(feature: WidgetFeature) {
        when (feature) {
            WidgetFeature.JOYSTICK_TOGGLE -> {
                toggleJoystick()
            }

            WidgetFeature.JOYSTICK_LOCK -> {
                toggleJoystickLock()
            }

            WidgetFeature.ROUTES_FLOATING -> {
                onRouteIconClicked()
            }

            WidgetFeature.FAVORITES_FLOATING -> {
                showFavoritesFloatingView()
            }

            WidgetFeature.SPEED_CYCLE -> {
                cycleSpeedProfile()
            }

            WidgetFeature.MAP_FLOATING -> {
                showMapFloatingView()
            }

            WidgetFeature.ELEVATION_CONTROLS -> { /* handled by sub-buttons */ }
        }
    }

    private fun onElevationModeSelected(mode: ElevationMode) {
        elevationModeFlow.value = mode
        mockLocationService?.setElevationMode(mode)
    }

    private fun onRouteIconClicked() {
        val mode = locationRepository.currentMode.value
        val isActive =
            mode == com.locationjoystick.core.model.MockMode.ROUTE_REPLAY ||
                mode == com.locationjoystick.core.model.MockMode.ROAMING ||
                mode == com.locationjoystick.core.model.MockMode.WALK_TO
        if (isActive) {
            routeExpandedFlow.value = !routeExpandedFlow.value
        } else {
            showRoutesFloatingView()
        }
    }

    private fun onRoutePauseResumeClicked() {
        when (locationRepository.currentMode.value) {
            com.locationjoystick.core.model.MockMode.WALK_TO -> {
                locationRepository.setWalkPaused(!locationRepository.isWalkPaused.value)
            }

            com.locationjoystick.core.model.MockMode.ROUTE_REPLAY -> {
                val paused = locationRepository.mockLocationState.value == com.locationjoystick.core.model.MockLocationState.PAUSED
                if (paused) {
                    serviceScope.launch {
                        val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                        startService(MockLocationIntentBuilder.resumeRouteReplay(this@FloatingWidgetService, speedMs))
                    }
                } else {
                    startService(MockLocationIntentBuilder.pauseRouteReplay(this@FloatingWidgetService))
                }
            }

            com.locationjoystick.core.model.MockMode.ROAMING -> {
                if (roamingRepository.isRoamingPaused.value) {
                    serviceScope.launch { roamingRepository.resumeRoaming() }
                } else {
                    serviceScope.launch { roamingRepository.pauseRoaming() }
                }
            }

            else -> {
                Unit
            }
        }
    }

    private fun onRouteStopClicked() {
        routeExpandedFlow.value = false
        when (locationRepository.currentMode.value) {
            com.locationjoystick.core.model.MockMode.ROAMING -> {
                serviceScope.launch { roamingRepository.stopRoaming() }
            }

            com.locationjoystick.core.model.MockMode.WALK_TO -> {
                walkCoordinator.cancel()
            }

            else -> {
                startService(MockLocationIntentBuilder.stopRouteReplay(this))
            }
        }
    }

    private fun teleportToFavorite(favorite: FavoriteLocation) {
        val svc = mockLocationService
        if (svc != null) {
            svc.updatePosition(favorite.position.latitude, favorite.position.longitude)
            Log.d(TAG, "Teleported to favorite: ${favorite.name}")
        } else {
            locationRepository.updatePosition(favorite.position)
            Log.d(TAG, "Teleported to favorite via repository: ${favorite.name}")
        }
    }

    private fun startWalkToFavorite(favorite: FavoriteLocation) {
        walkCoordinator.startWalk(favorite.position, serviceScope) { newPos, speedMs, bearing ->
            startService(
                MockLocationIntentBuilder.updatePosition(this@FloatingWidgetService, newPos.latitude, newPos.longitude, speedMs, bearing),
            )
        }
    }

    private fun startWalkViaRoadsToFavorite(favorite: FavoriteLocation) {
        serviceScope.launch {
            val current = locationRepository.currentPosition.value
            if (current == null) {
                startWalkToFavorite(favorite)
                return@launch
            }
            val waypoints =
                osrmClient
                    .getRoute(com.locationjoystick.core.routing.OsrmClient.PROFILE_FOOT, listOf(current, favorite.position))
                    .getOrNull()
            if (waypoints.isNullOrEmpty()) {
                Log.w(TAG, "OSRM failed for favorite walk; falling back to straight line")
                startWalkToFavorite(favorite)
                return@launch
            }
            walkCoordinator.startWalkAlongRoute(waypoints, serviceScope) { newPos, speedMs, bearing ->
                startService(
                    MockLocationIntentBuilder.updatePosition(
                        this@FloatingWidgetService,
                        newPos.latitude,
                        newPos.longitude,
                        speedMs,
                        bearing,
                    ),
                )
            }
        }
    }

    private fun startRouteReplayWithMode(
        routeId: String,
        isLooping: Boolean = false,
        isReverse: Boolean = false,
        isReturnToLocation: Boolean = false,
        teleportToStart: Boolean = false,
    ) {
        serviceScope.launch {
            val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
            val returnPosition = if (isReturnToLocation) locationRepository.currentPosition.value else null
            if (teleportToStart) {
                val route = routeRepository.getRoutes().first().find { it.id == routeId }
                val waypoints = route?.waypoints
                if (!waypoints.isNullOrEmpty()) {
                    val startWaypoint = if (isReverse) waypoints.last() else waypoints.first()
                    startService(
                        Intent(this@FloatingWidgetService, MockLocationService::class.java).apply {
                            action = MockLocationService.ACTION_UPDATE_POSITION
                            putExtra(AppConstants.ServiceConstants.EXTRA_LAT, startWaypoint.position.latitude)
                            putExtra(AppConstants.ServiceConstants.EXTRA_LON, startWaypoint.position.longitude)
                        },
                    )
                }
            }
            val intent =
                MockLocationIntentBuilder
                    .startRouteReplay(this@FloatingWidgetService, routeId, speedMs, isReverse)
                    .apply {
                        putExtra(MockLocationService.EXTRA_IS_LOOPING, isLooping)
                        if (returnPosition != null) {
                            putExtra(MockLocationService.EXTRA_RETURN_LAT, returnPosition.latitude)
                            putExtra(MockLocationService.EXTRA_RETURN_LON, returnPosition.longitude)
                        }
                    }
            startService(intent)
        }
    }

    private fun toggleJoystick() {
        val svc =
            joystickService ?: run {
                Log.w(TAG, "Cannot toggle joystick: service not bound")
                return
            }
        try {
            svc.toggleOverlay()
            Log.d(TAG, "Toggled joystick overlay visibility")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle joystick overlay", e)
        }
    }

    private fun toggleJoystickLock() {
        val svc = joystickService
        if (svc != null) {
            try {
                val newLocked = !svc.isLocked.value
                svc.setIsLocked(newLocked)
                joystickLockedFlow.value = newLocked
                Log.d(TAG, "Toggled joystick lock to: $newLocked")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle lock", e)
            }
        } else {
            Log.w(TAG, "Cannot toggle joystick lock: service not bound")
        }
    }

    private fun cycleSpeedProfile() {
        serviceScope.launch {
            val profiles = settingsRepository.getSpeedProfiles().first()
            if (profiles.isEmpty()) {
                Log.w(TAG, "cycleSpeedProfile: no profiles available, skipping")
                return@launch
            }
            val active = settingsRepository.getActiveSpeedProfile().first()
            val currentIndex = profiles.indexOfFirst { it.id == active.id }
            if (currentIndex == -1) {
                Log.w(TAG, "cycleSpeedProfile: active profile not found in list, resetting to first")
            }
            val nextIndex = (currentIndex + 1) % profiles.size
            settingsRepository.setActiveProfileId(profiles[nextIndex].id)
            Log.d(TAG, "Cycled speed profile to: ${profiles[nextIndex].id}")
        }
    }

    private fun showMapFloatingView() {
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
                    com.locationjoystick.core.model.RoamingDefaults(
                        radiusMeters = 1000.0,
                        distanceMeters = 200.0,
                        speedProfileId = "walk",
                        followRoads = false,
                        returnToInitialLocation = false,
                    ),
            )
            val speedUnit by remember {
                settingsRepository.getSpeedUnit()
            }.collectAsStateWithLifecycle(initialValue = com.locationjoystick.core.model.SpeedUnit.KMH)
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
                    val svc = mockLocationService
                    if (svc != null) {
                        svc.updatePosition(pos.latitude, pos.longitude)
                    } else {
                        locationRepository.updatePosition(pos)
                    }
                    moveAppToBack()
                },
                onWalkTo = { pos ->
                    walkCoordinator.startWalk(pos, serviceScope) { newPos, speedMs, bearing ->
                        startService(
                            MockLocationIntentBuilder.updatePosition(
                                this@FloatingWidgetService,
                                newPos.latitude,
                                newPos.longitude,
                                speedMs,
                                bearing,
                            ),
                        )
                    }
                    moveAppToBack()
                },
                onStopRouteAndTeleport = { pos ->
                    sendReplayCancel()
                    val svc = mockLocationService
                    if (svc != null) {
                        svc.updatePosition(pos.latitude, pos.longitude)
                    } else {
                        locationRepository.updatePosition(pos)
                    }
                    moveAppToBack()
                },
                onStopRouteAndWalkTo = { pos ->
                    sendReplayCancel()
                    walkCoordinator.startWalk(pos, serviceScope) { newPos, speedMs, bearing ->
                        startService(
                            MockLocationIntentBuilder.updatePosition(
                                this@FloatingWidgetService,
                                newPos.latitude,
                                newPos.longitude,
                                speedMs,
                                bearing,
                            ),
                        )
                    }
                    moveAppToBack()
                },
                onFinishRouteAndWalkTo = { pos ->
                    sendAppendWaypoint(pos)
                    moveAppToBack()
                },
                onAddEphemeralWaypoint = { pos ->
                    sendAddEphemeralWaypoint(pos)
                    moveAppToBack()
                },
                onStartRoaming = { defaults -> startRoamingWith(defaults) },
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
            )
        }
    }

    private fun startRoamingWithDefaults() {
        serviceScope.launch {
            try {
                val pos = locationRepository.currentPosition.value
                if (pos == null) {
                    Log.w(TAG, "Cannot start roaming: no current position")
                    return@launch
                }
                val defaults = settingsRepository.getRoamingDefaults().first()
                val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                val config =
                    RoamingConfig(
                        centerPosition = pos,
                        radiusMeters = defaults.radiusMeters,
                        distanceMeters = defaults.distanceMeters,
                        speedProfileId = defaults.speedProfileId,
                        useRoadSnapping = defaults.followRoads,
                        returnToInitialLocation = defaults.returnToInitialLocation,
                    )
                roamingRepository.startRoaming(config, speedMs)
                Log.d(TAG, "Started roaming with defaults")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start roaming", e)
            }
        }
    }

    private fun startRoamingWith(defaults: com.locationjoystick.core.model.RoamingDefaults) {
        serviceScope.launch {
            try {
                val pos = locationRepository.currentPosition.value
                if (pos == null) {
                    Log.w(TAG, "Cannot start roaming: no current position")
                    return@launch
                }
                val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                val config =
                    RoamingConfig(
                        centerPosition = pos,
                        radiusMeters = defaults.radiusMeters,
                        distanceMeters = defaults.distanceMeters,
                        speedProfileId = defaults.speedProfileId,
                        useRoadSnapping = defaults.followRoads,
                        returnToInitialLocation = defaults.returnToInitialLocation,
                    )
                roamingRepository.startRoaming(config, speedMs)
                Log.d(TAG, "Started roaming with custom defaults")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start roaming", e)
            }
        }
    }

    private fun sendReplayCancel() {
        try {
            startService(MockLocationIntentBuilder.cancelRouteReplay(this))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send replay cancel", e)
        }
    }

    private fun sendAppendWaypoint(pos: com.locationjoystick.core.model.LatLng) {
        try {
            startService(MockLocationIntentBuilder.appendWaypoint(this, pos))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send append waypoint", e)
        }
    }

    private fun sendAddEphemeralWaypoint(pos: com.locationjoystick.core.model.LatLng) {
        serviceScope.launch {
            try {
                ephemeralReplayController.addWaypoint(
                    newPoint = pos,
                    currentWaypoints = emptyList(), // widget doesn't track local ephemeral list
                    walkStart = locationRepository.currentPosition.value,
                    walkTarget = locationRepository.walkTarget.value,
                    context = this@FloatingWidgetService,
                    launchIntent = { startService(it) },
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send add ephemeral waypoint", e)
            }
        }
    }

    private fun moveAppToBack() {
        try {
            val intent =
                Intent(this, Class.forName("com.locationjoystick.app.MainActivity")).apply {
                    action = "com.locationjoystick.app.ACTION_MOVE_TO_BACK"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            startActivity(intent)
            Log.d(TAG, "Sent move-to-back to MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send move-to-back", e)
        }
    }
}
