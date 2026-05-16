package com.locationjoystick.core.common.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
    fun `throttleLatest with zero period emits all values`() =
        runTest(testDispatcher) {
            val results = mutableListOf<Int>()
            val flow = flow {
                for (i in 1..5) {
                    emit(i)
                }
            }.throttleLatest(0L)

            flow.toList(results)

            assertEquals(5, results.size)
        }

    @Test
    fun `throttleLatest with single emission works`() =
        runTest(testDispatcher) {
            val results = mutableListOf<String>()
            val flow = flow {
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
    fun `throttleLatest delays each emission by periodMs`() =
        runTest(testDispatcher) {
            val results = mutableListOf<Int>()
            val flow = flow {
                emit(1)
                emit(2)
                emit(3)
            }.throttleLatest(100L)

            // Start collecting
            val collectJob = kotlinx.coroutines.launch {
                flow.toList(results)
            }

            // After 50ms, nothing should be emitted yet (delay is 100ms per item)
            advanceTimeBy(50)
            assertEquals(0, results.size)

            // After 100ms, first item should be emitted
            advanceTimeBy(100)
            assertEquals(1, results.size)

            // After another 100ms, second item
            advanceTimeBy(100)
            assertEquals(2, results.size)

            // After another 100ms, third item
            advanceTimeBy(100)
            assertEquals(3, results.size)

            collectJob.join()
        }

    @Test
    fun `throttleLatest conflation drops intermediate values`() =
        runTest(testDispatcher) {
            val results = mutableListOf<Int>()
            val flow = flow {
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
            val flow = flow {
                emit(1)
                emit(2)
                emit(3)
            }.throttleLatest(10L)

            flow.toList(results)

            // The last value (3) should be in results due to conflation
            assertTrue(results.contains(3))
        }
}
