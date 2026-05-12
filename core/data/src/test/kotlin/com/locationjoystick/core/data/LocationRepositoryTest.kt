package com.locationjoystick.core.data

import app.cash.turbine.test
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationRepositoryTest {

    private val repository = LocationRepository()

    @Test
    fun `setPositionInternal emits new position via observePosition`() = runTest {
        val position = LatLng(10.0, 20.0)

        repository.observePosition().test {
            // consume initial null emission
            awaitItem()

            repository.setPositionInternal(position)

            assertEquals(position, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
