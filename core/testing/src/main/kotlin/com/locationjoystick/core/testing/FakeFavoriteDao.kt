package com.locationjoystick.core.testing

import com.locationjoystick.core.database.dao.FavoriteDao
import com.locationjoystick.core.database.entities.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeFavoriteDao : FavoriteDao {
    private val state = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    override suspend fun insert(favorite: FavoriteEntity) {
        state.value = state.value.filter { it.id != favorite.id } + favorite
    }

    override suspend fun update(favorite: FavoriteEntity) {
        state.value = state.value.map { if (it.id == favorite.id) favorite else it }
    }

    override suspend fun delete(favorite: FavoriteEntity) {
        state.value = state.value.filter { it.id != favorite.id }
    }

    override suspend fun getById(id: String): FavoriteEntity? = state.value.find { it.id == id }

    override fun getAll(): Flow<List<FavoriteEntity>> = state.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}
