package com.locationjoystick.feature.widget.impl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.WidgetFeature
import com.locationjoystick.core.overlay.OverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FloatingWidgetService : OverlayService(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingWidgetService"
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

    private var panelLayout: LinearLayout? = null
    private val speedCycleButtons = mutableMapOf<ImageButton, WidgetFeature>()

    private var mockLocationService: MockLocationService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
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
        bindService(
            Intent(this, MockLocationService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
        lifecycleScope.launch {
            settingsRepository.getActiveSpeedProfile().collect { profile ->
                updateSpeedCycleButtonIcon(profile.id)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        panelLayout?.visibility = View.GONE
        panelLayout = null
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Service was not bound when attempting to unbind", e)
        }
        super.onDestroy()
    }

    override fun createOverlayView(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        panelLayout = panel

        serviceScope.launch {
            settingsRepository.getWidgetFeatures().collect { features ->
                rebuildPanel(panel, features)
            }
        }

        return panel
    }

    private fun rebuildPanel(panel: LinearLayout, features: List<WidgetFeature>) {
        panel.removeAllViews()
        speedCycleButtons.clear()
        if (features.isEmpty()) {
            val placeholder = TextView(this).apply {
                text = "No items configured"
            }
            panel.addView(placeholder)
        } else {
            features.forEach { feature ->
                val button = ImageButton(this).apply {
                    contentDescription = feature.toContentDescription()
                    setOnClickListener { onFeatureButtonClicked(feature) }
                }
                panel.addView(button)
                if (feature == WidgetFeature.SPEED_CYCLE) {
                    speedCycleButtons[button] = feature
                }
            }
        }
    }

    private fun onFeatureButtonClicked(feature: WidgetFeature) {
        when (feature) {
            WidgetFeature.SPEED_CYCLE -> cycleSpeedProfile()
            else -> Log.d(TAG, "Feature button clicked: $feature")
        }
    }

    private fun cycleSpeedProfile() {
        serviceScope.launch {
            val profiles = settingsRepository.getSpeedProfiles().collect { profiles ->
                val activeProfile = settingsRepository.getActiveSpeedProfile().collect { active ->
                    val currentIndex = profiles.indexOfFirst { it.id == active.id }
                    val nextIndex = (currentIndex + 1) % profiles.size
                    val nextProfileId = profiles[nextIndex].id
                    settingsRepository.setActiveProfileId(nextProfileId)
                    Log.d(TAG, "Cycled speed profile to: $nextProfileId")
                }
            }
        }
    }

    private fun updateSpeedCycleButtonIcon(profileId: String) {
        speedCycleButtons.keys.forEach { button ->
            val iconRes = when (profileId) {
                "walk" -> android.R.drawable.ic_menu_compass
                "run" -> android.R.drawable.ic_menu_directions
                "bike" -> android.R.drawable.ic_menu_gallery
                else -> android.R.drawable.ic_menu_compass
            }
            button.setImageResource(iconRes)
        }
    }

    private fun WidgetFeature.toContentDescription(): String = when (this) {
        WidgetFeature.JOYSTICK_TOGGLE -> "Show/hide joystick"
        WidgetFeature.JOYSTICK_LOCK -> "Lock joystick position"
        WidgetFeature.ROUTES_PICKER -> "Routes picker"
        WidgetFeature.FAVORITES_PICKER -> "Favorites picker"
        WidgetFeature.SPEED_CYCLE -> "Speed cycle"
    }

    private fun startRouteReplay(routeId: String, isBackward: Boolean) {
        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_ROUTE_REPLAY_START
            putExtra(MockLocationService.EXTRA_ROUTE_ID, routeId)
            putExtra(MockLocationService.EXTRA_IS_BACKWARD, isBackward)
            putExtra(MockLocationService.EXTRA_SPEED_MS, 1.4)
        }
        startService(intent)
    }
}
