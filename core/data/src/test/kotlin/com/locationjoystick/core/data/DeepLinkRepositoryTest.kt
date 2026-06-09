package com.locationjoystick.core.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkRepositoryTest {
    private val repository = DeepLinkRepository()

    @Test
    fun `setPendingCoords emits the coordinate to active subscribers`() = runTest {
        repository.pendingCoords.test {
            repository.setPendingCoords(35.6762, 139.6503)
            val received = awaitItem()
            assertEquals(35.6762, received.latitude, 0.0001)
            assertEquals(139.6503, received.longitude, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `late subscriber receives buffered coord before consume`() = runTest {
        repository.setPendingCoords(1.0, 2.0)
        repository.pendingCoords.test {
            val received = awaitItem()
            assertEquals(1.0, received.latitude, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consume clears replay - late subscriber does not receive after consume`() = runTest {
        repository.setPendingCoords(1.0, 2.0)
        repository.consume()
        repository.pendingCoords.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `last emit wins when called multiple times before subscribe`() = runTest {
        repository.setPendingCoords(1.0, 0.0)
        repository.setPendingCoords(2.0, 0.0)
        repository.pendingCoords.test {
            val received = awaitItem()
            assertEquals(2.0, received.latitude, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consume does not throw`() {
        repository.consume()
    }
}
