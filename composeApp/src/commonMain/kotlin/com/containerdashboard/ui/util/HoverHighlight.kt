package com.containerdashboard.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * Subtle hover overlay for interactive rows.
 *
 * Stacks a translucent wash over whatever background the row already
 * has (selected / checked / default surface), so the wash is additive
 * and works regardless of selection state.
 *
 * Use as the **last** modifier in the chain before `.clickable(...)`
 * (or apply to the row's inner container if the click modifier has
 * already been set).
 */
fun Modifier.hoverHighlight(
    color: Color = Color.Unspecified,
    alpha: Float = 0.04f,
    shape: Shape = RectangleShape,
): Modifier =
    composed {
        val source = remember { MutableInteractionSource() }
        val hovered by source.collectIsHoveredAsState()
        val tint =
            if (color == Color.Unspecified) {
                androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            } else {
                color
            }
        this
            .hoverable(source)
            .then(
                if (hovered) Modifier.background(tint.copy(alpha = alpha), shape) else Modifier,
            )
    }
