package com.locationjoystick.core.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import com.locationjoystick.core.model.effectiveLaunchPackage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LaunchAfterLink"

/** Opens the app the user picked in Settings, else the guided path's game, once a map link was handled. */
@Singleton
class LaunchAfterLinkUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val captureRepository: CaptureCoordinatesRepository,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend fun launch() {
            val pkg =
                effectiveLaunchPackage(
                    captureRepository.launchAfterLinkPackage.first(),
                    captureRepository.launchAfterLinkChosen.first(),
                    settingsRepository.getGuidedPath().first(),
                ) { context.packageManager.getLaunchIntentForPackage(it) != null }
            if (pkg == null || pkg == context.packageName) return
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent == null) {
                Log.w(TAG, "$pkg is not launchable")
                return
            }
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Cannot launch $pkg", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Not allowed to launch $pkg", e)
            }
        }
    }
