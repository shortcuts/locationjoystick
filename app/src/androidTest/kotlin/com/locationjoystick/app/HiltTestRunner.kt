package com.locationjoystick.app

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun onStart() {
        // Wake screen and dismiss keyguard so activities can reach RESUMED state.
        uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        androidx.test.InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()

        // Reset DataStore so settings start fresh.
        uiAutomation
            .executeShellCommand(
                "rm -rf /data/data/${targetContext.packageName}/files/datastore/",
            ).close()

        // Grant all permissions so allPermissionsGranted() returns true and the app
        // starts directly on IdleScreen instead of OnboardingScreen.
        val pkg = targetContext.packageName
        uiAutomation.executeShellCommand("pm grant $pkg ${Manifest.permission.ACCESS_FINE_LOCATION}").close()
        uiAutomation.executeShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow").close()
        uiAutomation.executeShellCommand("appops set $pkg MOCK_LOCATION allow").close()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uiAutomation
                .executeShellCommand(
                    "pm grant $pkg ${Manifest.permission.POST_NOTIFICATIONS}",
                ).close()
        }
        super.onStart()
    }
}
