package com.locationjoystick.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.WhatsNewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WhatsNewLoadState {
    data object Loading : WhatsNewLoadState

    data class Loaded(
        val highlights: List<String>,
    ) : WhatsNewLoadState

    data object Failed : WhatsNewLoadState
}

/** Drives the app-level "What's New" badge (see docs/features/whats-new.md). */
@HiltViewModel
class WhatsNewViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val whatsNewRepository: WhatsNewRepository,
    ) : ViewModel() {
        val hasUnseenUpdate: StateFlow<Boolean> =
            settingsRepository.getWhatsNewLastSeenVersion()
                .map { it != AppConstants.AppInfo.VERSION_NAME }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )

        private val _loadState = MutableStateFlow<WhatsNewLoadState>(WhatsNewLoadState.Loading)
        val loadState: StateFlow<WhatsNewLoadState> = _loadState.asStateFlow()

        /** Tapping the badge is the acknowledgment — reviewing the fetched content further is optional. */
        fun markSeen() {
            viewModelScope.launch {
                settingsRepository.setWhatsNewLastSeenVersion(AppConstants.AppInfo.VERSION_NAME)
            }
        }

        fun loadHighlights() {
            _loadState.value = WhatsNewLoadState.Loading
            viewModelScope.launch {
                val highlights = whatsNewRepository.fetchHighlights(AppConstants.AppInfo.VERSION_NAME)
                _loadState.value =
                    if (highlights != null) {
                        WhatsNewLoadState.Loaded(highlights)
                    } else {
                        WhatsNewLoadState.Failed
                    }
            }
        }
    }
