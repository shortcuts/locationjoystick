package com.locationjoystick.core.location.di

import com.locationjoystick.core.data.MockLocationServiceStarter
import com.locationjoystick.core.location.MockLocationServiceStarterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationBindingsModule {
    @Binds
    abstract fun bindMockLocationServiceStarter(impl: MockLocationServiceStarterImpl): MockLocationServiceStarter
}
