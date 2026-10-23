package com.containerdashboard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.ThemeStyleState
import com.containerdashboard.ui.theme.appStrokeCap
import kotlin.math.roundToInt

/** Segments of the Retro half gauge: 10 degrees each, so one segment is about 5.6 % of the value. */
internal const val GAUGE_SEGMENTS = 18

/** Degrees left dark between two Retro gauge segments. */
internal const val GAUGE_GAP_DEGREES = 3f

/**
 * A half-ring gauge. Default: a track arc with a smooth value arc over it. Retro: [GAUGE_SEGMENTS]
 * segments with [GAUGE_GAP_DEGREES] gaps, [litCells] of them lit in [color], the rest in
 * [trackColor].
 */
@Composable
fun SemiDonutGauge(
    percent: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    Box(
        modifier = modifier.size(width = 72.dp, height = 42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            // Square bounding box for the full-circle arc; its bottom half lies below
            // the canvas, so only the top semicircle is drawn.
            val arcSide = size.width - stroke
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(arcSide, arcSide)
            if (ThemeStyleState.isRetro) {
                val lit = litCells(fraction, GAUGE_SEGMENTS)
                val step = 180f / GAUGE_SEGMENTS
                for (index in 0 until GAUGE_SEGMENTS) {
                    drawArc(
                        color = if (index < lit) color else trackColor,
                        startAngle = 180f + index * step + GAUGE_GAP_DEGREES / 2,
                        sweepAngle = step - GAUGE_GAP_DEGREES,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = appStrokeCap),
                    )
                }
            } else {
                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = appStrokeCap),
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = appStrokeCap),
                )
            }
        }
        Text(
            text = "${percent.roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/**
 * How many of [cells] to light for [fraction]: none for NaN or a value at or below 0; otherwise the
 * nearest whole cell of `min(fraction, 1) x cells`, at least one, so any non-zero value shows.
 */
internal fun litCells(
    fraction: Float,
    cells: Int,
): Int {
    if (fraction.isNaN() || fraction <= 0f) return 0
    return (fraction.coerceAtMost(1f) * cells).roundToInt().coerceIn(1, cells)
}
