package com.locationjoystick.feature.settings.impl

import androidx.lifecycle.ViewModel
import com.locationjoystick.feature.settings.api.SettingsExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsExtensionsViewModel
    @Inject
    constructor(
        allExtensions: Set<@JvmSuppressWildcards SettingsExtension>,
    ) : ViewModel() {
        val extensions = allExtensions.filter { it.isAvailable }
    }
