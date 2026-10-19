package com.locationjoystick.feature.settings.impl

import android.net.Uri
import com.locationjoystick.core.common.constants.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImportExportRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val repository = ImportExportRepository(RuntimeEnvironment.getApplication())

    private fun fileUri(bytes: ByteArray): Uri = Uri.fromFile(folder.newFile().also { it.writeBytes(bytes) })

    @Test
    fun `file within cap is read`() {
        assertEquals("{\"a\":1}", repository.readTextFromUri(fileUri("{\"a\":1}".toByteArray())))
    }

    @Test
    fun `file above cap throws`() {
        val oversized = ByteArray((AppConstants.ExportConstants.MAX_IMPORT_SIZE_BYTES + 1).toInt())
        assertThrows(IllegalArgumentException::class.java) { repository.readTextFromUri(fileUri(oversized)) }
        assertThrows(IllegalArgumentException::class.java) { repository.readBytesFromUri(fileUri(oversized)) }
    }
}
