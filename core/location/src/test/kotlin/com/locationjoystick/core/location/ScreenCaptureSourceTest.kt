package com.locationjoystick.core.location

import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.ReconnectRunResult
import com.locationjoystick.core.model.ReconnectStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenCaptureSourceTest {
    private fun source(
        accepted: Boolean,
        bound: Boolean = true,
        gameInFront: Boolean = true,
    ): ScreenCaptureSource {
        val repository = mockk<SettingsRepository>()
        every { repository.getAccessibilityDisclosureAccepted() } returns flowOf(accepted)
        return ScreenCaptureSource(repository).apply {
            if (bound) {
                bind(
                    object : ScreenAccessibilityServiceBridge {
                        override val gameInFront = gameInFront

                        override suspend fun captureGrayFrame(): GrayImage = GrayImage(2, 2, ByteArray(4))
                    },
                )
            }
        }
    }

    @Test
    fun `no gray frame until the disclosure is accepted`() =
        runTest {
            assertNull(source(accepted = false).captureGrayFrame())
        }

    @Test
    fun `gray frame comes from the bound service once accepted`() =
        runTest {
            val frame = source(accepted = true).captureGrayFrame()
            assertEquals(2, frame!!.width)
        }

    @Test
    fun `no gray frame while the game is not in front`() =
        runTest {
            assertNull(source(accepted = true, gameInFront = false).captureGrayFrame())
        }

    @Test
    fun `unbound returns null`() =
        runTest {
            assertNull(source(accepted = true, bound = false).captureGrayFrame())
        }

    @Test
    fun `reconnect status is reported and cleared on unbind`() {
        val source = source(accepted = true)
        val status = ReconnectStatus(ReconnectRunResult.GATE_CLOSED, 42L)
        source.reportReconnect(status)
        assertEquals(status, source.reconnectStatus.value)
        source.unbind()
        assertNull(source.reconnectStatus.value)
    }
}
