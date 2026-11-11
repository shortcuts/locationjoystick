package com.locationjoystick.app.privacy

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrivacyModule {
    @Provides
    @Singleton
    fun cloneStore(
        @ApplicationContext context: Context,
    ): CloneStore = CloneStore(File(context.filesDir, "privacy-clone"))

    @Provides
    @Singleton
    fun privacyFlagsStore(
        @ApplicationContext context: Context,
    ): PrivacyFlagsStore = PrivacyFlagsStore(File(context.filesDir, "privacy-mode/flags.properties"))
}
