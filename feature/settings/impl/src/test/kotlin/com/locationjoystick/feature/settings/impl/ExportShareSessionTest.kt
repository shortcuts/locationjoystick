package com.locationjoystick.feature.settings.impl

import com.locationjoystick.core.common.util.NsdCodeManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportShareSessionTest {
    private val server = mockk<ExportSyncServer>(relaxed = true)
    private val nsd = mockk<NsdCodeManager>(relaxed = true)
    private val scopeJob = SupervisorJob()
    private val session = ExportShareSession(server, nsd, CoroutineScope(scopeJob))

    init {
        every { server.start(any(), any()) } returns 4242
    }

    @After
    fun tearDown() {
        scopeJob.cancel()
    }

    private fun awaitJob(job: Job) = runBlocking { job.join() }

    @Test
    fun `starts idle`() {
        assertEquals(ShareState.Idle, session.current)
    }

    @Test
    fun `start moves idle to starting then serving once the server is up`() {
        val release = CompletableDeferred<Unit>()
        val served = CompletableDeferred<Unit>()
        session.start { serve ->
            serve("tok", "{}")
            served.complete(Unit)
            release.await()
        }
        runBlocking { served.await() }
        val s = session.current as ShareState.Serving
        assertEquals("tok", s.code)
        verify { nsd.startAdvertising("tok", 4242, export = true) }
        release.complete(Unit)
    }

    @Test
    fun `start before serve is starting with no code`() {
        val release = CompletableDeferred<Unit>()
        session.start { release.await() }
        assertTrue(session.current is ShareState.Starting)
        assertEquals(null, session.current.code)
        release.complete(Unit)
    }

    @Test
    fun `stop from serving ends advertising and returns to idle`() {
        val served = CompletableDeferred<Unit>()
        session.start { serve ->
            serve("tok", "{}")
            served.complete(Unit)
            CompletableDeferred<Unit>().await()
        }
        runBlocking { served.await() }
        session.stop()
        val stopping = session.current
        assertTrue(stopping is ShareState.Stopping)
        awaitJob(stopping.job!!)
        assertEquals(ShareState.Idle, session.current)
        verify { server.stop() }
        verify { nsd.stopAdvertising("tok") }
    }

    @Test
    fun `stop from idle still tears down and returns to idle`() {
        session.stop()
        awaitJob(session.current.job!!)
        assertEquals(ShareState.Idle, session.current)
        verify { server.stop() }
    }

    @Test
    fun `start after stop waits and a later stop wins`() {
        session.stop()
        val stopJob = session.current.job!!
        session.start { }
        val startJob = session.current.job!!
        session.stop()
        val last = session.current
        assertTrue(last is ShareState.Stopping)
        awaitJob(last.job!!)
        assertTrue(stopJob.isCompleted && startJob.isCompleted)
        assertEquals(ShareState.Idle, session.current)
    }

    @Test
    fun `start from serving keeps the stale code for stop`() {
        val served = CompletableDeferred<Unit>()
        session.start { serve ->
            serve("old", "{}")
            served.complete(Unit)
            CompletableDeferred<Unit>().await()
        }
        runBlocking { served.await() }
        session.start { CompletableDeferred<Unit>().await() }
        val s = session.current as ShareState.Starting
        assertEquals("old", s.code)
        session.stop()
        awaitJob(session.current.job!!)
        verify { nsd.stopAdvertising("old") }
        assertSame(ShareState.Idle, session.current)
    }
}
