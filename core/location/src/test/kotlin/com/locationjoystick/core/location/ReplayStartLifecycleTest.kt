package com.locationjoystick.core.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayStartLifecycleTest {
    @Test
    fun `begin supersedes and wakes the previous session`() {
        val l = ReplayStartLifecycle()
        val first = l.begin()
        l.setPaused(first, true)
        val second = l.begin()
        assertFalse(first.paused)
        assertFalse(l.isCurrent(first.generation))
        assertTrue(l.isCurrent(second.generation))
    }

    @Test
    fun `abort clears current and unpauses`() {
        val l = ReplayStartLifecycle()
        val s = l.begin()
        l.setPaused(s, true)
        l.abort()
        assertNull(l.current)
        assertFalse(s.paused)
        assertFalse(l.isCurrent(s.generation))
    }

    @Test
    fun `runWhenResumed waits for unpause then runs once`() =
        runTest {
            val l = ReplayStartLifecycle()
            val s = l.begin()
            l.setPaused(s, true)
            var runs = 0
            launch { l.runWhenResumed(s) { runs++ } }
            advanceUntilIdle()
            assertEquals(0, runs)
            l.setPaused(s, false)
            advanceUntilIdle()
            assertEquals(1, runs)
        }

    @Test
    fun `awaitResume throws when superseded while waiting`() =
        runTest {
            val l = ReplayStartLifecycle()
            val s = l.begin()
            l.setPaused(s, true)
            var thrown: Throwable? = null
            launch {
                try {
                    l.awaitResume(s)
                } catch (e: CancellationException) {
                    thrown = e
                }
            }
            advanceUntilIdle()
            l.begin()
            advanceUntilIdle()
            assertTrue(thrown is CancellationException)
        }

    @Test
    fun `isStarting is true until the engine starts`() {
        val l = ReplayStartLifecycle()
        assertFalse(l.isStarting())
        val s = l.begin()
        assertTrue(l.isStarting(true))
        assertFalse(l.isStarting(false))
        l.markEngineStarted(s)
        assertFalse(l.isStarting(true))
    }

    @Test
    fun `engine start keeps the pause flag`() {
        val l = ReplayStartLifecycle()
        val s = l.begin()
        l.setPaused(s, true)
        l.markEngineStarted(s)
        assertEquals(ReplayStartLifecycle.Phase.EngineRunning(paused = true), s.phase.value)
    }

    @Test
    fun `engine stop returns to starting and keeps the pause flag`() {
        val l = ReplayStartLifecycle()
        val s = l.begin()
        l.markEngineStarted(s)
        l.setPaused(s, true)
        l.markEngineStopped(s)
        assertEquals(ReplayStartLifecycle.Phase.Starting(paused = true), s.phase.value)
    }

    @Test
    fun `engine start from engine running is rejected`() {
        val l = ReplayStartLifecycle()
        val s = l.begin()
        l.markEngineStarted(s)
        l.setPaused(s, true)
        l.markEngineStarted(s)
        assertEquals(ReplayStartLifecycle.Phase.EngineRunning(paused = true), s.phase.value)
    }

    @Test
    fun `engine stop from starting is rejected`() {
        val l = ReplayStartLifecycle()
        val s = l.begin()
        l.markEngineStopped(s)
        assertEquals(ReplayStartLifecycle.Phase.Starting(paused = false), s.phase.value)
    }
}
