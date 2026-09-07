package com.locationjoystick.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Animated placeholder brush for skeleton rows — a translating gradient built only from
 * theme surface tokens, so it tracks light/dark automatically instead of a hardcoded color.
 */
@Composable
private fun rememberLjShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "ljShimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "ljShimmerTranslate",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = lerp(base, MaterialTheme.colorScheme.surface, 0.6f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 0f),
    )
}

@Composable
private fun LjShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    Box(modifier = modifier.background(brush = rememberLjShimmerBrush(), shape = shape))
}

/**
 * Skeleton stand-in for one [LjListItemCard] row (Routes/Favorites lists) — wraps the real
 * component so shape, elevation, and padding always match the loaded card exactly.
 */
@Composable
private fun LjListItemCardSkeleton(
    modifier: Modifier = Modifier,
    trailingIconCount: Int = 1,
) {
    LjListItemCard(
        modifier = modifier,
        trailing = {
            repeat(trailingIconCount) {
                LjShimmerBlock(modifier = Modifier.size(40.dp), shape = CircleShape)
            }
        },
    ) {
        LjShimmerBlock(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp))
        Spacer(modifier = Modifier.height(6.dp))
        LjShimmerBlock(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
    }
}

/**
 * Fixed-count skeleton list shown while Routes/Favorites load, replacing the center-screen
 * spinner — Room reads are near-instant, so this fills the screen with the eventual card
 * shape instead of a one-frame spinner flash.
 */
@Composable
fun LjListItemCardSkeletonList(
    modifier: Modifier = Modifier,
    trailingIconCount: Int = 1,
    count: Int = 4,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            LjListItemCardSkeleton(
                modifier = Modifier.fillMaxWidth(),
                trailingIconCount = trailingIconCount,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LjListItemCardSkeletonListPreview() {
    LjListItemCardSkeletonList(trailingIconCount = 2)
}
