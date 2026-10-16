package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjSpacing

/** Control shown by [LjSelectionRow]: radio (pick one), checkbox (pick many) or switch (on/off). */
enum class LjSelectionMode { Single, Multi, Toggle }

/** Card: bordered when selected, for step-level choices. Flat: compact row for lists and sheets. */
enum class LjSelectionStyle { Card, Flat }

/** One labelled selection row; the whole row is the hit area and carries the role semantics. */
@Composable
fun LjSelectionRow(
    mode: LjSelectionMode,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    textColor: Color = Color.Unspecified,
    descriptionColor: Color? = null,
    icon: ImageVector? = null,
    style: LjSelectionStyle = LjSelectionStyle.Flat,
    trailing: @Composable (() -> Unit)? = null,
) {
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val card = style == LjSelectionStyle.Card
    val action =
        when (mode) {
            LjSelectionMode.Single ->
                Modifier.selectable(selected = checked, enabled = enabled, role = Role.RadioButton) {
                    if (!checked) onCheckedChange(true)
                }
            LjSelectionMode.Multi ->
                Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            LjSelectionMode.Toggle ->
                Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        }
    val content: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(
            modifier = rowModifier.padding(if (card) PaddingValues(12.dp) else PaddingValues(vertical = LjSpacing.sm)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (mode) {
                LjSelectionMode.Single -> RadioButton(selected = checked, onClick = null, enabled = enabled)
                LjSelectionMode.Multi -> Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                LjSelectionMode.Toggle -> Unit
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabledColor,
                )
                Spacer(modifier = Modifier.width(LjSpacing.xs))
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(
                            start =
                                if (mode == LjSelectionMode.Toggle) {
                                    0.dp
                                } else if (card) {
                                    8.dp
                                } else {
                                    LjSpacing.sm
                                },
                        ),
            ) {
                Text(
                    text = title,
                    style = if (card) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color = if (enabled) textColor else disabledColor,
                )
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = descriptionColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (mode == LjSelectionMode.Toggle) {
                Switch(checked = checked, onCheckedChange = null, enabled = enabled)
            }
            trailing?.invoke()
        }
    }
    if (card) {
        Card(
            modifier = modifier.fillMaxWidth().then(action),
            border = if (checked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        ) { content(Modifier.fillMaxWidth()) }
    } else {
        content(modifier.fillMaxWidth().then(action))
    }
}
