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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.common.util.advancePosition
import com.locationjoystick.core.common.util.calculateBearing
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjText
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
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
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min
import android.view.WindowManager as AndroidWindowManager

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

    private var composeView: ComposeView? = null
    private var panelComposeView: ComposeView? = null
    private var walkToJob: Job? = null
    private var joystickPollJob: Job? = null

    // Joystick state
    private val _joystickVisible = MutableStateFlow(false)
    private val _joystickLocked = MutableStateFlow(false)
    private val _activeProfileId = MutableStateFlow("walk")

    // Route/walk-to active state
    private val _isRouteActive = MutableStateFlow(false)
    private val _isRoutePaused = MutableStateFlow(false)
    private val _routeExpanded = MutableStateFlow(false)

    // Master panel expand/collapse
    private val _isPanelExpanded = MutableStateFlow(false)

    // Floating panel data
    private val _favoritesData = MutableStateFlow<List<FavoriteLocation>>(emptyList())
    private val _routesData = MutableStateFlow<List<com.locationjoystick.core.model.Route>>(emptyList())

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
                _joystickVisible.value = false
                _joystickLocked.value = false
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
                _activeProfileId.value = profile.id
            }
        }
        lifecycleScope.launch {
            kotlinx.coroutines.flow
                .combine(
                    locationRepository.walkTarget,
                    locationRepository.activeRouteId,
                    locationRepository.mockLocationState,
                    locationRepository.isWalkPaused,
                ) { walkTarget, activeRouteId, state, walkPaused ->
                    val active = walkTarget != null || activeRouteId != null
                    val paused =
                        walkPaused ||
                            (activeRouteId != null && state == com.locationjoystick.core.model.MockLocationState.PAUSED)
                    active to paused
                }.collect { (active, paused) ->
                    if (!active) _routeExpanded.value = false
                    _isRouteActive.value = active
                    _isRoutePaused.value = paused
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
        walkToJob?.cancel()
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
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }

    override fun createOverlayView(): View {
        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWidgetService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            }
        composeView = view

        var dragOffsetX = 0f
        var dragOffsetY = 0f

        view.setContent {
            val features by settingsRepository.getWidgetFeatures().collectAsState(initial = emptyList())
            val joystickVisible by _joystickVisible.collectAsState()
            val joystickLocked by _joystickLocked.collectAsState()
            val activeProfileId by _activeProfileId.collectAsState()
            val isRouteActive by _isRouteActive.collectAsState()
            val isRoutePaused by _isRoutePaused.collectAsState()
            val routeExpanded by _routeExpanded.collectAsState()
            val isPanelExpanded by _isPanelExpanded.collectAsState()

            LjTheme {
                WidgetPanel(
                    features = features,
                    joystickVisible = joystickVisible,
                    joystickLocked = joystickLocked,
                    activeProfileId = activeProfileId,
                    isRouteActive = isRouteActive,
                    isRoutePaused = isRoutePaused,
                    routeExpanded = routeExpanded,
                    isPanelExpanded = isPanelExpanded,
                    onToggleMaster = { _isPanelExpanded.value = !_isPanelExpanded.value },
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
        isRouteActive: Boolean,
        isRoutePaused: Boolean,
        routeExpanded: Boolean,
        isPanelExpanded: Boolean,
        onToggleMaster: () -> Unit,
        onFeatureClicked: (WidgetFeature) -> Unit,
        onRouteClicked: () -> Unit,
        onRoutePauseResume: () -> Unit,
        onRouteStop: () -> Unit,
        onDrag: (dx: Float, dy: Float) -> Unit,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Master toggle icon — always visible; drag to reposition, tap to toggle panel
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .padding(4.dp)
                        .size(48.dp)
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
                    if (feature == WidgetFeature.ROUTES_PICKER) {
                        val routeIconTint = if (isRouteActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                        // Route icon + active controls in a horizontal row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .padding(4.dp)
                                        .size(48.dp)
                                        .background(Color.Black, CircleShape)
                                        .clickable { onRouteClicked() },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Route,
                                    contentDescription = "Routes picker",
                                    tint = routeIconTint,
                                    modifier = Modifier.size(25.dp),
                                )
                            }
                            // Pause/stop shown to the right when route active and expanded
                            if (isRouteActive && routeExpanded) {
                                val pauseResumeIcon = if (isRoutePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                                val pauseResumeTint = if (isRoutePaused) Color(0xFF4CAF50) else Color(0xFF757575)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .padding(4.dp)
                                            .size(48.dp)
                                            .background(Color.Black, CircleShape)
                                            .clickable { onRoutePauseResume() },
                                ) {
                                    Icon(
                                        imageVector = pauseResumeIcon,
                                        contentDescription = if (isRoutePaused) "Resume route" else "Pause route",
                                        tint = pauseResumeTint,
                                        modifier = Modifier.size(25.dp),
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .padding(4.dp)
                                            .size(48.dp)
                                            .background(Color.Black, CircleShape)
                                            .clickable { onRouteStop() },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = "Stop route",
                                        tint = Color(0xFFF44336),
                                        modifier = Modifier.size(25.dp),
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
                                    .size(48.dp)
                                    .background(Color.Black, CircleShape)
                                    .clickable { onFeatureClicked(feature) },
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = feature.toContentDescription(),
                                tint = iconTint,
                                modifier = Modifier.size(25.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FavoritesPanel(
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
                    Text(
                        text = "Favorites",
                        style = MaterialTheme.typography.titleLarge,
                        color = LjText,
                    )
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
    private fun RoutesPanel(
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
                    Text(
                        text = "Routes",
                        style = MaterialTheme.typography.titleLarge,
                        color = LjText,
                    )
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
            0,
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

    private fun showFavoritesPanel() {
        serviceScope.launch {
            _favoritesData.value = favoriteRepository.getFavorites().first()
            val panel =
                ComposeView(this@FloatingWidgetService).apply {
                    setViewTreeLifecycleOwner(this@FloatingWidgetService)
                    setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
                }
            panel.setContent {
                val favs by _favoritesData.collectAsState()
                LjTheme {
                    FavoritesPanel(
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
                                    _favoritesData.value = favoriteRepository.getFavorites().first()
                                } else {
                                    Log.w(TAG, "Cannot add favorite: no current position")
                                }
                            }
                        },
                    )
                }
            }
            hidePanelView()
            try {
                windowManager.addView(panel, panelLayoutParams())
                panelComposeView = panel
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show favorites panel", e)
            }
        }
    }

    private fun showRoutesPanel() {
        serviceScope.launch {
            _routesData.value = routeRepository.getRoutes().first()
            val panel =
                ComposeView(this@FloatingWidgetService).apply {
                    setViewTreeLifecycleOwner(this@FloatingWidgetService)
                    setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
                }
            panel.setContent {
                val routes by _routesData.collectAsState()
                LjTheme {
                    RoutesPanel(
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

            WidgetFeature.ROUTES_PICKER -> {
                Pair(Icons.Rounded.Route, true)
            }

            WidgetFeature.FAVORITES_PICKER -> {
                Pair(Icons.Rounded.Favorite, true)
            }

            WidgetFeature.SPEED_CYCLE -> {
                Pair(
                    when (activeProfileId) {
                        "run" -> Icons.AutoMirrored.Rounded.DirectionsRun
                        "bike" -> Icons.AutoMirrored.Rounded.DirectionsBike
                        else -> Icons.AutoMirrored.Rounded.DirectionsWalk
                    },
                    true,
                )
            }

            WidgetFeature.MAP -> {
                Pair(Icons.Rounded.LocationOn, true)
            }
        }

    private fun syncJoystickState() {
        val svc = joystickService ?: return
        _joystickVisible.value = svc.isOverlayVisible
        _joystickLocked.value = svc.locked
    }

    private fun startJoystickPolling() {
        joystickPollJob?.cancel()
        joystickPollJob =
            serviceScope.launch {
                while (true) {
                    delay(1000L)
                    syncJoystickState()
                }
            }
    }

    private fun onFeatureButtonClicked(feature: WidgetFeature) {
        when (feature) {
            WidgetFeature.JOYSTICK_TOGGLE -> toggleJoystick()
            WidgetFeature.JOYSTICK_LOCK -> toggleJoystickLock()
            WidgetFeature.ROUTES_PICKER -> showRoutesPanel()
            WidgetFeature.FAVORITES_PICKER -> showFavoritesPanel()
            WidgetFeature.SPEED_CYCLE -> cycleSpeedProfile()
            WidgetFeature.MAP -> openMap()
        }
    }

    private fun onRouteIconClicked() {
        if (_isRouteActive.value) {
            _routeExpanded.value = !_routeExpanded.value
        } else {
            showRoutesPanel()
        }
    }

    private fun onRoutePauseResumeClicked() {
        if (_isRoutePaused.value) {
            if (locationRepository.walkTarget.value != null) {
                locationRepository.setWalkPaused(false)
                resumeWalkToJob()
            } else {
                val intent =
                    Intent(this, MockLocationService::class.java).apply {
                        action = MockLocationService.ACTION_ROUTE_REPLAY_RESUME
                        putExtra(MockLocationService.EXTRA_SPEED_MS, 1.4)
                    }
                startService(intent)
            }
        } else {
            if (locationRepository.walkTarget.value != null) {
                pauseWalkToJob()
                locationRepository.setWalkPaused(true)
            } else {
                val intent =
                    Intent(this, MockLocationService::class.java).apply {
                        action = MockLocationService.ACTION_ROUTE_REPLAY_PAUSE
                    }
                startService(intent)
            }
        }
    }

    private fun onRouteStopClicked() {
        _routeExpanded.value = false
        if (locationRepository.walkTarget.value != null) {
            walkToJob?.cancel()
            walkToJob = null
            locationRepository.setWalkTarget(null)
        } else {
            val intent =
                Intent(this, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_ROUTE_REPLAY_STOP
                }
            startService(intent)
        }
    }

    private fun pauseWalkToJob() {
        walkToJob?.cancel()
        walkToJob = null
    }

    private fun resumeWalkToJob() {
        val target = locationRepository.walkTarget.value ?: return
        startWalkToPosition(target)
    }

    private fun startWalkToPosition(target: LatLng) {
        walkToJob?.cancel()
        walkToJob =
            serviceScope.launch {
                try {
                    val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                    val targetLat = target.latitude
                    val targetLon = target.longitude

                    while (true) {
                        val current = locationRepository.currentPosition.value
                        if (current == null) {
                            Log.w(TAG, "No current position; stopping walk to target")
                            break
                        }

                        val distanceM =
                            haversineDistance(
                                current.latitude,
                                current.longitude,
                                targetLat,
                                targetLon,
                            )
                        if (distanceM < 1.0) {
                            Log.d(TAG, "Reached walk target")
                            break
                        }

                        val bearing =
                            calculateBearing(
                                current.latitude,
                                current.longitude,
                                targetLat,
                                targetLon,
                            )
                        val advanceM = min(speedMs * 1.0, distanceM)
                        val (newLat, newLon) =
                            advancePosition(
                                current.latitude,
                                current.longitude,
                                bearing,
                                advanceM,
                            )

                        try {
                            val intent =
                                Intent(this@FloatingWidgetService, MockLocationService::class.java).apply {
                                    action = MockLocationService.ACTION_UPDATE_POSITION
                                    putExtra("lat", newLat)
                                    putExtra("lon", newLon)
                                }
                            startService(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Walk update failed", e)
                        }

                        delay(1000L)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Walk to position interrupted", e)
                } finally {
                    locationRepository.setWalkTarget(null)
                }
            }
    }

    private fun teleportToFavorite(favorite: FavoriteLocation) {
        val svc = mockLocationService
        if (svc != null) {
            svc.updatePosition(favorite.position.latitude, favorite.position.longitude)
            Log.d(TAG, "Teleported to favorite: ${favorite.name}")
        } else {
            serviceScope.launch {
                locationRepository.updatePosition(favorite.position)
                Log.d(TAG, "Teleported to favorite via repository: ${favorite.name}")
            }
        }
    }

    private fun startWalkToFavorite(favorite: FavoriteLocation) {
        locationRepository.setWalkTarget(favorite.position)
        startWalkToPosition(favorite.position)
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
                _joystickLocked.value = newLocked
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
            val active = settingsRepository.getActiveSpeedProfile().first()
            val currentIndex = profiles.indexOfFirst { it.id == active.id }
            val nextIndex = (currentIndex + 1) % profiles.size
            settingsRepository.setActiveProfileId(profiles[nextIndex].id)
            Log.d(TAG, "Cycled speed profile to: ${profiles[nextIndex].id}")
        }
    }

    private fun openMap() {
        try {
            val intent =
                Intent(this, Class.forName("com.locationjoystick.app.MainActivity")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("navigate_to_map", true)
                }
            startActivity(intent)
            Log.d(TAG, "Opened map screen")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open map screen", e)
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
            WidgetFeature.ROUTES_PICKER -> "Routes picker"
            WidgetFeature.FAVORITES_PICKER -> "Favorites picker"
            WidgetFeature.SPEED_CYCLE -> "Speed cycle"
            WidgetFeature.MAP -> "Open map"
        }

    private fun startRouteReplay(
        routeId: String,
        isBackward: Boolean,
    ) {
        val intent =
            Intent(this, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_ROUTE_REPLAY_START
                putExtra(MockLocationService.EXTRA_ROUTE_ID, routeId)
                putExtra(MockLocationService.EXTRA_IS_BACKWARD, isBackward)
                putExtra(MockLocationService.EXTRA_SPEED_MS, 1.4)
            }
        startService(intent)
    }
}
