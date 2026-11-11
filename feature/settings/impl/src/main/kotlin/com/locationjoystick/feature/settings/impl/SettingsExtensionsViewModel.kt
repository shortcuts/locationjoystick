package com.locationjoystick.feature.settings.impl

import androidx.lifecycle.ViewModel
import com.locationjoystick.feature.settings.api.SettingsExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsExtensionsViewModel
    @Inject
    constructor(
        val extensions: Set<@JvmSuppressWildcards SettingsExtension>,
    ) : ViewModel()
