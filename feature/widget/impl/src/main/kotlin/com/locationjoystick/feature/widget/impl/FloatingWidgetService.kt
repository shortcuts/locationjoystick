package com.locationjoystick.feature.widget.impl

import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.view.View
import androidx.compose.runtime.getValue
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
import com.locationjoystick.core.data.ActivityStateRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.MockLocationIntentBuilder
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.ElevationMode
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
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
import kotlinx.coroutines.launch
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

    @Inject lateinit var activityStateRepository: ActivityStateRepository

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var mapController: MapController

    private var composeView: ComposeView? = null

    // Joystick state
    private val joystickVisibleFlow = MutableStateFlow(false)
    private val joystickLockedFlow = MutableStateFlow(false)
    private val activeProfileIdFlow = MutableStateFlow("walk")

    // Activity state — driven entirely by locationRepository.currentMode via isActivityActive/isActivityPausable
    private val routeExpandedFlow = MutableStateFlow(false)

    // Master panel expand/collapse
    private val isPanelExpandedFlow = MutableStateFlow(false)

    private val elevationModeFlow = MutableStateFlow<ElevationMode?>(null)

    // Drag position — class-level so onConfigurationChanged can read them after rotation.
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private lateinit var serviceBinder: WidgetServiceBinder
    private lateinit var panelPresenter: WidgetPanelPresenter

    private val mockLocationService: MockLocationService?
        get() = serviceBinder.mockLocationService

    private val joystickService: JoystickOverlayService?
        get() = serviceBinder.joystickService

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        overlayHelper.registerOverlayVisibilityReceiver(this, this)
        serviceBinder =
            WidgetServiceBinder(
                context = this,
                serviceScope = serviceScope,
                overlayHelper = overlayHelper,
                joystickVisibleFlow = joystickVisibleFlow,
                joystickLockedFlow = joystickLockedFlow,
            )
        panelPresenter =
            WidgetPanelPresenter(
                context = this,
                windowManager = windowManager,
                lifecycleOwner = this,
                savedStateRegistryOwner = this,
                serviceScope = serviceScope,
                mapController = mapController,
                callbacks = panelCallbacks,
            )
        serviceBinder.bind()
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
        lifecycleScope.launch {
            settingsRepository.getWidgetFeatures().collect { features ->
                if (WidgetFeature.ELEVATION_CONTROLS !in features) setElevationMode(null)
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
        mapController.stopWalk()
        serviceScope.cancel()
        panelPresenter.hidePanelView()
        // Tear down the FAB composition so the Recomposer and any captured state holders are
        // released. The base OverlayService.onDestroy() removes the view from the WindowManager.
        composeView?.disposeComposition()
        composeView = null
        overlayHelper.cleanupOverlayBindings(this)
        serviceBinder.unbind()
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
            val isActivityPaused by activityStateRepository.isActivityPaused.collectAsStateWithLifecycle(initialValue = false)
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
                    onElevationModeSelected = { mode -> setElevationMode(mode) },
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
                panelPresenter.showFavoritesFloatingView()
            }

            WidgetFeature.SPEED_CYCLE -> {
                cycleSpeedProfile()
            }

            WidgetFeature.MAP_FLOATING -> {
                panelPresenter.showMapFloatingView()
            }

            WidgetFeature.ELEVATION_CONTROLS -> {
                Unit
            }
        }
    }

    private fun setElevationMode(mode: ElevationMode?) {
        elevationModeFlow.value = mode
        mockLocationService?.setElevationMode(mode)
    }

    private fun onRouteIconClicked() {
        val mode = mapController.sharedState.value.mockMode
        val isActive =
            mode == MockMode.ROUTE_REPLAY ||
                mode == MockMode.ROAMING ||
                mode == MockMode.WALK_TO
        if (isActive) {
            routeExpandedFlow.value = !routeExpandedFlow.value
        } else {
            panelPresenter.showRoutesFloatingView()
        }
    }

    private fun onRoutePauseResumeClicked() {
        when (mapController.sharedState.value.mockMode) {
            MockMode.WALK_TO -> {
                if (mapController.sharedState.value.isWalkPaused) mapController.resumeWalk() else mapController.pauseWalk()
            }

            MockMode.ROUTE_REPLAY -> {
                if (mapController.sharedState.value.mockLocationState == MockLocationState.PAUSED) {
                    mapController.resumeRouteReplay()
                } else {
                    mapController.pauseRouteReplay()
                }
            }

            MockMode.ROAMING -> {
                if (mapController.sharedState.value.isRoamingPaused) {
                    mapController.resumeRoaming()
                } else {
                    mapController.pauseRoaming()
                }
            }

            else -> {
                Unit
            }
        }
    }

    private fun onRouteStopClicked() {
        routeExpandedFlow.value = false
        when (mapController.sharedState.value.mockMode) {
            MockMode.ROAMING -> mapController.stopRoaming()
            MockMode.WALK_TO -> mapController.stopWalk()
            else -> mapController.stopRouteReplay()
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

    private val panelCallbacks =
        object : WidgetPanelPresenter.Callbacks {
            override fun teleportToFavorite(favorite: FavoriteLocation) = mapController.teleportTo(favorite.position)

            override fun startWalkToFavorite(favorite: FavoriteLocation) = mapController.walkTo(favorite.position)

            override fun startWalkViaRoadsToFavorite(favorite: FavoriteLocation) = mapController.walkViaRoads(favorite.position)

            override fun startRouteReplayWithMode(
                routeId: String,
                isLooping: Boolean,
                isReverse: Boolean,
                isReturnToLocation: Boolean,
                teleportToStart: Boolean,
            ) = mapController.startRouteReplay(routeId, isLooping, isReverse, isReturnToLocation, teleportToStart)

            override fun teleport(pos: LatLng) = mapController.teleportTo(pos)

            override fun walkTo(pos: LatLng) = mapController.walkTo(pos)

            override fun walkViaRoads(pos: LatLng) = mapController.walkViaRoads(pos)

            override fun stopRouteAndTeleport(pos: LatLng) {
                mapController.stopRouteOnly()
                mapController.teleportTo(pos)
            }

            override fun stopRouteAndWalkTo(pos: LatLng) {
                mapController.stopRouteOnly()
                mapController.walkTo(pos)
            }

            override fun finishRouteAndWalkTo(pos: LatLng) = mapController.appendWaypointToRoute(pos)

            override fun addEphemeralWaypoint(pos: LatLng) = mapController.addEphemeralWaypoint(pos)

            override fun startRoamingWith(defaults: RoamingDefaults) {
                val pos =
                    mapController.sharedState.value.currentPosition ?: run {
                        Log.w(TAG, "Cannot start roaming: no current position")
                        return
                    }
                mapController.startRoaming(defaults, pos)
            }

            override fun saveCurrentLocation(name: String) = mapController.saveCurrentLocation(name)

            override fun moveAppToBack() = this@FloatingWidgetService.moveAppToBack()
        }

    private fun moveAppToBack() {
        // Only send the move-to-back intent if the app is currently in the foreground.
        // When the user opened the widget from a different app, our process is not in the
        // foreground and startActivity would briefly launch MainActivity before it calls
        // moveTaskToBack(true), causing a visible flicker and leaving the user on the home
        // screen instead of returning to the app they came from.
        if (!isAppInForeground()) return
        try {
            val intent =
                Intent().apply {
                    setClassName(packageName, "com.locationjoystick.app.MainActivity")
                    action = "com.locationjoystick.app.ACTION_MOVE_TO_BACK"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            startActivity(intent)
            Log.d(TAG, "Sent move-to-back to MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send move-to-back", e)
        }
    }

    private fun isAppInForeground(): Boolean {
        val am = getSystemService(android.app.ActivityManager::class.java) ?: return false
        return am.runningAppProcesses?.any { proc ->
            proc.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                proc.pkgList.contains(packageName)
        } == true
    }
}
