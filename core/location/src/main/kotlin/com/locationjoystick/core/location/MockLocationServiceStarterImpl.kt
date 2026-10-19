package com.locationjoystick.core.location

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.locationjoystick.core.common.di.ApplicationScope
import com.locationjoystick.core.data.MockLocationServiceStarter
import com.locationjoystick.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MockLocationServiceStarter"

internal fun hasMockLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Singleton
class MockLocationServiceStarterImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : MockLocationServiceStarter {
        override fun start(intent: Intent) {
            if (!hasMockLocationPermission(context)) {
                Log.e(TAG, "Location permission not granted: posting error notification and resetting onboarding.")
                // The service creates this channel in onCreate, which never runs on this path.
                createMockLocationNotificationChannels(context)
                postMockLocationPermissionErrorNotification(context)
                appScope.launch { settingsRepository.setOnboardingComplete(false) }
                return
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
