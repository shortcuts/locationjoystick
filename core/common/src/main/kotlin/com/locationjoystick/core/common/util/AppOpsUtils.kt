package com.locationjoystick.core.common.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings

fun isMockLocationEnabled(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode =
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(),
            context.packageName,
        )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun isOverlayPermissionGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
