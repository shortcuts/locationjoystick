package com.locationjoystick.core.common.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.locationjoystick.core.common.R
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng

fun currentLocationShareText(position: LatLng?): String? = position?.let(::formatCapturedPoint)

internal fun shareChooserFlags(fromActivity: Boolean): Int = if (fromActivity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK

/** Opens the system share sheet with `formatCapturedPoint` of [position], or toasts if missing.
 *  Returns true when the chooser was started. */
fun shareCurrentLocationCoordinates(
    context: Context,
    position: LatLng?,
): Boolean {
    val text = currentLocationShareText(position)
    if (text == null) {
        toastNoCurrentLocation(context)
        return false
    }
    startShareChooser(context, text)
    return true
}

/** The HTTPS location link for [position] (see docs/features/location-links.md). */
fun locationShareLink(position: LatLng): String = AppConstants.AppInfo.buildDeepLink(position.latitude, position.longitude)

/** Opens the system share sheet with the HTTPS location link for [position], or toasts if missing. */
fun shareLocationLink(
    context: Context,
    position: LatLng?,
) {
    if (position == null) return toastNoCurrentLocation(context)
    startShareChooser(context, locationShareLink(position))
}

/** Copies `formatCapturedPoint` of [position] to the clipboard and toasts the copied text, or toasts if missing. */
fun copyCoordinatesToClipboard(
    context: Context,
    position: LatLng?,
) {
    if (position == null) return toastNoCurrentLocation(context)
    val text = formatCapturedPoint(position)
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, text))
    Toast.makeText(context, context.getString(R.string.share_location_copied_coordinates, text), Toast.LENGTH_SHORT).show()
}

private fun startShareChooser(
    context: Context,
    text: String,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    val chooser =
        Intent.createChooser(send, null).apply {
            addFlags(shareChooserFlags(context is Activity))
        }
    context.startActivity(chooser)
}

private fun toastNoCurrentLocation(context: Context) {
    Toast.makeText(context, context.getString(R.string.share_coordinates_no_current_location), Toast.LENGTH_SHORT).show()
}
