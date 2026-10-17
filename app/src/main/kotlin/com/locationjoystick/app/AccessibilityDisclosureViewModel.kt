package com.locationjoystick.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Records the user's answer to the accessibility disclosure launched via [MainActivity]'s intent. */
@HiltViewModel
class AccessibilityDisclosureViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        fun record(accepted: Boolean) {
            viewModelScope.launch { settingsRepository.recordAccessibilityDisclosure(accepted) }
        }
    }
