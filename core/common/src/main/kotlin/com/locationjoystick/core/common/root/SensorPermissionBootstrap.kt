package com.locationjoystick.core.common.root

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorPermissionBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val permission = "android.permission.INJECT_EVENTS"

    fun isGranted(): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    suspend fun grantIfNeeded(): Boolean {
        if (isGranted()) return true
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "pm grant ${context.packageName} $permission")
                )
                process.waitFor()
                process.destroy()
                isGranted()
            } catch (e: Exception) {
                false
            }
        }
    }
}
