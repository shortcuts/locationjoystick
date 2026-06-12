package com.locationjoystick.feature.favorites.impl

import app.cash.turbine.test
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.testing.FakeFavoriteDao
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeFavoriteDao: FakeFavoriteDao
    private lateinit var favoriteRepository: FavoriteRepository
    private val locationRepository = LocationRepository()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val teleportUseCase: TeleportUseCase = mockk(relaxed = true)
    private lateinit var viewModel: FavoritesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeFavoriteDao = FakeFavoriteDao()
        favoriteRepository = FavoriteRepository(fakeFavoriteDao)
        every { settingsRepository.getFavoritesSortNewestFirst() } returns flowOf(true)
        every { settingsRepository.getLastTeleportTime() } returns flowOf(0L)
        every { settingsRepository.getLastLocation() } returns flowOf(null)
        every { settingsRepository.getRecentSearches() } returns flowOf(emptyList())
        viewModel = FavoritesViewModel(favoriteRepository, locationRepository, settingsRepository, teleportUseCase)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_emits_empty_list_initially() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<FavoriteLocation>(), state.favorites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_sorts_newest_first() = runTest {
        favoriteRepository.addFavorite("id1", "Old", LatLng(0.0, 0.0), createdAt = 1000L)
        favoriteRepository.addFavorite("id2", "New", LatLng(1.0, 1.0), createdAt = 2000L)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("New", state.favorites[0].name)
            assertEquals("Old", state.favorites[1].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_sorts_oldest_first_when_sort_flag_false() = runTest {
        every { settingsRepository.getFavoritesSortNewestFirst() } returns flowOf(false)
        val vm = FavoritesViewModel(favoriteRepository, locationRepository, settingsRepository, teleportUseCase)
        favoriteRepository.addFavorite("id1", "Old", LatLng(0.0, 0.0), createdAt = 1000L)
        favoriteRepository.addFavorite("id2", "New", LatLng(1.0, 1.0), createdAt = 2000L)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals("Old", state.favorites[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleSort_flips_sort_order() = runTest {
        viewModel.toggleSort()
        coVerify { settingsRepository.setFavoritesSortNewestFirst(false) }
    }

    @Test
    fun setPendingDeleteId_updates_ui_state() = runTest {
        viewModel.setPendingDeleteId("target-id")
        viewModel.uiState.test {
            assertEquals("target-id", awaitItem().pendingDeleteId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmDelete_deletes_favorite_and_clears_pending() = runTest {
        favoriteRepository.addFavorite("fav1", "Tokyo", LatLng(35.68, 139.69), 1000L)
        viewModel.setPendingDeleteId("fav1")
        viewModel.confirmDelete()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.pendingDeleteId)
            assertEquals(0, state.favorites.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmDelete_is_noop_when_no_pending_id() = runTest {
        favoriteRepository.addFavorite("fav1", "Tokyo", LatLng(35.68, 139.69), 1000L)
        viewModel.confirmDelete()

        viewModel.uiState.test {
            assertEquals(1, awaitItem().favorites.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addFavorite_persists_to_repository() = runTest {
        viewModel.addFavorite("Paris", 48.8566, 2.3522)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.favorites.size)
            assertEquals("Paris", state.favorites[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteFavorite_removes_from_repository() = runTest {
        favoriteRepository.addFavorite("fav1", "Tokyo", LatLng(35.68, 139.69), 1000L)
        viewModel.deleteFavorite("fav1")

        viewModel.uiState.test {
            assertEquals(0, awaitItem().favorites.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun renameFavorite_updates_name() = runTest {
        favoriteRepository.addFavorite("fav1", "Old Name", LatLng(35.68, 139.69), 1000L)

        viewModel.uiState.test {
            awaitItem()
            viewModel.renameFavorite("fav1", "New Name")
            assertEquals("New Name", awaitItem().favorites[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateFavorite_updates_name_and_position() = runTest {
        favoriteRepository.addFavorite("fav1", "Old", LatLng(0.0, 0.0), 1000L)

        viewModel.uiState.test {
            awaitItem()
            viewModel.updateFavorite("fav1", "Updated", 10.0, 20.0)
            val state = awaitItem()
            assertEquals("Updated", state.favorites[0].name)
            assertEquals(LatLng(10.0, 20.0), state.favorites[0].position)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun teleportTo_invokes_use_case() = runTest {
        val fav = FavoriteLocation("id", "Test", LatLng(1.0, 2.0), 0L)
        viewModel.teleportTo(fav)
        coVerify { teleportUseCase.execute(LatLng(1.0, 2.0)) }
    }
}
