package com.locationjoystick.core.designsystem.component

import com.locationjoystick.core.common.constants.AppConstants.AnimationConstants
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** One reversible timeline: separation finishes exactly where expansion starts. */
internal data class LiquidLabelFrame(
    val separation: Float,
    val expansion: Float,
    val pinch: Float,
    val retraction: Float,
    val wobble: Float,
    val textAlpha: Float,
)

internal fun liquidLabelFrame(progress: Float): LiquidLabelFrame {
    val t = progress.coerceIn(0f, 1f)
    val pinchStart = AnimationConstants.LIQUID_LABEL_PINCH_START
    val pinchEnd = AnimationConstants.LIQUID_LABEL_PINCH_END
    val separationEnd = AnimationConstants.LIQUID_LABEL_SEPARATION_END
    val expansionEnd = AnimationConstants.LIQUID_LABEL_EXPANSION_END
    val textStart = AnimationConstants.LIQUID_LABEL_TEXT_START
    val recoil = ((t - pinchEnd) / (separationEnd - pinchEnd)).coerceIn(0f, 1f)
    return LiquidLabelFrame(
        separation = smooth(t / separationEnd),
        expansion = 1f - (1f - ((t - separationEnd) / (expansionEnd - separationEnd)).coerceIn(0f, 1f)).pow(3),
        pinch = smooth((t - pinchStart) / (pinchEnd - pinchStart)),
        retraction = smooth(recoil),
        wobble = sin(recoil * PI.toFloat() * 3f) * (1f - recoil).pow(2),
        textAlpha = smooth((t - textStart) / (expansionEnd - textStart)),
    )
}

private fun smooth(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
