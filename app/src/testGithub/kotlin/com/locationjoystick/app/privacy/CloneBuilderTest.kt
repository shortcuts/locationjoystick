package com.locationjoystick.app.privacy

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloneBuilderTest {
    @Test
    fun `runtime error inside the rebuild returns Failed instead of throwing`() =
        runTest {
            val store = mockk<CloneStore> { every { read() } throws IllegalStateException("boom") }
            val builder = CloneBuilder(mockk<Context>(), store, UnconfinedTestDispatcher(testScheduler))
            assertEquals(CloneBuildResult.Failed("boom"), builder.build("Notes"))
        }
}
