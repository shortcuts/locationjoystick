package com.locationjoystick.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

/** Snackbar host every screen shares, so a message looks the same wherever it appears. */
@Composable
fun LjSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState) { data -> Snackbar(data, shape = MaterialTheme.shapes.small) }
}
