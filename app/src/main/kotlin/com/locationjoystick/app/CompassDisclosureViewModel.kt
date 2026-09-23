package com.locationjoystick.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Records the user's answer to the compass accessibility disclosure shown from the widget overlay. */
@HiltViewModel
class CompassDisclosureViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        fun record(accepted: Boolean) {
            viewModelScope.launch {
                settingsRepository.setCompassDisclosureChoice(
                    if (accepted) {
                        AppConstants.CompassTrackingConstants.DISCLOSURE_ACCEPTED
                    } else {
                        AppConstants.CompassTrackingConstants.DISCLOSURE_DECLINED
                    },
                )
            }
        }
    }
