package com.locationjoystick.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.locationjoystick.core.datastore.AppPreferencesDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppPreferencesDataSource.DATASTORE_FILE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.appPreferencesDataStore

    @Provides
    @Singleton
    fun provideAppPreferencesDataSource(dataStore: DataStore<Preferences>): AppPreferencesDataSource = AppPreferencesDataSource(dataStore)
}
