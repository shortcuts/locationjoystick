package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LjCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            content = content,
        )
    } else {
        ElevatedCard(
            modifier = modifier,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            content = content,
        )
    }
}

@Composable
fun LjOutlinedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier,
            content = content,
        )
    } else {
        OutlinedCard(
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
fun LjSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        content = content,
    )
}
