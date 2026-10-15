package com.locationjoystick.core.location

import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.GrayImage
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

                    override suspend fun captureGrayFrame(): GrayImage = GrayImage(2, 2, ByteArray(4))
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

    private class FakeBridge(
        private val results: List<Float?>,
    ) : CompassAccessibilityServiceBridge {
        var calls = 0

        override suspend fun captureHeading(): Float? = results.getOrNull(calls++)

        override suspend fun captureGrayFrame(): GrayImage? = null
    }

    private fun accepted(bridge: FakeBridge?): CompassHeadingSource {
        val repository = mockk<SettingsRepository>()
        every { repository.getCompassDisclosureAccepted() } returns flowOf(true)
        return CompassHeadingSource(repository).apply { bridge?.let(::bind) }
    }

    @Test
    fun `retries transient null then succeeds`() =
        runTest {
            val bridge = FakeBridge(listOf(null, null, 1.5f))
            assertEquals(1.5f, accepted(bridge).captureHeading())
            assertEquals(3, bridge.calls)
        }

    @Test
    fun `gives up after max attempts`() =
        runTest {
            val bridge = FakeBridge(emptyList())
            assertNull(accepted(bridge).captureHeading())
            assertEquals(AppConstants.CompassTrackingConstants.CAPTURE_ATTEMPTS, bridge.calls)
        }

    @Test
    fun `first success does not retry`() =
        runTest {
            val bridge = FakeBridge(listOf(2f))
            assertEquals(2f, accepted(bridge).captureHeading())
            assertEquals(1, bridge.calls)
        }

    @Test
    fun `unbound returns null`() =
        runTest {
            assertNull(accepted(null).captureHeading())
        }
}
