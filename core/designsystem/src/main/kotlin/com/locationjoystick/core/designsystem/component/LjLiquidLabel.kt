package com.locationjoystick.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.common.constants.AppConstants.AnimationConstants
import com.locationjoystick.core.designsystem.UiConstants
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Keeps interrupted transitions continuous and respects the system animator duration scale. */
@Composable
fun rememberLiquidLabelProgress(
    visible: Boolean,
    animate: Boolean = true,
): State<Float> {
    val preview = LocalInspectionMode.current
    val progress = remember { Animatable(if (preview && visible) 1f else 0f) }
    LaunchedEffect(visible, animate) {
        val target = if (visible) 1f else 0f
        if (animate) {
            val fullDuration =
                if (visible) AnimationConstants.LIQUID_LABEL_ENTER_MS else AnimationConstants.LIQUID_LABEL_EXIT_MS
            val duration = (fullDuration * abs(target - progress.value)).roundToInt()
            progress.animateTo(target, tween(durationMillis = duration, easing = LinearEasing))
        } else {
            progress.snapTo(target)
        }
    }
    return progress.asState()
}

/**
 * A droplet separates from a circular source, then immediately expands into a rounded label.
 *
 * Place the source circle under the left (or [expandLeft] right) edge, vertically centered.
 * The source's interior is excluded from drawing, preserving its icon and click handling.
 * This component owns no activity state or window; it can be used in a layout or a popup.
 * Reserve the full measured size throughout entry/exit to keep the source aligned.
 */
@Composable
fun LjLiquidLabel(
    text: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    expandLeft: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    sourceDiameter: Dp = UiConstants.FAB_CONTAINER_SIZE,
    gap: Dp = AnimationConstants.LIQUID_LABEL_GAP_DP.dp,
    cornerRadius: Dp = 20.dp,
    maxLabelWidth: Dp = 200.dp,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textLayout =
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = with(density) { (maxLabelWidth - 32.dp).roundToPx().coerceAtLeast(1) }),
        )
    val labelHeight = maxOf(32.dp, with(density) { textLayout.size.height.toDp() } + 12.dp)
    val labelWidth =
        maxOf(labelHeight, 88.dp, with(density) { textLayout.size.width.toDp() } + 32.dp)
            .coerceAtMost(maxLabelWidth)
    val height = maxOf(sourceDiameter, labelHeight) + 10.dp
    val neck = remember { Path() }
    val sourceCutout = remember { Path() }

    Canvas(
        modifier =
            modifier
                .size(width = sourceDiameter + gap + labelWidth, height = height)
                .semantics { contentDescription = text },
    ) {
        val fraction = progress()
        if (fraction <= 0f) return@Canvas
        val frame = liquidLabelFrame(fraction)
        val sourceRadius = sourceDiameter.toPx() / 2f
        val dropRadius = minOf(32.dp.toPx(), sourceDiameter.toPx()) / 2f
        val cy = size.height / 2f
        val finalCx = sourceRadius * 2f + gap.toPx() + dropRadius
        val cx = mix(sourceRadius, finalCx, frame.separation)
        val width = mix(dropRadius * 2f, labelWidth.toPx(), frame.expansion)
        val animatedHeight = mix(dropRadius * 2f, labelHeight.toPx(), frame.expansion)
        val radius = mix(dropRadius, minOf(cornerRadius.toPx(), labelHeight.toPx() / 2f), frame.expansion)

        // Mirror geometry only. Text always reads normally, including at the left screen edge.
        scale(if (expandLeft) -1f else 1f, 1f) {
            sourceCutout.reset()
            sourceCutout.addOval(Rect(0f, cy - sourceRadius, sourceRadius * 2f, cy + sourceRadius))
            clipPath(sourceCutout, ClipOp.Difference) {
                drawLiquidNeck(neck, frame, sourceRadius, dropRadius, cx, cy, containerColor)
                scale(1f + 0.1f * frame.wobble, 1f - 0.08f * frame.wobble, Offset(cx, cy)) {
                    drawRoundRect(
                        color = containerColor,
                        topLeft = Offset(cx - dropRadius, cy - animatedHeight / 2f),
                        size = Size(width, animatedHeight),
                        cornerRadius = CornerRadius(radius),
                    )
                }
            }
        }
        val textCenter = if (expandLeft) size.width - (cx - dropRadius + width / 2f) else cx - dropRadius + width / 2f
        drawText(
            textLayoutResult = textLayout,
            color = contentColor,
            topLeft = Offset(textCenter - textLayout.size.width / 2f, cy - textLayout.size.height / 2f),
            alpha = frame.textAlpha,
        )
    }
}

private fun DrawScope.drawLiquidNeck(
    path: Path,
    frame: LiquidLabelFrame,
    sourceRadius: Float,
    dropRadius: Float,
    cx: Float,
    cy: Float,
    color: Color,
) {
    if (frame.separation >= 1f || cx <= sourceRadius + (sourceRadius + dropRadius) * 0.7f) return
    val angle = PI.toFloat() / 4f * (1f - frame.retraction)
    val leftX = sourceRadius + sourceRadius * cos(angle)
    val leftH = sourceRadius * sin(angle)
    val rightX = cx - dropRadius * cos(angle)
    val rightH = dropRadius * sin(angle)
    val middle = (sourceRadius * 2f + cx - dropRadius) / 2f
    val unit = sourceRadius / 34f
    path.reset()
    path.moveTo(leftX, cy - leftH)
    if (frame.pinch < 1f) {
        val threadH = 14f * unit * (1f - frame.pinch)
        path.cubicTo(leftX + 9f * unit, cy - leftH * 0.5f, middle - 8f * unit, cy - threadH, middle, cy - threadH)
        path.cubicTo(middle + 8f * unit, cy - threadH, rightX - 7f * unit, cy - rightH * 0.45f, rightX, cy - rightH)
        path.lineTo(rightX, cy + rightH)
        path.cubicTo(rightX - 7f * unit, cy + rightH * 0.45f, middle + 8f * unit, cy + threadH, middle, cy + threadH)
        path.cubicTo(middle - 8f * unit, cy + threadH, leftX + 9f * unit, cy + leftH * 0.5f, leftX, cy + leftH)
        path.close()
    } else {
        val leftTip = mix(middle, sourceRadius * 2f, frame.retraction)
        val rightTip = mix(middle, cx - dropRadius, frame.retraction)
        val leftControl = mix(leftX, leftTip, 0.6f)
        val rightControl = mix(rightX, rightTip, 0.6f)
        path.cubicTo(leftX, cy - leftH * 0.5f, leftControl, cy, leftTip, cy)
        path.cubicTo(leftControl, cy, leftX, cy + leftH * 0.5f, leftX, cy + leftH)
        path.close()
        path.moveTo(rightX, cy - rightH)
        path.cubicTo(rightX, cy - rightH * 0.5f, rightControl, cy, rightTip, cy)
        path.cubicTo(rightControl, cy, rightX, cy + rightH * 0.5f, rightX, cy + rightH)
        path.close()
    }
    drawPath(path, color)
}

private fun mix(
    start: Float,
    end: Float,
    fraction: Float,
): Float = start + (end - start) * fraction

@Preview
@Composable
private fun LjLiquidLabelPreview() {
    Box(contentAlignment = Alignment.CenterStart) {
        Box(Modifier.size(42.dp).background(Color.Black, CircleShape))
        LjLiquidLabel(text = "Paused", progress = { 1f }, containerColor = Color.Black, contentColor = Color.White)
    }
}
