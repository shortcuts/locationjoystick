package com.locationjoystick.feature.settings.impl

import com.locationjoystick.feature.settings.api.SettingsExtension
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsExtensionsModule {
    @Multibinds
    abstract fun settingsExtensions(): Set<SettingsExtension>
}
