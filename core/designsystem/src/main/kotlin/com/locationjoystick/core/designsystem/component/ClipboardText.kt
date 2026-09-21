package com.locationjoystick.core.designsystem.component

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

suspend fun Clipboard.readPlainText(): String? =
    getClipEntry()
        ?.clipData
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.text
        ?.toString()

suspend fun Clipboard.writePlainText(text: String) = setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
