package com.containerdashboard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CircularSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 1f..5f,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    label: (Float) -> String = { "${it.toInt()}s" }
) {
    val startAngle = 135f
    val sweepAngle = 270f
    val range = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)

    fun positionToValue(position: Offset, width: Float, height: Float): Float? {
        val center = Offset(width / 2f, height / 2f)
        val dx = position.x - center.x
        val dy = position.y - center.y

        var angle = atan2(dy, dx) * (180f / PI.toFloat())
        if (angle < 0f) angle += 360f

        var arcPosition = angle - startAngle
        if (arcPosition < 0f) arcPosition += 360f

        return if (arcPosition <= sweepAngle) {
            val newFraction = arcPosition / sweepAngle
            val rawValue = valueRange.start + newFraction * range
            rawValue.coerceIn(valueRange).roundToInt().toFloat()
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(72.dp)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    positionToValue(
                        down.position,
                        size.width.toFloat(),
                        size.height.toFloat()
                    )?.let { onValueChange(it) }

                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                positionToValue(
                                    change.position,
                                    size.width.toFloat(),
                                    size.height.toFloat()
                                )?.let { onValueChange(it) }
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 6.dp.toPx()
            val padding = strokeWidth / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val arcTopLeft = Offset(padding, padding)

            // Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active progress
            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * fraction,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Thumb
            val thumbAngleRad = (startAngle + sweepAngle * fraction) * (PI.toFloat() / 180f)
            val radius = arcSize.width / 2
            val arcCenter = Offset(
                arcTopLeft.x + arcSize.width / 2,
                arcTopLeft.y + arcSize.height / 2
            )
            val thumbCenter = Offset(
                x = arcCenter.x + radius * cos(thumbAngleRad),
                y = arcCenter.y + radius * sin(thumbAngleRad)
            )

            // Thumb outer (white border)
            drawCircle(
                color = Color.White,
                radius = strokeWidth * 1.0f,
                center = thumbCenter
            )
            // Thumb inner
            drawCircle(
                color = thumbColor,
                radius = strokeWidth * 0.7f,
                center = thumbCenter
            )
        }

        // Center value text
        Text(
            text = label(value),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
