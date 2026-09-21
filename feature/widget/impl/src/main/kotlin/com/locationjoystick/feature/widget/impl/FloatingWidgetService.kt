package com.locationjoystick.feature.widget.impl

import android.content.Intent
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Toast
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
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.CooldownEngine
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.location.CompassHeadingSource
import com.locationjoystick.core.location.MapController
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.AppFeature
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.MockLocationState
import com.locationjoystick.core.model.MockMode
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.RouteStartConfig
import com.locationjoystick.core.model.ThemeMode
import com.locationjoystick.core.model.isRoutePlaying
import com.locationjoystick.core.model.shouldIgnoreJoystickInput
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.view.WindowManager as AndroidWindowManager

/** Keys for the panel's independent expand/collapse StateFlows, held by [PanelExpandFlows]. */
private enum class PanelExpandKey { ROUTE, ROAMING, PASTE_CAPTURE, GROUP_SYNC, ALTITUDE }

/**
 * Holds one collapsed/expanded [MutableStateFlow] per [PanelExpandKey], replacing five
 * near-identical fields that each tracked one panel section's expand state independently.
 */
private class PanelExpandFlows {
    private val flows: Map<PanelExpandKey, MutableStateFlow<Boolean>> =
        PanelExpandKey.entries.associateWith { MutableStateFlow(false) }

    fun flow(key: PanelExpandKey): StateFlow<Boolean> = flows.getValue(key)

    fun toggle(key: PanelExpandKey) {
        val f = flows.getValue(key)
        f.value = !f.value
    }

    fun collapse(key: PanelExpandKey) {
        flows.getValue(key).value = false
    }

    fun collapseAll() {
        flows.values.forEach { it.value = false }
    }
}

