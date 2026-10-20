package com.locationjoystick.core.data

import androidx.room.withTransaction
import com.locationjoystick.core.database.LjDatabase
import com.locationjoystick.core.database.dao.FavoriteDao
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.entities.toEntity
import com.locationjoystick.core.model.FavoriteLocation
import com.locationjoystick.core.model.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository
    @Inject
    constructor(
        private val database: LjDatabase,
        private val favoriteDao: FavoriteDao,
        private val routeDao: RouteDao,
    ) {
        /** Throws instead of returning a Result: a failed insert must roll back the deletes and reach the caller. */
        suspend fun apply(
            favorites: List<FavoriteLocation>,
            routes: List<Route>,
            clearFavorites: Boolean,
            clearRoutes: Boolean,
        ) = withContext(Dispatchers.IO) {
            database.withTransaction {
                if (clearFavorites) favoriteDao.deleteAll()
                if (clearRoutes) {
                    routeDao.deleteAllWaypoints()
                    routeDao.deleteAll()
                }
                favorites.forEach { favoriteDao.insert(it.toEntity()) }
                routes.forEach { route ->
                    routeDao.insert(route.toEntity())
                    routeDao.replaceWaypoints(route.id, route.waypoints.map { it.toEntity(route.id) })
                }
            }
        }
    }
