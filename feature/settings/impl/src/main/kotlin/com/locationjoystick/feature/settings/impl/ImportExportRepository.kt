package com.locationjoystick.feature.settings.impl

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportExportRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun readTextFromUri(uri: Uri): String =
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""

        fun readBytesFromUri(uri: Uri): ByteArray =
            context.contentResolver.openInputStream(uri)?.readBytes() ?: ByteArray(0)

        fun writeToUri(
            uri: Uri,
            content: String,
        ) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
        }
    }
