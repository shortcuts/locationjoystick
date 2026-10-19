package com.locationjoystick.feature.widget.impl

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotPacerTest {
    @Test
    fun `first call does not wait`() =
        runTest {
            val pacer = ScreenshotPacer(350) { currentTime }
            assertEquals(0L, pacer.paced { currentTime })
        }

    @Test
    fun `concurrent calls start at least the gap apart`() =
        runTest {
            val pacer = ScreenshotPacer(350) { currentTime }
            val starts = List(3) { async { pacer.paced { currentTime } } }.awaitAll()
            assertEquals(listOf(0L, 350L, 700L), starts.sorted())
        }

    @Test
    fun `a failing call still stamps, so the next one waits`() =
        runTest {
            val pacer = ScreenshotPacer(350) { currentTime }
            runCatching { pacer.paced { error("boom") } }
            assertEquals(350L, pacer.paced { currentTime })
        }
}
