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

    @Test
    fun `addFavorite preserves exact position`() =
        runTest {
            val position = LatLng(48.8584, 2.2945)
            repository.addFavorite("fav-1", "Tower", position, createdAt = 1_000L)

            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals(position.latitude, list.first().position.latitude, 0.00001)
                assertEquals(position.longitude, list.first().position.longitude, 0.00001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updateFavorite preserves position`() =
        runTest {
            val originalPos = LatLng(0.0, 0.0)
            repository.addFavorite("fav-1", "A", originalPos, createdAt = 1_000L)

            val newPos = LatLng(51.5, -0.1)
            val updated = FavoriteLocation(id = "fav-1", name = "B", position = newPos, createdAt = 1_000L)
            repository.updateFavorite(updated)

            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals(newPos.latitude, list.first().position.latitude, 0.00001)
                assertEquals(newPos.longitude, list.first().position.longitude, 0.00001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `multiple favorites maintain creation order`() =
        runTest {
            repository.addFavorite("a", "First", LatLng(0.0, 0.0), createdAt = 100L)
            repository.addFavorite("b", "Second", LatLng(1.0, 0.0), createdAt = 200L)
            repository.addFavorite("c", "Third", LatLng(2.0, 0.0), createdAt = 300L)

            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals(3, list.size)
                // Descending order (newest first)
                assertEquals("Third", list[0].name)
                assertEquals("Second", list[1].name)
                assertEquals("First", list[2].name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsertHotLocations inserts all 26 hot locations`() =
        runTest {
            repository.upsertHotLocations()

            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals(FavoriteRepository.HOT_LOCATIONS.size, list.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsertHotLocations uses hot_ id prefix for new entries`() =
        runTest {
            repository.upsertHotLocations()

            repository.getFavorites().test {
                val list = awaitItem()
                list.forEach { fav ->
                    assertTrue("Expected hot_ prefix for ${fav.name}", fav.id.startsWith("hot_"))
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsertHotLocations updates coordinates when name already exists`() =
        runTest {
            // Pre-existing favorite with same name as a hot location, different coords
            repository.addFavorite("user-id", "Singapore", LatLng(0.0, 0.0), createdAt = 1_000L)

            repository.upsertHotLocations()

            repository.getFavorites().test {
                val list = awaitItem()
                val singapore = list.first { it.name == "Singapore" }
                // Coords updated to hot location values
                assertEquals(1.288719, singapore.position.latitude, 0.000001)
                assertEquals(103.848742, singapore.position.longitude, 0.000001)
                // Original id preserved (not replaced with hot_ prefix)
                assertEquals("user-id", singapore.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsertHotLocations does not create duplicate when called twice`() =
        runTest {
            repository.upsertHotLocations()
            repository.upsertHotLocations()

            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals(FavoriteRepository.HOT_LOCATIONS.size, list.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `removeHotLocations deletes hot_ prefixed entries`() =
        runTest {
            repository.upsertHotLocations()
            repository.removeHotLocations()

            repository.getFavorites().test {
                val list = awaitItem()
                assertTrue(list.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `removeHotLocations preserves user favorites with non-hot_ ids`() =
        runTest {
            repository.addFavorite("user-1", "My Place", LatLng(10.0, 20.0), createdAt = 1_000L)
            repository.upsertHotLocations()
            repository.removeHotLocations()

            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("My Place", list.first().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `removeHotLocations preserves user favorite that had name collision with hot location`() =
        runTest {
            // User had "Singapore" before hot locations were enabled — upsert updates coords, keeps user id
            repository.addFavorite("user-id", "Singapore", LatLng(0.0, 0.0), createdAt = 1_000L)
            repository.upsertHotLocations()
            repository.removeHotLocations()

            // "Singapore" kept because its id is "user-id", not "hot_*"
            repository.getFavorites().test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("Singapore", list.first().name)
                assertEquals("user-id", list.first().id)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
