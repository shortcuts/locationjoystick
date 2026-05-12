package com.locationjoystick.core.common.extension

import android.app.ActivityManager
import android.content.Context

@Suppress("DEPRECATION")
fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == serviceClass.name }
}
