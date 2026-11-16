package com.locationjoystick.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.locationjoystick.core.common.geocoding.Geocoding
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.data.HotLocationsRepository
import com.locationjoystick.core.data.HotRoutesRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.location.ExternalTeleportService
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.map.maplibre.MapTileHttp
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.followerModeEnabled
import com.locationjoystick.core.model.role
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LjApplication : Application() {
    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var hotLocationsRepository: HotLocationsRepository

    @Inject
    lateinit var favoriteRepository: FavoriteRepository

    @Inject
    lateinit var hotRoutesRepository: HotRoutesRepository

    @Inject
    lateinit var routeRepository: RouteRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MapTileHttp.install(this)
        Geocoding.installOfflineCities { assets.open("geonames/cities5000.tsv") }
        resumeActiveGroupRole()
        applicationScope.launch {
            settingsRepository.getDisabledGeocodingProviders().collect { Geocoding.disabled = it }
        }
        applicationScope.launch {
            settingsRepository.getExternalTeleportEnabled().distinctUntilChanged().collect(::applyExternalTeleportComponent)
        }
        applicationScope.launch {
            if (hotLocationsRepository.refreshIfStale()) {
                val s = settingsRepository.getSettingsSnapshot().first()
                if (s.hotLocationsEnabled) {
                    favoriteRepository.upsertHotLocations(hotLocationsRepository.locations.value, s.selectedHotLocationIds)
                }
            }
        }
        applicationScope.launch {
            if (hotRoutesRepository.refreshIfStale()) {
                val s = settingsRepository.getSettingsSnapshot().first()
                if (s.hotRoutesEnabled) {
                    routeRepository.upsertHotRoutes(hotRoutesRepository.routes.value, s.selectedHotRouteIds)
                }
            }
        }
    }

    private fun applyExternalTeleportComponent(enabled: Boolean) {
        val state =
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, ExternalTeleportService::class.java),
                state,
                PackageManager.DONT_KILL_APP,
            )
        } catch (e: RuntimeException) {
            Log.e("LjApplication", "external teleport component state not applied", e)
        }
    }

    // Device reboot means MockLocationService never even starts until the user opens it manually.
    // Sending a no-action intent triggers the service's existing OS-restart resume path
    // (MockLocationService.onStartCommand's `null ->` branch), so a follower/leader role persisted
    // in DataStore resumes in the background without requiring a visit to the Group Sync screen.
    private fun resumeActiveGroupRole() {
        applicationScope.launch {
            val state = groupRepository.groupState.first()
            val hasActiveRole =
                state.role == GroupRole.LEADER ||
                    (state.role == GroupRole.FOLLOWER && state.followerModeEnabled)
            if (hasActiveRole) {
                startService(Intent(this@LjApplication, MockLocationService::class.java))
            }
        }
    }
}
