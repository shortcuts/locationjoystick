package com.locationjoystick.core.routing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingErrorReporterTest {
    @Test
    fun `report emits to all collectors`() =
        runTest(UnconfinedTestDispatcher()) {
            val reporter = RoutingErrorReporter()
            val received1 = mutableListOf<String>()
            val received2 = mutableListOf<String>()

            val job1 = backgroundScope.launch { reporter.errors.collect { received1.add(it) } }
            val job2 = backgroundScope.launch { reporter.errors.collect { received2.add(it) } }

            reporter.report("road routing unavailable")

            assertEquals(listOf("road routing unavailable"), received1)
            assertEquals(listOf("road routing unavailable"), received2)

            job1.cancel()
            job2.cancel()
        }

    @Test
    fun `reportRoadFollowingFallbacks emits formatted summary when fallbackCount is positive`() =
        runTest(UnconfinedTestDispatcher()) {
            val reporter = RoutingErrorReporter()
            val received = mutableListOf<String>()
            val job = backgroundScope.launch { reporter.errors.collect { received.add(it) } }

            reporter.reportRoadFollowingFallbacks(fallbackCount = 2, totalLegs = 5)

            assertEquals(
                listOf("Road-following partially unavailable — 2 of 5 legs used straight-line paths"),
                received,
            )
            job.cancel()
        }

    @Test
    fun `reportRoadFollowingFallbacks emits nothing when fallbackCount is zero`() =
        runTest(UnconfinedTestDispatcher()) {
            val reporter = RoutingErrorReporter()
            val received = mutableListOf<String>()
            val job = backgroundScope.launch { reporter.errors.collect { received.add(it) } }

            reporter.reportRoadFollowingFallbacks(fallbackCount = 0, totalLegs = 5)

            assertEquals(emptyList<String>(), received)
            job.cancel()
        }
}
