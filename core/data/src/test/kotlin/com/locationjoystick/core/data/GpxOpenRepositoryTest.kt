package com.locationjoystick.core.data

import app.cash.turbine.test
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GpxOpenRepositoryTest {
    private val repository = GpxOpenRepository()

    @Test
    fun `setPending emits points and name`() =
        runTest {
            repository.pending.test {
                repository.setPending(listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)), "walk")
                val pending = awaitItem()
                assertEquals("walk", pending.suggestedName)
                assertEquals(2, pending.points.size)
                assertEquals(1.0, pending.points[0].latitude, 0.0)
            }
        }

    @Test
    fun `replay delivers to a late collector until consume`() =
        runTest {
            repository.setPending(listOf(LatLng(1.0, 2.0)), "a")
            repository.pending.test {
                assertEquals("a", awaitItem().suggestedName)
            }
            repository.consume()
            repository.pending.test {
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `collecting does not clear pending for a later map ViewModel`() =
        runTest {
            repository.setPending(listOf(LatLng(1.0, 2.0)), "a")
            repository.pending.test {
                assertEquals("a", awaitItem().suggestedName)
                cancelAndIgnoreRemainingEvents()
            }
            repository.pending.test {
                assertEquals("a", awaitItem().suggestedName)
            }
        }

    @Test
    fun `setPending with the same points is delivered again`() =
        runTest {
            val points = listOf(LatLng(1.0, 2.0))
            repository.pending.test {
                repository.setPending(points, "a")
                assertEquals("a", awaitItem().suggestedName)
                repository.setPending(points, "a")
                assertEquals("a", awaitItem().suggestedName)
            }
        }
}
