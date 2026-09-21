package com.locationjoystick.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import com.locationjoystick.core.common.util.isGpxOpenCandidate

internal fun extraStreamUri(intent: Intent): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }

internal fun gpxUriFromIntent(intent: Intent): Uri? {
    val stream = extraStreamUri(intent)
    if (stream != null) return stream
    return intent.data
}

internal fun isContentOrFileUri(uri: Uri?): Boolean {
    val scheme = uri?.scheme?.lowercase() ?: return false
    return scheme == "content" || scheme == "file"
}

/** Chooser already picked this app: try GPX for any local file URI (content providers may omit the extension). */
internal fun shouldTryOpenAsGpx(intent: Intent): Boolean {
    val action = intent.action
    if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return false
    return isContentOrFileUri(gpxUriFromIntent(intent))
}

internal fun shouldHandleAsGpxOpen(
    intent: Intent,
    displayName: String? = null,
): Boolean {
    val action = intent.action
    if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return false
    val uri = gpxUriFromIntent(intent)
    return isGpxOpenCandidate(intent.type, uri?.toString(), displayName ?: uri?.lastPathSegment)
}
