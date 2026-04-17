package com.containerdashboard.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing

/**
 * Shimmer-style placeholder rows shown while a list is loading for the first
 * time. Each row has a name stripe and a subtitle stripe so the skeleton
 * roughly mirrors the shape of the real row it replaces.
 */
@Composable
fun ListRowSkeleton(
    rowCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    val alpha = rememberSkeletonAlpha()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(rowCount) {
            SkeletonRow(alpha = alpha)
        }
    }
}

@Composable
private fun SkeletonRow(alpha: Float) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBar(width = 20.dp, height = 20.dp, alpha = alpha)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBar(width = 180.dp, height = 12.dp, alpha = alpha)
            SkeletonBar(width = 120.dp, height = 10.dp, alpha = alpha)
        }
    }
}

/**
 * A single rounded placeholder bar whose alpha is driven by the caller so a
 * whole screen can share one infinite transition instead of one per row.
 */
@Composable
fun SkeletonBar(
    width: Dp,
    height: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(width = width, height = height)
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

/**
 * Animated alpha that matches [ListRowSkeleton]. Useful when callers want to
 * drive their own custom skeleton shapes while sharing the same pulse.
 */
@Composable
fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton-alpha")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "skeleton-alpha-value",
    )
    return alpha
}
