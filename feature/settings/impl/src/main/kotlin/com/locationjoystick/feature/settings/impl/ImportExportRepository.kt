package com.locationjoystick.feature.settings.impl

import android.content.Context
import android.net.Uri
import com.locationjoystick.core.common.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportExportRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun readTextFromUri(uri: Uri): String = readBytesFromUri(uri).toString(Charsets.UTF_8)

        fun readBytesFromUri(uri: Uri): ByteArray {
            val limit = AppConstants.ExportConstants.MAX_IMPORT_SIZE_BYTES
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readUpTo(limit.toInt() + 1) } ?: return ByteArray(0)
            require(bytes.size <= limit) { "Import file is larger than ${limit / 1024 / 1024} MB" }
            return bytes
        }

        fun writeToUri(
            uri: Uri,
            content: String,
        ) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
        }
    }

// InputStream.readNBytes needs API 33; min SDK is 28.
private fun InputStream.readUpTo(max: Int): ByteArray {
    val buffer = ByteArray(max)
    var filled = 0
    while (filled < max) {
        val read = read(buffer, filled, max - filled)
        if (read < 0) break
        filled += read
    }
    return buffer.copyOf(filled)
}
