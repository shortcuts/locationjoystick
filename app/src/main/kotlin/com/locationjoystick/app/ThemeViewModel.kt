package com.locationjoystick.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the user's theme preference so [MainActivity] can apply light/dark [com.locationjoystick.core.designsystem.LjTheme] hot. */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> =
            settingsRepository.getThemeMode().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeMode.DARK,
            )
    }
