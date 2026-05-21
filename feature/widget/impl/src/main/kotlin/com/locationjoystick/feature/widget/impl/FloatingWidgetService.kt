package com.locationjoystick.feature.widget.impl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
import com.locationjoystick.core.location.MockLocationIntentBuilder
import com.locationjoystick.core.location.MockLocationService
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private var composeView: ComposeView? = null
    private var panelComposeView: ComposeView? = null
    private var joystickPollJob: Job? = null

    // Joystick state
    private val joystickVisibleFlow = MutableStateFlow(false)
    private val joystickLockedFlow = MutableStateFlow(false)
    private val activeProfileIdFlow = MutableStateFlow("walk")

    // Activity state — driven entirely by locationRepository.currentMode via isActivityActive/isActivityPausable
    private val routeExpandedFlow = MutableStateFlow(false)

    // Master panel expand/collapse
    private val isPanelExpandedFlow = MutableStateFlow(false)

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
                joystickService = (binder as JoystickOverlayService.LocalBinder).getService()
                syncJoystickState()
                startJoystickPolling()
                Log.d(TAG, "Bound to JoystickOverlayService")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                joystickPollJob?.cancel()
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
        joystickPollJob?.cancel()
        serviceScope.cancel()
        hidePanelView()
        composeView?.visibility = View.GONE
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

    override fun getWindowManagerParams(view: View): AndroidWindowManager.LayoutParams =
        AndroidWindowManager
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
                y = resources.displayMetrics.heightPixels / 2
            }

    override fun createOverlayView(): View {
        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWidgetService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            }
        composeView = view

        var dragOffsetX = 0f
        var dragOffsetY = resources.displayMetrics.heightPixels / 2f

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
            val isActivityPaused =
                isWalkPaused ||
                    (
                        mockMode == com.locationjoystick.core.model.MockMode.ROUTE_REPLAY &&
                            mockLocationState == com.locationjoystick.core.model.MockLocationState.PAUSED
                    )
            val routeExpanded by routeExpandedFlow.collectAsStateWithLifecycle()
            val isPanelExpanded by isPanelExpandedFlow.collectAsStateWithLifecycle()

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
                    onToggleMaster = { isPanelExpandedFlow.value = !isPanelExpandedFlow.value },
                    onFeatureClicked = { feature -> onFeatureButtonClicked(feature) },
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

    @Composable
    private fun WidgetPanel(
        features: List<WidgetFeature>,
        joystickVisible: Boolean,
        joystickLocked: Boolean,
        activeProfileId: String,
        isActivityActive: Boolean,
        isActivityPaused: Boolean,
        isActivityPausable: Boolean,
        routeExpanded: Boolean,
        isPanelExpanded: Boolean,
        onToggleMaster: () -> Unit,
        onFeatureClicked: (WidgetFeature) -> Unit,
        onRouteClicked: () -> Unit,
        onRoutePauseResume: () -> Unit,
        onRouteStop: () -> Unit,
        onDrag: (dx: Float, dy: Float) -> Unit,
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            // Master toggle icon — always visible; drag to reposition, tap to toggle panel
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .padding(4.dp)
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .pointerInput(Unit) {
                            var isDragging = false
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    isDragging = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val drag = event.changes.firstOrNull() ?: break
                                        val delta = drag.position - drag.previousPosition
                                        if (delta != androidx.compose.ui.geometry.Offset.Zero) {
                                            isDragging = true
                                            onDrag(delta.x, delta.y)
                                            drag.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                    if (!isDragging) {
                                        onToggleMaster()
                                    }
                                }
                            }
                        },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_launcher),
                    contentDescription = if (isPanelExpanded) "Collapse widget" else "Expand widget",
                    modifier = Modifier.size(25.dp),
                )
            }

            // Feature icons — only shown when panel expanded
            if (isPanelExpanded) {
                features.forEach { feature ->
                    if (feature == WidgetFeature.ROUTES_FLOATING) {
                        val routeIconTint = if (isActivityActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                        // Route icon + active controls in a horizontal row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .padding(4.dp)
                                        .size(36.dp)
                                        .background(Color.Black, CircleShape)
                                        .clickable { onRouteClicked() },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Route,
                                    contentDescription = "Routes picker",
                                    tint = routeIconTint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            // Pause/stop shown to the right when activity active and expanded
                            if (isActivityActive && routeExpanded) {
                                if (isActivityPausable) {
                                    val pauseResumeIcon = if (isActivityPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                                    val pauseResumeTint = if (isActivityPaused) Color(0xFF4CAF50) else Color(0xFF757575)
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier =
                                            Modifier
                                                .padding(4.dp)
                                                .size(36.dp)
                                                .background(Color.Black, CircleShape)
                                                .clickable { onRoutePauseResume() },
                                    ) {
                                        Icon(
                                            imageVector = pauseResumeIcon,
                                            contentDescription = if (isActivityPaused) "Resume" else "Pause",
                                            tint = pauseResumeTint,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .padding(4.dp)
                                            .size(36.dp)
                                            .background(Color.Black, CircleShape)
                                            .clickable { onRouteStop() },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = "Stop",
                                        tint = Color(0xFFF44336),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        val (icon, active) = featureIconAndState(feature, joystickVisible, joystickLocked, activeProfileId)
                        val iconTint = if (active) MaterialTheme.colorScheme.primary else Color(0xFF757575)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .padding(4.dp)
                                    .size(36.dp)
                                    .background(Color.Black, CircleShape)
                                    .clickable { onFeatureClicked(feature) },
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = feature.toContentDescription(),
                                tint = iconTint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FavoritesFloatingView(
        favorites: List<FavoriteLocation>,
        onDismiss: () -> Unit,
        onTeleport: (FavoriteLocation) -> Unit,
        onWalk: (FavoriteLocation) -> Unit,
        onAddFromHere: (name: String) -> Unit,
    ) {
        var showAddForm by remember { mutableStateOf(false) }
        var newFavName by remember { mutableStateOf("") }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { onDismiss() },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .background(LjBg, RoundedCornerShape(16.dp))
                        .clickable { /* consume touches inside panel */ },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Favorites",
                            style = MaterialTheme.typography.titleLarge,
                            color = LjText,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = LjText)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (favorites.isEmpty()) {
                            Text(
                                text = "No favorites saved",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LjText.copy(alpha = 0.6f),
                            )
                        } else {
                            LazyColumn {
                                items(favorites, key = { it.id }) { fav ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = fav.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = LjText,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(onClick = {
                                            onTeleport(fav)
                                            onDismiss()
                                        }) {
                                            Text("Teleport")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = {
                                            onWalk(fav)
                                            onDismiss()
                                        }) {
                                            Text("Walk")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (showAddForm) {
                        OutlinedTextField(
                            value = newFavName,
                            onValueChange = { newFavName = it },
                            label = { Text("Name", color = LjText) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        if (newFavName.isNotBlank()) {
                                            onAddFromHere(newFavName.trim())
                                            newFavName = ""
                                            showAddForm = false
                                        }
                                    },
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TextButton(onClick = {
                                showAddForm = false
                                newFavName = ""
                            }) {
                                Text("Cancel", color = LjText)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newFavName.isNotBlank()) {
                                        onAddFromHere(newFavName.trim())
                                        newFavName = ""
                                        showAddForm = false
                                    }
                                },
                            ) {
                                Text("Save")
                            }
                        }
                    } else {
                        Button(
                            onClick = { showAddForm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add from current location")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RoutesFloatingView(
        routes: List<com.locationjoystick.core.model.Route>,
        onDismiss: () -> Unit,
        onStart: (routeId: String) -> Unit,
        onStartReverse: (routeId: String) -> Unit,
        onCreateFromMap: () -> Unit,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { onDismiss() },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .background(LjBg, RoundedCornerShape(16.dp))
                        .clickable { /* consume touches inside panel */ },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Routes",
                            style = MaterialTheme.typography.titleLarge,
                            color = LjText,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = LjText)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (routes.isEmpty()) {
                            Text(
                                text = "No routes saved",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LjText.copy(alpha = 0.6f),
                            )
                        } else {
                            LazyColumn {
                                items(routes, key = { it.id }) { route ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = route.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = LjText,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(onClick = {
                                            onStart(route.id)
                                            onDismiss()
                                        }) {
                                            Text("Play")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = {
                                            onStartReverse(route.id)
                                            onDismiss()
                                        }) {
                                            Text("Reverse")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onCreateFromMap()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create route from map")
                    }
                }
            }
        }
    }

    private fun panelLayoutParams() =
        AndroidWindowManager.LayoutParams(
            AndroidWindowManager.LayoutParams.MATCH_PARENT,
            AndroidWindowManager.LayoutParams.MATCH_PARENT,
            AndroidWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE is mandatory: prevents stealing keyboard focus from games/apps
            // running behind the overlay panel. FLAG_NOT_TOUCH_MODAL limits touch interception
            // to the panel bounds only.
            AndroidWindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
        }
        panelComposeView = null
    }

    private fun showFavoritesFloatingView() {
        serviceScope.launch {
            favoritesDataFlow.value = favoriteRepository.getFavorites().first()
            val panel =
                ComposeView(this@FloatingWidgetService).apply {
                    setViewTreeLifecycleOwner(this@FloatingWidgetService)
                    setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
                }
            panel.setContent {
                val favs by favoritesDataFlow.collectAsStateWithLifecycle()
                LjTheme {
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
                        onAddFromHere = { name ->
                            serviceScope.launch {
                                val pos = locationRepository.currentPosition.value
                                if (pos != null) {
                                    favoriteRepository.addFavorite(
                                        id = UUID.randomUUID().toString(),
                                        name = name,
                                        position = pos,
                                    )
                                    favoritesDataFlow.value = favoriteRepository.getFavorites().first()
                                } else {
                                    Log.w(TAG, "Cannot add favorite: no current position")
                                }
                            }
                        },
                    )
                }
            }
            hidePanelView()
            // Use isActive instead of a lifecycle-state snapshot to avoid a TOCTOU race:
            // isActive reflects scope cancellation atomically, while checking lifecycle.currentState
            // has a window between the check and addView where the service can be destroyed.
            if (!isActive) {
                Log.w(TAG, "Service destroyed before favorites panel could be shown")
                return@launch
            }
            try {
                windowManager.addView(panel, panelLayoutParams())
                panelComposeView = panel
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show favorites panel", e)
            }
        }
    }

    private fun showRoutesFloatingView() {
        serviceScope.launch {
            routesDataFlow.value = routeRepository.getRoutes().first()
            val panel =
                ComposeView(this@FloatingWidgetService).apply {
                    setViewTreeLifecycleOwner(this@FloatingWidgetService)
                    setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
                }
            panel.setContent {
                val routes by routesDataFlow.collectAsStateWithLifecycle()
                LjTheme {
                    RoutesFloatingView(
                        routes = routes,
                        onDismiss = { hidePanelView() },
                        onStart = { routeId ->
                            startRouteReplay(routeId, false)
                            moveAppToBack()
                        },
                        onStartReverse = { routeId ->
                            startRouteReplay(routeId, true)
                            moveAppToBack()
                        },
                        onCreateFromMap = { openRouteCreator() },
                    )
                }
            }
            hidePanelView()
            if (!isActive) {
                Log.w(TAG, "Service destroyed before routes panel could be shown")
                return@launch
            }
            try {
                windowManager.addView(panel, panelLayoutParams())
                panelComposeView = panel
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show routes panel", e)
            }
        }
    }

    private fun featureIconAndState(
        feature: WidgetFeature,
        joystickVisible: Boolean,
        joystickLocked: Boolean,
        activeProfileId: String,
    ): Pair<ImageVector, Boolean> =
        when (feature) {
            WidgetFeature.JOYSTICK_TOGGLE -> {
                Pair(Icons.Rounded.Visibility, joystickVisible)
            }

            WidgetFeature.JOYSTICK_LOCK -> {
                Pair(
                    if (joystickLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    joystickLocked,
                )
            }

            WidgetFeature.ROUTES_FLOATING -> {
                Pair(Icons.Rounded.Route, true)
            }

            WidgetFeature.FAVORITES_FLOATING -> {
                Pair(Icons.Rounded.Favorite, true)
            }

            WidgetFeature.SPEED_CYCLE -> {
                Pair(
                    when (activeProfileId) {
                        AppConstants.ProfileConstants.PROFILE_ID_RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
                        AppConstants.ProfileConstants.PROFILE_ID_BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
                        else -> Icons.AutoMirrored.Rounded.DirectionsWalk
                    },
                    true,
                )
            }

            WidgetFeature.MAP_FLOATING -> {
                Pair(Icons.Rounded.LocationOn, true)
            }
        }

    private fun syncJoystickState() {
        val svc = joystickService ?: return
        joystickVisibleFlow.value = svc.isOverlayVisible
        joystickLockedFlow.value = svc.locked
    }

    private fun startJoystickPolling() {
        joystickPollJob?.cancel()
        joystickPollJob =
            serviceScope.launch {
                while (isActive) {
                    delay(AppConstants.LocationConstants.UPDATE_INTERVAL_MS)
                    syncJoystickState()
                }
            }
    }

    private fun onFeatureButtonClicked(feature: WidgetFeature) {
        when (feature) {
            WidgetFeature.JOYSTICK_TOGGLE -> toggleJoystick()
            WidgetFeature.JOYSTICK_LOCK -> toggleJoystickLock()
            WidgetFeature.ROUTES_FLOATING -> onRouteIconClicked()
            WidgetFeature.FAVORITES_FLOATING -> showFavoritesFloatingView()
            WidgetFeature.SPEED_CYCLE -> cycleSpeedProfile()
            WidgetFeature.MAP_FLOATING -> showMapFloatingView()
        }
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
        val mode = locationRepository.currentMode.value
        val isPaused =
            locationRepository.isWalkPaused.value ||
                (
                    mode == com.locationjoystick.core.model.MockMode.ROUTE_REPLAY &&
                        locationRepository.mockLocationState.value == com.locationjoystick.core.model.MockLocationState.PAUSED
                )
        if (isPaused) {
            if (mode == com.locationjoystick.core.model.MockMode.WALK_TO) {
                locationRepository.setWalkPaused(false)
            } else {
                serviceScope.launch {
                    val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                    startService(MockLocationIntentBuilder.resumeRouteReplay(this@FloatingWidgetService, speedMs))
                }
            }
        } else {
            if (mode == com.locationjoystick.core.model.MockMode.WALK_TO) {
                locationRepository.setWalkPaused(true)
            } else {
                startService(MockLocationIntentBuilder.pauseRouteReplay(this@FloatingWidgetService))
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

    private fun toggleJoystick() {
        val svc = joystickService
        if (svc != null) {
            try {
                svc.toggleOverlay()
                serviceScope.launch {
                    delay(100L)
                    syncJoystickState()
                }
                Log.d(TAG, "Toggled joystick overlay visibility")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle joystick overlay", e)
            }
        } else {
            Log.w(TAG, "Cannot toggle joystick: service not bound")
        }
    }

    private fun toggleJoystickLock() {
        val svc = joystickService
        if (svc != null) {
            try {
                val newLocked = !svc.locked
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
        val panel =
            ComposeView(this@FloatingWidgetService).apply {
                setViewTreeLifecycleOwner(this@FloatingWidgetService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            }
        panel.setContent {
            LjTheme {
                MapFloatingView(
                    locationRepository = locationRepository,
                    favoriteRepository = favoriteRepository,
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
                    onStartRoaming = { startRoamingWithDefaults() },
                    onStopRoaming = {
                        serviceScope.launch {
                            roamingRepository.stopRoaming()
                        }
                    },
                    onDismiss = { hidePanelView() },
                    context = this@FloatingWidgetService,
                )
            }
        }
        hidePanelView()
        try {
            windowManager.addView(panel, panelLayoutParams())
            panelComposeView = panel
            Log.d(TAG, "Opened map panel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show map panel", e)
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

    private fun openRouteCreator() {
        try {
            val intent =
                Intent(this, Class.forName("com.locationjoystick.app.MainActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("navigate_to_route_creator", true)
                }
            startActivity(intent)
            Log.d(TAG, "Opened route creator")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open route creator", e)
        }
    }

    private fun moveAppToBack() {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val isAppForeground =
                am.runningAppProcesses?.any { proc ->
                    proc.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        proc.processName == packageName
                } ?: false

            if (isAppForeground) {
                val intent =
                    Intent(this, Class.forName("com.locationjoystick.app.MainActivity")).apply {
                        action = "com.locationjoystick.app.ACTION_MOVE_TO_BACK"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                startActivity(intent)
                Log.d(TAG, "App in foreground — sent move-to-back to MainActivity")
            } else {
                Log.d(TAG, "App already in background — no move-to-back needed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send move-to-back", e)
        }
    }

    private fun WidgetFeature.toContentDescription(): String =
        when (this) {
            WidgetFeature.JOYSTICK_TOGGLE -> "Show/hide joystick"
            WidgetFeature.JOYSTICK_LOCK -> "Lock joystick position"
            WidgetFeature.ROUTES_FLOATING -> "Routes picker"
            WidgetFeature.FAVORITES_FLOATING -> "Favorites picker"
            WidgetFeature.SPEED_CYCLE -> "Speed cycle"
            WidgetFeature.MAP_FLOATING -> "Open map"
        }

    private fun startRouteReplay(
        routeId: String,
        isBackward: Boolean,
    ) {
        serviceScope.launch {
            val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
            startService(MockLocationIntentBuilder.startRouteReplay(this@FloatingWidgetService, routeId, speedMs, isBackward))
        }
    }
}
