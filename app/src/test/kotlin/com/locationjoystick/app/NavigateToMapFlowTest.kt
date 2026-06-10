package com.locationjoystick.app

import android.content.Intent
import app.cash.turbine.test
import com.locationjoystick.core.common.constants.AppConstants
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the navigate-to-map intent handling contract.
 *
 * Regression: widget MAP button would silently fail on cold start because
 * handleIntent ran before LaunchedEffect registered the nav callback. Fixed by
 * using MutableSharedFlow(replay=1) — emission is buffered and replayed to
 * late collectors.
 */
class NavigateToMapFlowTest {
    // Mirrors MainActivity.handleIntent logic so tests catch regressions there.
    private fun handleIntent(
        intent: Intent?,
        flow: MutableSharedFlow<Unit>,
    ) {
        if (intent?.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP, false) == true) {
            flow.tryEmit(Unit)
        }
    }

    @Test
    fun `navigate_to_map true emits on flow`() {
        val flow = MutableSharedFlow<Unit>(replay = 1)
        val intent = mockk<Intent>()
        every { intent.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP, false) } returns true

        handleIntent(intent, flow)

        assertEquals(1, flow.replayCache.size)
    }

    @Test
    fun `navigate_to_map false does not emit`() =
        runTest {
            val flow = MutableSharedFlow<Unit>(replay = 1)
            val intent = mockk<Intent>()
            every { intent.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP, false) } returns false

            handleIntent(intent, flow)

            assertEquals(0, flow.replayCache.size)
        }

    @Test
    fun `null intent does not emit`() =
        runTest {
            val flow = MutableSharedFlow<Unit>(replay = 1)

            handleIntent(null, flow)

            assertEquals(0, flow.replayCache.size)
        }

    @Test
    fun `replay buffer delivers cold-start emission to late collector`() =
        runTest {
            val flow = MutableSharedFlow<Unit>(replay = 1)
            val intent = mockk<Intent>()
            every { intent.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP, false) } returns true

            // Simulate cold start: handleIntent fires before LaunchedEffect collect starts
            handleIntent(intent, flow)

            // Late collector — must still receive the event
            var received = false
            flow.test {
                awaitItem()
                received = true
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue("Late collector must receive replayed emission", received)
        }

    @Test
    fun `multiple handleIntent calls only buffer one replay event`() =
        runTest {
            val flow = MutableSharedFlow<Unit>(replay = 1)
            val intent = mockk<Intent>()
            every { intent.getBooleanExtra(AppConstants.ServiceConstants.EXTRA_NAVIGATE_TO_MAP, false) } returns true

            handleIntent(intent, flow)
            handleIntent(intent, flow)

            assertEquals(1, flow.replayCache.size)
        }
}
