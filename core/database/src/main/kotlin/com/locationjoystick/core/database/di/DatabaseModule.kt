package com.locationjoystick.core.database.di

import android.content.Context
import androidx.room.Room
import com.locationjoystick.core.database.LjDatabase
import com.locationjoystick.core.database.dao.FavoriteDao
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.dao.WaypointDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLjDatabase(
        @ApplicationContext context: Context,
    ): LjDatabase = Room.databaseBuilder(
        context,
        LjDatabase::class.java,
        LjDatabase.DATABASE_NAME,
    ).addMigrations(LjDatabase.MIGRATION_1_2).build()

    @Provides
    fun provideRouteDao(database: LjDatabase): RouteDao = database.routeDao()

    @Provides
    fun provideWaypointDao(database: LjDatabase): WaypointDao = database.waypointDao()

    @Provides
    fun provideFavoriteDao(database: LjDatabase): FavoriteDao = database.favoriteDao()
}
