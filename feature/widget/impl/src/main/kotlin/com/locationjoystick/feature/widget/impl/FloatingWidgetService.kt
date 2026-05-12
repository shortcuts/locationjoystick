package com.locationjoystick.feature.widget.impl

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.WidgetFeature
import com.locationjoystick.core.overlay.OverlayService
import com.locationjoystick.feature.joystick.impl.JoystickOverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@AndroidEntryPoint
class FloatingWidgetService :
    OverlayService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    companion object {
        private const val TAG = "FloatingWidgetService"
        private const val EARTH_RADIUS_M = 6371000.0
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    @Inject lateinit var routeRepository: RouteRepository

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var favoriteRepository: FavoriteRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    private var composeView: ComposeView? = null
    private var walkToJob: Job? = null
    private var joystickPollJob: Job? = null

    // Joystick state exposed to Compose
    private val _joystickVisible = MutableStateFlow(false)
    private val _joystickLocked = MutableStateFlow(false)
    private val _activeProfileId = MutableStateFlow("walk")

    private var mockLocationService: MockLocationService? = null

    // Persistent binding to JoystickOverlayService for state tracking
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

    private val overlayVisibilityReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_OVERLAY_HIDE -> hideOverlay()
                    ACTION_OVERLAY_SHOW -> showOverlay()
                }
            }
        }
    private val serviceConnection =
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
        val filter =
            IntentFilter().apply {
                addAction(ACTION_OVERLAY_HIDE)
                addAction(ACTION_OVERLAY_SHOW)
            }
        ContextCompat.registerReceiver(this, overlayVisibilityReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        bindService(
            Intent(this, MockLocationService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
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
        composeView?.visibility = View.GONE
        composeView = null
        try {
            unregisterReceiver(overlayVisibilityReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Overlay visibility receiver not registered", e)
        }
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Service was not bound when attempting to unbind", e)
        }
        if (joystickBound) {
            try {
                unbindService(joystickConnection)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Joystick service was not bound when attempting to unbind", e)
            }
        }
        super.onDestroy()
    }

    override fun createOverlayView(): View {
        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWidgetService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            }
        composeView = view

        view.setContent {
            val features by settingsRepository.getWidgetFeatures().collectAsState(initial = emptyList())
            val joystickVisible by _joystickVisible.collectAsState()
            val joystickLocked by _joystickLocked.collectAsState()
            val activeProfileId by _activeProfileId.collectAsState()

            MaterialTheme {
                WidgetPanel(
                    features = features,
                    joystickVisible = joystickVisible,
                    joystickLocked = joystickLocked,
                    activeProfileId = activeProfileId,
                    onFeatureClicked = { feature -> onFeatureButtonClicked(feature, view) },
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
        onFeatureClicked: (WidgetFeature) -> Unit,
    ) {
        if (features.isEmpty()) {
            // No-op: placeholder not rendered as overlay is collapsed when empty
            return
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            features.forEach { feature ->
                val (icon, active) = featureIconAndState(feature, joystickVisible, joystickLocked, activeProfileId)
                val activeColor = MaterialTheme.colorScheme.onSurface
                val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                IconButton(onClick = { onFeatureClicked(feature) }) {
                    Icon(
                        imageVector = icon,
                        contentDescription = feature.toContentDescription(),
                        tint = if (active) activeColor else inactiveColor,
                    )
                }
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
                Pair(Icons.Rounded.Explore, joystickVisible)
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

    private fun onFeatureButtonClicked(
        feature: WidgetFeature,
        anchor: View,
    ) {
        when (feature) {
            WidgetFeature.JOYSTICK_TOGGLE -> toggleJoystick()
            WidgetFeature.JOYSTICK_LOCK -> toggleJoystickLock()
            WidgetFeature.ROUTES_PICKER -> showRoutesPopup(anchor)
            WidgetFeature.FAVORITES_PICKER -> showFavoritesPopup(anchor)
            WidgetFeature.SPEED_CYCLE -> cycleSpeedProfile()
        }
    }

    private fun showFavoritesPopup(anchor: View) {
        serviceScope.launch {
            val favorites = favoriteRepository.getFavorites().first()

            withContext(Dispatchers.Main) {
                val listLayout =
                    LinearLayout(this@FloatingWidgetService).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 16, 16, 16)
                    }

                val popup =
                    PopupWindow(
                        ScrollView(this@FloatingWidgetService).apply { addView(listLayout) },
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true,
                    ).apply {
                        isOutsideTouchable = true
                        isFocusable = true
                    }

                if (favorites.isEmpty()) {
                    listLayout.addView(
                        TextView(this@FloatingWidgetService).apply {
                            text = "No favorites saved"
                            setPadding(8, 8, 8, 8)
                        },
                    )
                } else {
                    favorites.forEach { favorite ->
                        listLayout.addView(buildFavoriteRow(favorite) { popup.dismiss() })
                    }
                }

                popup.showAsDropDown(anchor)
            }
        }
    }

    private fun buildFavoriteRow(
        favorite: FavoriteLocation,
        onDismiss: () -> Unit,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 4, 8, 4)

            addView(
                TextView(this@FloatingWidgetService).apply {
                    text = favorite.name
                    setPadding(0, 0, 16, 0)
                },
            )

            addView(
                Button(this@FloatingWidgetService).apply {
                    text = "Teleport"
                    setOnClickListener {
                        teleportToFavorite(favorite)
                        onDismiss()
                    }
                },
            )

            addView(
                Button(this@FloatingWidgetService).apply {
                    text = "Walk"
                    setOnClickListener {
                        startWalkToFavorite(favorite)
                        onDismiss()
                    }
                },
            )
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
        walkToJob?.cancel()
        walkToJob =
            serviceScope.launch {
                try {
                    val speedMs = settingsRepository.getActiveSpeedProfile().first().speedMetersPerSecond
                    val targetLat = favorite.position.latitude
                    val targetLon = favorite.position.longitude

                    while (true) {
                        val current = locationRepository.currentPosition.value
                        if (current == null) {
                            Log.w(TAG, "No current position; stopping walk to ${favorite.name}")
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
                            Log.d(TAG, "Reached favorite: ${favorite.name}")
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
                    Log.e(TAG, "Walk to favorite interrupted", e)
                }
            }
    }

    private fun toggleJoystick() {
        val svc = joystickService
        if (svc != null) {
            try {
                svc.toggleOverlay()
                // Sync state after toggle (small delay for view attach/detach to propagate)
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

    private fun showRoutesPopup(anchor: View) {
        serviceScope.launch {
            val routes = routeRepository.getRoutes().first()

            withContext(Dispatchers.Main) {
                val listLayout =
                    LinearLayout(this@FloatingWidgetService).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(16, 16, 16, 16)
                    }

                val popup =
                    PopupWindow(
                        ScrollView(this@FloatingWidgetService).apply { addView(listLayout) },
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true,
                    ).apply {
                        isOutsideTouchable = true
                        isFocusable = true
                    }

                if (routes.isEmpty()) {
                    listLayout.addView(
                        TextView(this@FloatingWidgetService).apply {
                            text = "No routes saved"
                            setPadding(8, 8, 8, 8)
                        },
                    )
                } else {
                    routes.forEach { route ->
                        listLayout.addView(buildRouteRow(route) { popup.dismiss() })
                    }
                }

                popup.showAsDropDown(anchor)
            }
        }
    }

    private fun buildRouteRow(
        route: com.locationjoystick.core.model.Route,
        onDismiss: () -> Unit,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 4, 8, 4)

            addView(
                TextView(this@FloatingWidgetService).apply {
                    text = route.name
                    setPadding(0, 0, 16, 0)
                },
            )

            addView(
                Button(this@FloatingWidgetService).apply {
                    text = "Play"
                    setOnClickListener {
                        startRouteReplay(route.id, false)
                        onDismiss()
                    }
                },
            )

            addView(
                Button(this@FloatingWidgetService).apply {
                    text = "Reverse"
                    setOnClickListener {
                        startRouteReplay(route.id, true)
                        onDismiss()
                    }
                },
            )
        }

    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun calculateBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        return atan2(
            sin(dLon) * cos(lat2Rad),
            cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon),
        )
    }

    private fun advancePosition(
        lat: Double,
        lon: Double,
        bearing: Double,
        distanceM: Double,
    ): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val angularDist = distanceM / EARTH_RADIUS_M
        val newLatRad =
            Math.asin(
                sin(latRad) * cos(angularDist) +
                    cos(latRad) * sin(angularDist) * cos(bearing),
            )
        val newLonRad =
            lonRad +
                atan2(
                    sin(bearing) * sin(angularDist) * cos(latRad),
                    cos(angularDist) - sin(latRad) * sin(newLatRad),
                )
        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
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

    private fun WidgetFeature.toContentDescription(): String =
        when (this) {
            WidgetFeature.JOYSTICK_TOGGLE -> "Show/hide joystick"
            WidgetFeature.JOYSTICK_LOCK -> "Lock joystick position"
            WidgetFeature.ROUTES_PICKER -> "Routes picker"
            WidgetFeature.FAVORITES_PICKER -> "Favorites picker"
            WidgetFeature.SPEED_CYCLE -> "Speed cycle"
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
