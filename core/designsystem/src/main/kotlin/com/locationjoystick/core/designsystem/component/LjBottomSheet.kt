package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom sheets open fully expanded so action buttons are not trapped under the default
 * half-height peek (which forces the user to drag the sheet up).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLjSheetState(): SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

/** Bottom sheet with the shared Quiet Utility look: surface container, 16 dp top corners, theme scrim. */
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
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        scrimColor = MaterialTheme.colorScheme.scrim,
        content = content,
    )
}
