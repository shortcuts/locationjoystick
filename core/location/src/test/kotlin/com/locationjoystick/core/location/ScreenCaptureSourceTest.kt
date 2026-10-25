package com.locationjoystick.core.location

import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.GrayImage
import com.locationjoystick.core.model.GroundFlowFit
import com.locationjoystick.core.model.GuidedPath
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
        tapToWalk: Boolean = true,
    ): ScreenCaptureSource {
        val repository = mockk<SettingsRepository>()
        every { repository.getAccessibilityDisclosureAccepted() } returns flowOf(accepted)
        every { repository.getTapToWalkOverlayEnabled() } returns flowOf(tapToWalk)
        every { repository.getGuidedPath() } returns flowOf(GuidedPath.POKEMON_GO)
        return ScreenCaptureSource(repository).apply {
            if (bound) {
                bind(
                    object : ScreenAccessibilityServiceBridge {
                        override val gameInFront = gameInFront

                        override suspend fun captureCrops() = GroundFlowFit.crops(GrayImage(100, 200, ByteArray(100 * 200)))
                    },
                )
            }
        }
    }

    @Test
    fun `no crops until the disclosure is accepted`() =
        runTest {
            assertNull(source(accepted = false).captureCrops())
        }

    @Test
    fun `crops comes from the bound service once accepted`() =
        runTest {
            val frame = source(accepted = true).captureCrops()
            assertEquals(100, frame!!.width)
        }

    @Test
    fun `no crops while the game is not in front`() =
        runTest {
            assertNull(source(accepted = true, gameInFront = false).captureCrops())
        }

    @Test
    fun `no crops while the Tap to Walk switch is off`() =
        runTest {
            assertNull(source(accepted = true, tapToWalk = false).captureCrops())
        }

    @Test
    fun `no crops after unbind`() =
        runTest {
            val source = source(accepted = true)
            source.unbind()
            assertNull(source.captureCrops())
        }

    @Test
    fun `unbound returns null`() =
        runTest {
            assertNull(source(accepted = true, bound = false).captureCrops())
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
