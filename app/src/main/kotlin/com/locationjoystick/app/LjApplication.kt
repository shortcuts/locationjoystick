package com.locationjoystick.app

import android.app.Application
import android.content.Intent
import com.locationjoystick.core.data.GroupRepository
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.model.GroupRole
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class LjApplication : Application() {
    @Inject
    lateinit var groupRepository: GroupRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        resumeActiveGroupRole()
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
