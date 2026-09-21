package com.locationjoystick.feature.map.impl

import androidx.lifecycle.Lifecycle

/**
 * Modal bottom sheets call [androidx.compose.material3.ModalBottomSheet]'s onDismissRequest when
 * the user swipes them away *and* when the map leaves composition (Idle redirect on ON_STOP).
 * Only the in-foreground swipe/back should clear a pending GPX open; teardown must leave it
 * so the next map ViewModel can still show Open GPX.
 */
internal fun shouldHonorPasteSheetDismiss(lifecycleState: Lifecycle.State): Boolean = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
