package com.locationjoystick.core.routing.di

import com.locationjoystick.core.routing.OsrmClient
import com.locationjoystick.core.routing.RoamingEngine
import com.locationjoystick.core.routing.RouteInterpolator
import com.locationjoystick.core.routing.RouteReplayEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {
    @Provides
    @Singleton
    fun provideOsrmClient(): OsrmClient = OsrmClient()

    @Provides
    @Singleton
    fun provideRouteInterpolator(): RouteInterpolator = RouteInterpolator()

    @Provides
    @Singleton
    fun provideRoamingEngine(
        osrmClient: OsrmClient,
        routeInterpolator: RouteInterpolator,
    ): RoamingEngine = RoamingEngine(osrmClient, routeInterpolator)

    @Provides
    @Singleton
    fun provideRouteReplayEngine(routeInterpolator: RouteInterpolator): RouteReplayEngine = RouteReplayEngine(routeInterpolator)
}
