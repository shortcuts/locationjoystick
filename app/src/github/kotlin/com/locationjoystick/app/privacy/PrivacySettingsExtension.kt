package com.locationjoystick.app.privacy

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.locationjoystick.app.BuildConfig
import com.locationjoystick.app.R
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.feature.settings.api.SettingsExtension
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class PrivacySettingsExtension
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SettingsExtension {
        override val id = "privacy_mode"
        override val icon: ImageVector = LjIcons.Lock

        // A clone keeps the original application ID in BuildConfig, so it can tell it is not the original.
        override val isAvailable = context.packageName == BuildConfig.APPLICATION_ID

        @Composable
        override fun title() = stringResource(R.string.privacy_title)

        @Composable
        override fun description() = stringResource(R.string.privacy_description)

        @Composable
        override fun Screen(onBack: () -> Unit) = PrivacyModeScreen(onBack)
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class PrivacyExtensionModule {
    @Binds
    @IntoSet
    abstract fun extension(impl: PrivacySettingsExtension): SettingsExtension
}
