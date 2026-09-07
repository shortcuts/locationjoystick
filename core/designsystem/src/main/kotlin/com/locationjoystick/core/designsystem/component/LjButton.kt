package com.locationjoystick.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjAccent
import com.locationjoystick.core.designsystem.LjBg
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.LjText

/** Press feedback shared by every LjButton variant: 0.96 scale, never lower (feels exaggerated below). */
@Composable
internal fun rememberPressScale(interactionSource: MutableInteractionSource): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "pressScale")
    return scale
}

@Composable
fun LjButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp).scale(scale),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = LjSpacing.lg, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun LjOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp).scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = LjSpacing.lg, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun LjTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp).scale(scale),
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun LjPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LjButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun <T> LjSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) LjAccent else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segmentedControlContainerColor",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) LjBg else LjText,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segmentedControlContentColor",
            )
            Button(
                onClick = { onSelect(value) },
                modifier = Modifier.padding(horizontal = 2.dp).defaultMinSize(minHeight = 48.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = LjSpacing.sm),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
