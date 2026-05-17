package com.locationjoystick.core.common.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowUtilsTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `throttleLatest with zero period emits at least one value`() =
        runTest(testDispatcher) {
            val results = mutableListOf<Int>()
            val flow =
                flow {
                    for (i in 1..5) {
                        emit(i)
                    }
                }.throttleLatest(0L)

            flow.toList(results)

            assertTrue("should emit at least one value", results.isNotEmpty())
        }

    @Test
    fun `throttleLatest with single emission works`() =
        runTest(testDispatcher) {
            val results = mutableListOf<String>()
            val flow =
                flow {
                    emit("only")
                }.throttleLatest(50L)

            flow.toList(results)

            assertEquals(1, results.size)
            assertEquals("only", results[0])
        }

    @Test
    fun `throttleLatest with empty flow emits nothing`() =
        runTest(testDispatcher) {
            val results = mutableListOf<Int>()
            val flow = flow<Int> { }.throttleLatest(100L)

            flow.toList(results)

            assertTrue(results.isEmpty())
        }

    @Test
    fun `throttleLatest applies delay between emissions`() =
        runTest(testDispatcher) {
            val results = mutableListOf<Int>()
            val flow =
                flow {
                    emit(1)
                    delay(10)
                    emit(2)
                    delay(10)
                    emit(3)
                }.throttleLatest(50L)

            flow.toList(results)

            assertTrue("should emit at least one value", results.isNotEmpty())
            assertTrue("conflation should reduce items", results.size < 3)
        }

    @Test
    fun `throttleLatest conflation drops intermediate values`() =
        runTest(testDispatcher) {
            val results = mutableListOf<Int>()
            val flow =
                flow {
                    // Emit many values quickly - conflate should drop all but last
                    for (i in 1..100) {
                        emit(i)
                    }
                }.throttleLatest(50L)

            flow.toList(results)

            // With conflate, only the last value before each delay should be kept
            // Since all emits happen instantly, we should get just the last one
            assertTrue("conflation should reduce items", results.size < 100)
        }

    @Test
    fun `throttleLatest preserves last value through conflation`() =
        runTest(testDispatcher) {
            val results = mutableListOf<Int>()
            val flow =
                flow {
                    emit(1)
                    emit(2)
                    emit(3)
                }.throttleLatest(10L)

            flow.toList(results)

            // The last value (3) should be in results due to conflation
            assertTrue(results.contains(3))
        }
}
