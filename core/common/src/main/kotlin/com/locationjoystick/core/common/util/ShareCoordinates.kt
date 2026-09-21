package com.locationjoystick.core.common.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.locationjoystick.core.common.R
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
        Toast.makeText(context, context.getString(R.string.share_coordinates_no_current_location), Toast.LENGTH_SHORT).show()
        return false
    }
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
    return true
}
