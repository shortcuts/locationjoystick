package com.locationjoystick.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

/**
 * Bottom sheets open fully expanded so action buttons are not trapped under the default
 * half-height peek (which forces the user to drag the sheet up).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLjSheetState(): SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
