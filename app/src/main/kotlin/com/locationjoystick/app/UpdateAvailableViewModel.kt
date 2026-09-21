package com.locationjoystick.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.isNewerVersion
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.data.UpdateCheckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateAvailableUiState(
    val visible: Boolean,
    val latestVersion: String,
)

/** Drives the home-screen "update available" badge (see docs/features/update-check.md). */
@HiltViewModel
class UpdateAvailableViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val updateCheckRepository: UpdateCheckRepository,
    ) : ViewModel() {
        val uiState: StateFlow<UpdateAvailableUiState> =
            combine(
                settingsRepository.getUpdateCheckCachedLatestVersion(),
                settingsRepository.getUpdateCheckDismissedVersion(),
            ) { latestVersion, dismissedVersion ->
                UpdateAvailableUiState(
                    visible =
                        latestVersion.isNotEmpty() &&
                            latestVersion != dismissedVersion &&
                            isNewerVersion(latestVersion, AppConstants.AppInfo.VERSION_NAME),
                    latestVersion = latestVersion,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UpdateAvailableUiState(visible = false, latestVersion = ""),
            )

        init {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val lastCheckedAtMs = settingsRepository.getUpdateCheckLastCheckedAtMs().first()
                if (now - lastCheckedAtMs < AppConstants.UpdateCheckConstants.CHECK_INTERVAL_MS) return@launch
                // Stamped before the fetch so a failure still waits a day instead of retrying every launch.
                settingsRepository.setUpdateCheckLastCheckedAtMs(now)
                val latestVersion = updateCheckRepository.fetchLatestVersion() ?: return@launch
                settingsRepository.setUpdateCheckCachedLatestVersion(latestVersion)
            }
        }

        /** Opening the release page and the dismiss "X" both acknowledge this version. */
        fun dismiss(version: String) {
            viewModelScope.launch { settingsRepository.setUpdateCheckDismissedVersion(version) }
        }
    }
