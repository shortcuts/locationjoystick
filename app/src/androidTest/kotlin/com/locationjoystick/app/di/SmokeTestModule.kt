package com.locationjoystick.app.di

import android.content.Context
import androidx.room.Room
import com.locationjoystick.core.database.LjDatabase
import com.locationjoystick.core.database.dao.FavoriteDao
import com.locationjoystick.core.database.dao.RouteDao
import com.locationjoystick.core.database.dao.WaypointDao
import com.locationjoystick.core.database.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class],
)
object SmokeTestModule {
    @Provides
    @Singleton
    fun provideTestDatabase(
        @ApplicationContext context: Context,
    ): LjDatabase =
        Room
            .inMemoryDatabaseBuilder(context, LjDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideRouteDao(db: LjDatabase): RouteDao = db.routeDao()

    @Provides
    fun provideWaypointDao(db: LjDatabase): WaypointDao = db.waypointDao()

    @Provides
    fun provideFavoriteDao(db: LjDatabase): FavoriteDao = db.favoriteDao()
}
