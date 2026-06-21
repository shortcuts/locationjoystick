package com.locationjoystick.core.location

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin Hilt ViewModel wrapping [MapController.isSpoofing] / [MapController.toggleSpoofing] so any
 * screen's top bar can drive the global start/stop spoofing state without each feature's own
 * ViewModel needing to depend on [MapController] directly.
 */
@HiltViewModel
class SpoofToggleViewModel
    @Inject
    constructor(
        private val mapController: MapController,
    ) : ViewModel() {
        val isSpoofing: StateFlow<Boolean> = mapController.isSpoofing

        fun toggle() {
            mapController.toggleSpoofing()
        }
    }
