package com.locationjoystick.core.routing.di

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.routing.BackendCooldowns
import com.locationjoystick.core.routing.OsrmClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Its own module so instrumented tests replace only the road lookup. */
@Module
@InstallIn(SingletonComponent::class)
object OsrmClientModule {
    @Provides
    @Singleton
    fun provideOsrmClient(
        @ApplicationContext context: Context,
    ): OsrmClient =
        OsrmClient(
            BackendCooldowns(
                context.getSharedPreferences(AppConstants.OsrmConstants.COOLDOWN_PREFS_NAME, Context.MODE_PRIVATE),
            ),
        )
}
