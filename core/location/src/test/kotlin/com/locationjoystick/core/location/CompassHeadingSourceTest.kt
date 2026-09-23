package com.locationjoystick.core.location

import com.locationjoystick.core.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompassHeadingSourceTest {
    private fun source(accepted: Boolean): CompassHeadingSource {
        val repository = mockk<SettingsRepository>()
        every { repository.getCompassDisclosureAccepted() } returns flowOf(accepted)
        return CompassHeadingSource(repository).apply {
            bind(
                object : CompassAccessibilityServiceBridge {
                    override suspend fun captureHeading(): Float = 1.5f
                },
            )
        }
    }

    @Test
    fun `no screenshot until the disclosure is accepted`() =
        runTest {
            assertNull(source(accepted = false).captureHeading())
        }

    @Test
    fun `captures once the disclosure is accepted`() =
        runTest {
            assertEquals(1.5f, source(accepted = true).captureHeading())
        }
}
