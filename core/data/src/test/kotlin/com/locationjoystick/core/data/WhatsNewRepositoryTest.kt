package com.locationjoystick.core.data

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsNewRepositoryTest {
    private val context = mockk<Context>(relaxed = true)
    private val repository = WhatsNewRepository(context)

    @Test
    fun `reads packaged entries offline and normalizes version suffix`() =
        runTest {
            val body = """{"entries":[{"category":"feat","scope":"General","summary":"Offline notes."}]}"""
            every { context.assets.open("1.0.0.json") } returns body.byteInputStream()
            assertEquals(listOf(WhatsNewEntry("feat", "General", "Offline notes.")), repository.fetchEntries("1.0.0-test"))
        }

    @Test
    fun `missing asset returns null`() =
        runTest {
            every { context.assets.open(any()) } throws java.io.FileNotFoundException()
            assertNull(repository.fetchEntries("1.0.0"))
        }

    @Test
    fun `malformed asset returns null`() =
        runTest {
            every { context.assets.open(any()) } returns "not json".byteInputStream()
            assertNull(repository.fetchEntries("1.0.0"))
        }
}
