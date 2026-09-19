package com.locationjoystick.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsCaptureViewModel
    @Inject
    constructor(
        private val captureRepository: CaptureCoordinatesRepository,
    ) : ViewModel() {
        val captureModeEnabled: StateFlow<Boolean> =
            captureRepository.captureModeEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val previousBrowserPackage: StateFlow<String?> =
            captureRepository.previousBrowserPackage
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun rememberPreviousBrowser(packageName: String?) {
            val trimmed = packageName?.trim().orEmpty()
            if (trimmed.isEmpty()) return
            viewModelScope.launch { captureRepository.setPreviousBrowserPackage(trimmed) }
        }
    }