/**
 * Floating widget overlay service.
 *
 * Displays a compact FAB that expands to a panel with quick-access controls:
 * - Joystick toggle and lock
 * - Routes list with replay controls
 * - Favorites list with teleport buttons
 * - Speed profile switcher
 *
 * The widget is configured via [AppFeature] items stored in DataStore.
 * Each feature can be enabled/disabled independently in Settings.
 *
 * Lifecycle:
 * - Starts collapsed (FAB only)
 * - Tap expands to full panel
 * - Drag to reposition (persisted via WindowManager params)
 *
 * Requires SYSTEM_ALERT_WINDOW permission (enforced by [OverlayService]).
 *
 * @see AppFeature for available features
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

    @Inject lateinit var compassHeadingSource: CompassHeadingSource

    @Inject lateinit var groupRepository: GroupRepository

    @Inject lateinit var captureCoordinatesRepository: CaptureCoordinatesRepository

    @Inject lateinit var favoriteRepository: FavoriteRepository

    @Inject lateinit var routeRepository: RouteRepository

    private var composeView: ComposeView? = null

    // Joystick state
    private val joystickVisibleFlow = MutableStateFlow(false)
    private val joystickLockedFlow = MutableStateFlow(false)
    private val activeProfileIdFlow = MutableStateFlow("walk")
    private val profilesFlow = MutableStateFlow<List<com.locationjoystick.core.model.SpeedProfile>>(emptyList())

    private val stopPopupVisibleFlow = MutableStateFlow(false)

    // One expand/collapse StateFlow per panel section (route, roaming, paste/capture,
    // group sync, altitude override), keyed by PanelExpandKey.
    private val panelExpand = PanelExpandFlows()

    // Master panel expand/collapse
    private val isPanelExpandedFlow = MutableStateFlow(false)

    // Red dot badge — set when a route/walk completes, cleared when panel is opened
    private val pendingCompletionFlow = MutableStateFlow(false)

    private val isTapToWalkActiveFlow = MutableStateFlow(false)
    private lateinit var tapToWalkScaleMpx: StateFlow<Double>
    private var tapToWalkOverlay: TapToWalkOverlay? = null

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
                settingsRepository = settingsRepository,
                captureRepository = captureCoordinatesRepository,
                favoriteRepository = favoriteRepository,
                routeRepository = routeRepository,
            )
        tapToWalkScaleMpx =
            settingsRepository
                .getTapToWalkScaleMpx()
                .stateIn(lifecycleScope, SharingStarted.Eagerly, AppConstants.TapToWalkConstants.DEFAULT_SCALE_MPX)
        serviceBinder.bind()
        if (isSpoofingActive()) {
            serviceBinder.bindJoystick()
        }
        lifecycleScope.launch {
            locationRepository.mockLocationState.collect { state ->
                when (state) {
                    MockLocationState.IDLE, MockLocationState.ERROR -> {
                        serviceBinder.unbindJoystick()
                        panelPresenter.hidePanelView()
                        dismissTapToWalkOverlay()
                        panelExpand.collapseAll()
                    }

                    MockLocationState.RUNNING, MockLocationState.PAUSED -> {
                        serviceBinder.bindJoystick()
                    }
                }
            }
        }
        lifecycleScope.launch {
            settingsRepository.getActiveSpeedProfile().collect { profile ->
                activeProfileIdFlow.value = profile.id
            }
        }
        lifecycleScope.launch {
            settingsRepository.getEnabledSpeedProfiles().collect { profiles ->
                profilesFlow.value = profiles
            }
        }
        lifecycleScope.launch {
            locationRepository.currentMode.collect { mode ->
                if (!routeControlsActive(mode)) panelExpand.collapse(PanelExpandKey.ROUTE)
                if (mode != MockMode.ROAMING) panelExpand.collapse(PanelExpandKey.ROAMING)
            }
        }
        lifecycleScope.launch {
            mapController.routingErrors.collect { msg ->
                Toast.makeText(this@FloatingWidgetService, msg, Toast.LENGTH_SHORT).show()
            }
        }
        lifecycleScope.launch {
            mapController.completionMessages.collect {
                pendingCompletionFlow.value = true
            }
        }
        // The FAB overlay window is FLAG_NOT_FOCUSABLE by design (never steal keyboard focus
        // from the foreground app), but that also blocks the altitude override field from ever
        // taking IME focus — so no keyboard appears and the field can't actually be typed into.
        // Borrow focus only while that field is expanded, per the same fix already applied to
        // the map panel's search field (mapPanelLayoutParams).
        lifecycleScope.launch {
            panelExpand.flow(PanelExpandKey.ALTITUDE).collect { expanded -> setOverlayFocusable(expanded) }
        }
        lifecycleScope.launch {
            groupRepository.teleportUnavailableEvent.collect {
                Toast
                    .makeText(
                        this@FloatingWidgetService,
                        getString(R.string.widget_panel_leader_position_not_yet_known),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
        lifecycleScope.launch {
            settingsRepository.getTapToWalkOverlayEnabled().collect { enabled ->
                if (!enabled) dismissTapToWalkOverlay()
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
        dismissTapToWalkOverlay()
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
            val currentMode by locationRepository.currentMode.collectAsStateWithLifecycle(initialValue = MockMode.TELEPORT)
            val mockLocationState by locationRepository.mockLocationState.collectAsStateWithLifecycle(
                initialValue = MockLocationState.IDLE,
            )
            val isActivityPaused by activityStateRepository.isActivityPaused.collectAsStateWithLifecycle(initialValue = false)
            val routeExpanded by panelExpand.flow(PanelExpandKey.ROUTE).collectAsStateWithLifecycle()
            val roamingExpanded by panelExpand.flow(PanelExpandKey.ROAMING).collectAsStateWithLifecycle()
            val stopPopupVisible by stopPopupVisibleFlow.collectAsStateWithLifecycle()
            val pasteCaptureExpanded by panelExpand.flow(PanelExpandKey.PASTE_CAPTURE).collectAsStateWithLifecycle()
            val isPanelExpanded by isPanelExpandedFlow.collectAsStateWithLifecycle()
            val hasPendingCompletion by pendingCompletionFlow.collectAsStateWithLifecycle()
            val isTapToWalkEnabled by settingsRepository.getTapToWalkOverlayEnabled().collectAsStateWithLifecycle(initialValue = false)
            val isTapToWalkActive by isTapToWalkActiveFlow.collectAsStateWithLifecycle()
            val groupState by groupRepository.groupState.collectAsStateWithLifecycle(initialValue = GroupState())
            val isGroupSyncExpanded by panelExpand.flow(PanelExpandKey.GROUP_SYNC).collectAsStateWithLifecycle()
            val hideTeleportFeatures by settingsRepository.getHideTeleportFeatures().collectAsStateWithLifecycle(initialValue = false)
            val showRouteJumpButtons by settingsRepository.getShowRouteJumpButtons().collectAsStateWithLifecycle(
                initialValue = AppConstants.ProfileConstants.SHOW_ROUTE_JUMP_BUTTONS_DEFAULT,
            )
            val isAltitudeOverrideButtonVisible by
                settingsRepository.getAltitudeOverrideButtonEnabled().collectAsStateWithLifecycle(initialValue = false)
            val isAltitudeExpanded by panelExpand.flow(PanelExpandKey.ALTITUDE).collectAsStateWithLifecycle()
            val reportedAltitudeMeters by locationRepository.reportedAltitudeMeters.collectAsStateWithLifecycle(initialValue = null)
            val debugStatsEnabled by settingsRepository.getDebugStatsEnabled().collectAsStateWithLifecycle(initialValue = false)
            val debugStats by locationRepository.debugStats.collectAsStateWithLifecycle(initialValue = null)
            val routeProgress by locationRepository.routeProgress.collectAsStateWithLifecycle(initialValue = null)

            val themeMode by settingsRepository.getThemeMode().collectAsStateWithLifecycle(
                initialValue = ThemeMode.DARK,
            )
            LjTheme(darkTheme = themeMode == ThemeMode.DARK) {
                val routeControls =
                    RouteControlsState(
                        expanded = routeExpanded,
                        isActive = routeControlsActive(currentMode),
                        isPaused = isActivityPaused,
                        isPausable = routeControlsActive(currentMode),
                        isReplay = currentMode == MockMode.ROUTE_REPLAY,
                        hideTeleportFeatures = hideTeleportFeatures,
                        showRouteJumpButtons = showRouteJumpButtons,
                        onIconClick = { onRouteIconClicked() },
                        onPauseResume = { onRoutePauseResumeClicked() },
                        onStop = { onRouteStopClicked() },
                        onJumpNext = { mapController.jumpToNextWaypoint() },
                        onJumpPrevious = { mapController.jumpToPreviousWaypoint() },
                    )

                val roamingControls =
                    RoamingControlsState(
                        expanded = roamingExpanded,
                        isActive = currentMode == MockMode.ROAMING,
                        isPaused = isActivityPaused,
                        onIconClick = { onRoamingIconClicked() },
                        onPauseResume = { onRoamingPauseResumeClicked() },
                        onStop = { onRoamingStopClicked() },
                    )

                val masterToggle =
                    MasterToggleState(
                        spoofingActive = mockLocationState != MockLocationState.IDLE,
                        stopPopupVisible = stopPopupVisible,
                        onToggle = {
                            stopPopupVisibleFlow.value = false
                            if (!isPanelExpandedFlow.value) pendingCompletionFlow.value = false
                            isPanelExpandedFlow.value = !isPanelExpandedFlow.value
                        },
                        onLongPress = { stopPopupVisibleFlow.value = !stopPopupVisibleFlow.value },
                        onPark = { parkSpoofingKeepWidget() },
                        onStart = {
                            stopPopupVisibleFlow.value = false
                            mapController.startSpoofing()
                        },
                        onStop = {
                            stopPopupVisibleFlow.value = false
                            mapController.stopSpoofing()
                            // Close immediately. After Pause the mock service is already IDLE, so
                            // its overlay collector will not run again; Stop still must dismiss us.
                            stopSelf()
                        },
                    )

                val pasteCapture =
                    PasteCaptureState(
                        expanded = pasteCaptureExpanded,
                        onLongPress = { panelExpand.toggle(PanelExpandKey.PASTE_CAPTURE) },
                        onCaptureShortcut = { openCaptureScreen() },
                    )

                val sections =
                    buildList {
                        if (isTapToWalkEnabled) {
                            add(WidgetPanelSection.TapToWalk(active = isTapToWalkActive, onClick = { onTapToWalkClicked() }))
                        }
                        if (groupState.role == GroupRole.FOLLOWER && groupState.followerModeEnabled && !hideTeleportFeatures) {
                            add(
                                WidgetPanelSection.GroupSync(
                                    expanded = isGroupSyncExpanded,
                                    onClick = { panelExpand.toggle(PanelExpandKey.GROUP_SYNC) },
                                    onTeleport = { teleportToLeaderNow() },
                                ),
                            )
                        }
                        if (isAltitudeOverrideButtonVisible) {
                            add(
                                WidgetPanelSection.AltitudeOverride(
                                    expanded = isAltitudeExpanded,
                                    prefillMeters =
                                        reportedAltitudeMeters ?: AppConstants.RealismConstants.DEFAULT_ALTITUDE_METERS,
                                    onClick = { panelExpand.toggle(PanelExpandKey.ALTITUDE) },
                                    onConfirm = { onConfirmAltitude(it) },
                                ),
                            )
                        }
                    }

                WidgetPanel(
                    features = features,
                    joystickVisible = joystickVisible,
                    joystickLocked = joystickLocked,
                    activeProfileId = activeProfileId,
                    routeControls = routeControls,
                    roamingControls = roamingControls,
                    joystickInputIgnored =
                        shouldIgnoreJoystickInput(
                            currentMode,
                            mockLocationState,
                            isRoamingPaused = currentMode == MockMode.ROAMING && isActivityPaused,
                        ),
                    roamingStartIgnored = isRoutePlaying(currentMode, mockLocationState),
                    isPanelExpanded = isPanelExpanded,
                    hasPendingCompletion = hasPendingCompletion,
                    masterToggle = masterToggle,
                    onFeatureClicked = { feature -> onFeatureButtonClicked(feature) },
                    pasteCapture = pasteCapture,
                    sections = sections,
                    debugStats = if (debugStatsEnabled) debugStats else null,
                    routeProgress = routeProgress,
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

    private fun parkSpoofingKeepWidget() {
        // Unbind before the mock-GPS service stops the joystick, otherwise BIND_AUTO_CREATE
        // would restart the overlay while the widget stays on screen.
        serviceBinder.unbindJoystick()
        panelPresenter.hidePanelView()
        dismissTapToWalkOverlay()
        mapController.parkSpoofingKeepWidget()
    }

    private fun isSpoofingActive(): Boolean {
        val state = locationRepository.mockLocationState.value
        return state != MockLocationState.IDLE && state != MockLocationState.ERROR
    }

    private fun onFeatureButtonClicked(feature: AppFeature) {
        if (!isSpoofingActive()) return
        panelExpand.collapse(PanelExpandKey.PASTE_CAPTURE)
        when (feature) {
            AppFeature.JOYSTICK_TOGGLE -> {
                toggleJoystick()
            }

            AppFeature.JOYSTICK_LOCK -> {
                toggleJoystickLock()
            }

            AppFeature.ROUTES -> {
                onRouteIconClicked()
            }

            AppFeature.FAVORITES -> {
                panelPresenter.showFavoritesFloatingView()
            }

            AppFeature.SPEED_CYCLE -> {
                cycleSpeedProfile()
            }

            AppFeature.MAP_FLOATING -> {
                panelPresenter.showMapFloatingView()
            }

            AppFeature.PASTE_COORDINATES -> {
                panelPresenter.showPasteCoordinatesFloatingView()
            }

            AppFeature.ROAMING -> {
                onRoamingIconClicked()
            }

            AppFeature.SEARCH, AppFeature.CAPTURE_COORDINATES -> {
                Unit
            }
        }
    }

    private fun openCaptureScreen() {
        panelExpand.collapse(PanelExpandKey.PASTE_CAPTURE)
        isPanelExpandedFlow.value = false
        panelPresenter.hidePanelView()
        try {
            startActivity(
                Intent().apply {
                    setClassName(packageName, "com.locationjoystick.app.MainActivity")
                    putExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_CAPTURE, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Capture", e)
        }
    }

    private fun onRoamingIconClicked() {
        if (!isSpoofingActive()) return
        if (mapController.sharedState.value.mockMode == MockMode.ROAMING) {
            panelExpand.toggle(PanelExpandKey.ROAMING)
        } else {
            panelPresenter.showRoamingFloatingView()
        }
    }

    private fun onRoamingPauseResumeClicked() {
        if (mapController.sharedState.value.isRoamingPaused) {
            mapController.resumeRoaming()
        } else {
            mapController.pauseRoaming()
        }
    }

    private fun onRoamingStopClicked() {
        panelExpand.collapse(PanelExpandKey.ROAMING)
        mapController.stopRoaming()
    }

    private fun onTapToWalkClicked() {
        if (!isSpoofingActive()) return
        if (tapToWalkOverlay?.isShowing() == true) {
            dismissTapToWalkOverlay()
        } else {
            val overlay =
                TapToWalkOverlay(
                    context = this,
                    windowManager = windowManager,
                    lifecycleOwner = this,
                    savedStateRegistryOwner = this,
                    onWalkTo = { pos -> mapController.walkTo(pos) },
                    getPosition = { mapController.sharedState.value.currentPosition },
                    getScaleMpx = { tapToWalkScaleMpx.value },
                    onDismissed = { isTapToWalkActiveFlow.value = false },
                    getHeadingAsync = { compassHeadingSource.captureHeading() },
                )
            tapToWalkOverlay = overlay
            isTapToWalkActiveFlow.value = true
            overlay.show()
        }
    }

    private fun dismissTapToWalkOverlay() {
        tapToWalkOverlay?.dismiss()
        tapToWalkOverlay = null
    }

    private fun onRouteIconClicked() {
        if (!isSpoofingActive()) return
        val mode = mapController.sharedState.value.mockMode
        if (routeControlsActive(mode)) {
            panelExpand.toggle(PanelExpandKey.ROUTE)
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

            else -> {
                Unit
            }
        }
    }

    private fun onRouteStopClicked() {
        panelExpand.collapse(PanelExpandKey.ROUTE)
        when (mapController.sharedState.value.mockMode) {
            MockMode.WALK_TO -> mapController.stopWalk()
            else -> mapController.stopRouteReplay()
        }
    }

    private fun teleportToLeaderNow() {
        val intent =
            Intent(this, MockLocationService::class.java).apply {
                action = AppConstants.ServiceConstants.ACTION_FOLLOWER_TELEPORT
            }
        startService(intent)
        // Advisory only — same cooldown clock as everywhere else, doesn't block the teleport
        // itself. The icon-only panel row has no room for a persistent badge like the Group
        // Sync screen's, so a warning here is a one-shot Toast instead.
        lifecycleScope.launch {
            val leaderPos = groupRepository.leaderPosition.value
            val currentPos = locationRepository.currentPosition.value
            if (leaderPos != null && currentPos != null) {
                val teleportTime = settingsRepository.getLastTeleportTime().first()
                val state = CooldownEngine.computeState(teleportTime, currentPos, leaderPos)
                if (state is CooldownState.Cooling) {
                    Toast
                        .makeText(
                            this@FloatingWidgetService,
                            getString(R.string.widget_panel_suggested_wait, state.toAdvisoryLabel()),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    private fun onConfirmAltitude(meters: Double) {
        lifecycleScope.launch { settingsRepository.setBaseAltitudeOverride(meters) }
        panelExpand.collapse(PanelExpandKey.ALTITUDE)
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val params = currentParams ?: return
        val view = overlayView ?: return
        params.flags =
            if (focusable) {
                params.flags and AndroidWindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                params.flags or AndroidWindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
        if (view.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update overlay focusability", e)
            }
        }
    }

    private fun toggleJoystick() {
        val svc = joystickService
        if (svc == null) {
            Log.w(TAG, "Joystick overlay not bound — starting it shown")
            startJoystickOverlayShown()
            return
        }
        try {
            if (widgetJoystickEyeShowsOverlay(svc.isOverlayVisible)) {
                svc.showJoystick()
            } else {
                svc.hideJoystick()
            }
            joystickVisibleFlow.value = svc.isOverlayVisible
            Log.d(TAG, "Toggled joystick overlay visibility to: ${svc.isOverlayVisible}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle joystick overlay", e)
        }
    }

    private fun startJoystickOverlayShown() {
        val intent =
            Intent().apply {
                setClassName(packageName, AppConstants.ServiceConstants.JOYSTICK_SERVICE_CLASS)
                putExtra(AppConstants.ServiceConstants.EXTRA_SHOW_OVERLAY, true)
            }
        try {
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start joystick overlay", e)
        }
    }

    private fun toggleJoystickLock() {
        val svc = joystickService
        if (svc != null) {
            try {
                val result = widgetJoystickLockResult(svc.isOverlayVisible, svc.isLocked.value)
                if (result.showOverlay) {
                    svc.showJoystick()
                }
                svc.setIsLocked(result.locked)
                joystickLockedFlow.value = result.locked
                Log.d(TAG, "Toggled joystick lock to: ${result.locked}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle lock", e)
            }
        } else {
            Log.w(TAG, "Joystick overlay not bound — starting it shown")
            startJoystickOverlayShown()
        }
    }

    private fun cycleSpeedProfile() {
        val profiles = profilesFlow.value
        if (profiles.isEmpty()) {
            Log.w(TAG, "cycleSpeedProfile: no profiles available, skipping")
            return
        }
        val activeId = activeProfileIdFlow.value
        val currentIndex = profiles.indexOfFirst { it.id == activeId }
        if (currentIndex == -1) {
            Log.w(TAG, "cycleSpeedProfile: active profile not found in list, resetting to first")
        }
        val nextIndex = (currentIndex + 1) % profiles.size
        serviceScope.launch { settingsRepository.setActiveProfileId(profiles[nextIndex].id) }
        Log.d(TAG, "Cycled speed profile to: ${profiles[nextIndex].id}")
    }

    private val panelCallbacks =
        object : WidgetPanelPresenter.Callbacks {
            override fun teleportToFavorite(favorite: FavoriteLocation) = mapController.teleportTo(favorite.position)

            override fun startWalkToFavorite(favorite: FavoriteLocation) = mapController.walkTo(favorite.position)

            override fun startWalkViaRoadsToFavorite(favorite: FavoriteLocation) = mapController.walkViaRoads(favorite.position)

            override fun startRouteReplayWithMode(
                routeId: String,
                config: RouteStartConfig,
            ) = mapController.startRouteReplay(routeId, config)

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

            override fun addEphemeralWaypoint(
                pos: LatLng,
                followRoads: Boolean,
            ) = mapController.addEphemeralWaypoint(pos, followRoads)

            override fun startRoamingWith(defaults: RoamingDefaults) {
                val pos =
                    mapController.sharedState.value.currentPosition ?: run {
                        Log.w(TAG, "Cannot start roaming: no current position")
                        return
                    }
                mapController.startRoaming(defaults, pos)
            }

            override fun saveCurrentLocation(name: String) = mapController.saveCurrentLocation(name)

            override fun saveFavorite(
                name: String,
                position: LatLng,
            ) = mapController.saveFavorite(name, position)

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
