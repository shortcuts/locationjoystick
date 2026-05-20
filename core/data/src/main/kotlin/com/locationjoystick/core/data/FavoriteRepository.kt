package com.locationjoystick.core.data

import android.util.Log
import com.locationjoystick.core.database.dao.FavoriteDao
import com.locationjoystick.core.database.entities.toDomain
import com.locationjoystick.core.database.entities.toEntity
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FavoriteRepository"

@Singleton
class FavoriteRepository
    @Inject
    constructor(
        private val favoriteDao: FavoriteDao,
    ) {
        fun getFavorites(): Flow<List<FavoriteLocation>> = favoriteDao.getAll().map { list -> list.map { it.toDomain() } }

        suspend fun addFavorite(
            id: String,
            name: String,
            position: LatLng,
            createdAt: Long = System.currentTimeMillis(),
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val favorite =
                        FavoriteLocation(
                            id = id,
                            name = name,
                            position = position,
                            createdAt = createdAt,
                        )
                    favoriteDao.insert(favorite.toEntity())
                }.onFailure { e ->
                    Log.e(TAG, "Failed to add favorite: $name", e)
                }
            }

        suspend fun updateFavorite(favorite: FavoriteLocation): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.update(favorite.toEntity())
                }.onFailure { e ->
                    Log.e(TAG, "Failed to update favorite: ${favorite.id}", e)
                }
            }

        suspend fun deleteFavorite(id: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val entity = favoriteDao.getById(id)
                    if (entity != null) {
                        favoriteDao.delete(entity)
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to delete favorite: $id", e)
                }
            }

        suspend fun deleteAllFavorites(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.deleteAll()
                }.onFailure { e ->
                    Log.e(TAG, "Failed to delete all favorites", e)
                }
            }
    }
