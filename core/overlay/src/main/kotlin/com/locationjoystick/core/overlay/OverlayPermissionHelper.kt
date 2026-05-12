package com.locationjoystick.core.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object OverlayPermissionHelper {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun requestOverlayPermission(context: Context) {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }
}
