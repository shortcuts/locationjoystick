package com.locationjoystick.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.WhatsNewEntry
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
        val groups: List<WhatsNewCategoryGroup>,
    ) : WhatsNewLoadState

    data object Failed : WhatsNewLoadState
}

data class WhatsNewScopeGroup(
    val scope: String,
    val entries: List<WhatsNewEntry>,
)

data class WhatsNewCategoryGroup(
    val label: String,
    val scopeGroups: List<WhatsNewScopeGroup>,
)

/** feat before fix, empty categories omitted; scopes alphabetical within a category. */
fun groupWhatsNewEntries(entries: List<WhatsNewEntry>): List<WhatsNewCategoryGroup> {
    val byCategory = entries.groupBy { it.category }
    return listOf("feat" to "New & Improved", "fix" to "Fixes").mapNotNull { (category, label) ->
        val inCategory = byCategory[category].orEmpty()
        if (inCategory.isEmpty()) {
            null
        } else {
            WhatsNewCategoryGroup(
                label = label,
                scopeGroups =
                    inCategory
                        .groupBy { it.scope }
                        .toSortedMap()
                        .map { (scope, scopeEntries) -> WhatsNewScopeGroup(scope, scopeEntries) },
            )
        }
    }
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
            settingsRepository
                .getWhatsNewLastSeenVersion()
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

        fun loadEntries() {
            _loadState.value = WhatsNewLoadState.Loading
            viewModelScope.launch {
                val entries = whatsNewRepository.fetchEntries(AppConstants.AppInfo.VERSION_NAME)
                _loadState.value =
                    if (entries != null) {
                        WhatsNewLoadState.Loaded(groupWhatsNewEntries(entries))
                    } else {
                        WhatsNewLoadState.Failed
                    }
            }
        }
    }
