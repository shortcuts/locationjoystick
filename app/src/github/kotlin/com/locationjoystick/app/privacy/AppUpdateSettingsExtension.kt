package com.locationjoystick.app.privacy

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.locationjoystick.app.R
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.feature.settings.api.SettingsExtension
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

// Shown in the original and in a copy: a copy has no other way to update.
class AppUpdateSettingsExtension
    @Inject
    constructor() : SettingsExtension {
        override val id = "app_update"
        override val icon: ImageVector = LjIcons.FileDownload

        @Composable
        override fun title() = stringResource(R.string.app_update_title)

        @Composable
        override fun description() = stringResource(R.string.app_update_description)

        @Composable
        override fun Screen(onBack: () -> Unit) = AppUpdateScreen(onBack)
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateExtensionModule {
    @Binds
    @IntoSet
    abstract fun extension(impl: AppUpdateSettingsExtension): SettingsExtension
}
