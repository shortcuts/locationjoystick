package com.locationjoystick.core.data

import app.cash.turbine.test
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.testing.FakeFavoriteDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoriteRepositoryTest {
    private lateinit var dao: FakeFavoriteDao
    private lateinit var repository: FavoriteRepository

    @Before
    fun setUp() {
        dao = FakeFavoriteDao()
        repository = FavoriteRepository(dao)
    }

    @Test
    fun `getFavorites emits empty list initially`() =
        runTest {
            repository.getFavorites().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `addFavorite then getFavorites emits added item`() =
        runTest {
            repository.getFavorites().test {
                awaitItem() // empty list

                repository.addFavorite(
                    id = "fav-1",
                    name = "Eiffel Tower",
                    position = LatLng(48.8584, 2.2945),
                    createdAt = 1_000L,
                )

                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("Eiffel Tower", list.first().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteFavorite removes item from flow`() =
        runTest {
            repository.addFavorite("fav-1", "Tower", LatLng(48.8584, 2.2945), createdAt = 1_000L)

            repository.getFavorites().test {
                val initial = awaitItem()
                assertEquals(1, initial.size)

                repository.deleteFavorite("fav-1")

                val afterDelete = awaitItem()
                assertTrue(afterDelete.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteFavorite non-existent id succeeds without error`() =
        runTest {
            val result = repository.deleteFavorite("does-not-exist")
            assertTrue(result.isSuccess)
        }

    @Test
    fun `getFavorites sorts by createdAt descending`() =
        runTest {
            repository.addFavorite("a", "Old", LatLng(0.0, 0.0), createdAt = 100L)
            repository.addFavorite("b", "New", LatLng(1.0, 0.0), createdAt = 200L)

            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals(2, list.size)
                assertEquals("New", list.first().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updateFavorite changes name in flow`() =
        runTest {
            repository.addFavorite("fav-1", "Old Name", LatLng(0.0, 0.0), createdAt = 1_000L)

            val updated = FavoriteLocation(id = "fav-1", name = "New Name", position = LatLng(0.0, 0.0), createdAt = 1_000L)
            repository.updateFavorite(updated)

            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals("New Name", list.first().name)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
