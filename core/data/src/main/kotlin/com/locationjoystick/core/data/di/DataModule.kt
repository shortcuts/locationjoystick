package com.locationjoystick.core.data.di

import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.LocationRepository
import com.locationjoystick.core.data.RoamingRepository
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.database.dao.FavoriteDao
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.dao.WaypointDao
import com.locationjoystick.core.datastore.AppPreferencesDataSource
import com.locationjoystick.core.routing.RoamingEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideRouteRepository(
        routeDao: RouteDao,
        waypointDao: WaypointDao,
    ): RouteRepository = RouteRepository(routeDao, waypointDao)

    @Provides
    @Singleton
    fun provideFavoriteRepository(favoriteDao: FavoriteDao): FavoriteRepository = FavoriteRepository(favoriteDao)

    @Provides
    @Singleton
    fun provideSettingsRepository(dataSource: AppPreferencesDataSource): SettingsRepository = SettingsRepository(dataSource)

    @Provides
    @Singleton
    fun provideLocationRepository(): LocationRepository = LocationRepository()

    @Provides
    @Singleton
    fun provideRoamingRepository(
        roamingEngine: RoamingEngine,
        locationRepository: LocationRepository,
    ): RoamingRepository = RoamingRepository(roamingEngine, locationRepository)
}
