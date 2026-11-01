package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjPillShape
import com.locationjoystick.core.designsystem.LjSheetShape

/**
 * Bottom sheets open fully expanded so action buttons are not trapped under the default
 * half-height peek (which forces the user to drag the sheet up).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLjSheetState(): SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

/** Drag handle for hand-drawn sheets. The parent's alignment modifier places it. */
@Composable
fun LjSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .width(32.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), LjPillShape),
    )
}

/** Bottom sheet with the shared Quiet Utility look: surface container, sheet corner shape, theme scrim. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LjModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberLjSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = LjSheetShape,
        scrimColor = MaterialTheme.colorScheme.scrim,
        content = content,
    )
}
