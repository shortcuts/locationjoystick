package com.locationjoystick.core.routing.di

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.routing.BackendCooldowns
import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoamingEngine
import com.locationjoystick.core.routing.RouteInterpolator
import com.locationjoystick.core.routing.RouteReplayEngine
import com.locationjoystick.core.routing.RoutingErrorReporter
import com.locationjoystick.core.routing.TeleportRouteEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {
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

    @Provides
    @Singleton
    fun provideRouteInterpolator(): RouteInterpolator = RouteInterpolator()

    @Provides
    @Singleton
    fun provideRoamingEngine(
        osrmClient: OsrmClient,
        routeInterpolator: RouteInterpolator,
        routingErrorReporter: RoutingErrorReporter,
    ): RoamingEngine = RoamingEngine(osrmClient, routeInterpolator, routingErrorReporter)

    @Provides
    @Singleton
    fun provideRouteReplayEngine(routeInterpolator: RouteInterpolator): RouteReplayEngine = RouteReplayEngine(routeInterpolator)

    @Provides
    @Singleton
    fun provideTeleportRouteEngine(): TeleportRouteEngine = TeleportRouteEngine()
}
