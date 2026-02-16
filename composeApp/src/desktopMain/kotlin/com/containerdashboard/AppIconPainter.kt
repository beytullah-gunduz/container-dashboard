package com.containerdashboard

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

/**
 * Custom painter for the app icon - a package/container box (📦)
 */
class AppIconPainter : Painter() {
    override val intrinsicSize = Size(512f, 512f)

    override fun DrawScope.onDraw() {
        val padding = size.width * 0.1f
        val boxWidth = size.width - padding * 2
        val boxHeight = size.height - padding * 2

        // Colors
        val boxColor = Color(0xFFD4A574) // Cardboard brown
        val boxDark = Color(0xFFB8956A) // Darker brown for depth
        val boxLight = Color(0xFFE8C9A0) // Lighter brown for highlights
        val tapeColor = Color(0xFFF5E6D3) // Tape color
        val strokeColor = Color(0xFF8B7355) // Outline

        // Box front face
        val frontPath =
            Path().apply {
                moveTo(padding, padding + boxHeight * 0.25f)
                lineTo(padding + boxWidth * 0.5f, padding + boxHeight * 0.4f)
                lineTo(padding + boxWidth, padding + boxHeight * 0.25f)
                lineTo(padding + boxWidth, padding + boxHeight * 0.85f)
                lineTo(padding + boxWidth * 0.5f, padding + boxHeight)
                lineTo(padding, padding + boxHeight * 0.85f)
                close()
            }
        drawPath(frontPath, boxColor, style = Fill)
        drawPath(frontPath, strokeColor, style = Stroke(width = 3f))

        // Box left side (darker)
        val leftPath =
            Path().apply {
                moveTo(padding, padding + boxHeight * 0.25f)
                lineTo(padding + boxWidth * 0.5f, padding + boxHeight * 0.4f)
                lineTo(padding + boxWidth * 0.5f, padding + boxHeight)
                lineTo(padding, padding + boxHeight * 0.85f)
                close()
            }
        drawPath(leftPath, boxDark, style = Fill)
        drawPath(leftPath, strokeColor, style = Stroke(width = 2f))

        // Box right side (lighter)
        val rightPath =
            Path().apply {
                moveTo(padding + boxWidth * 0.5f, padding + boxHeight * 0.4f)
                lineTo(padding + boxWidth, padding + boxHeight * 0.25f)
                lineTo(padding + boxWidth, padding + boxHeight * 0.85f)
                lineTo(padding + boxWidth * 0.5f, padding + boxHeight)
                close()
            }
        drawPath(rightPath, boxLight, style = Fill)
        drawPath(rightPath, strokeColor, style = Stroke(width = 2f))

        // Box top (lid)
        val topPath =
            Path().apply {
                moveTo(padding + boxWidth * 0.5f, padding)
                lineTo(padding + boxWidth, padding + boxHeight * 0.15f)
                lineTo(padding + boxWidth, padding + boxHeight * 0.25f)
                lineTo(padding + boxWidth * 0.5f, padding + boxHeight * 0.4f)
                lineTo(padding, padding + boxHeight * 0.25f)
                lineTo(padding, padding + boxHeight * 0.15f)
                close()
            }
        drawPath(topPath, boxLight, style = Fill)
        drawPath(topPath, strokeColor, style = Stroke(width = 3f))

        // Tape on top (horizontal)
        val tapeWidth = boxWidth * 0.15f
        val tapePath =
            Path().apply {
                moveTo(padding + boxWidth * 0.5f - tapeWidth * 0.5f, padding + boxHeight * 0.02f)
                lineTo(padding + boxWidth * 0.5f + tapeWidth * 0.5f, padding + boxHeight * 0.02f)
                lineTo(padding + boxWidth * 0.5f + tapeWidth * 0.5f, padding + boxHeight * 0.4f)
                lineTo(padding + boxWidth * 0.5f - tapeWidth * 0.5f, padding + boxHeight * 0.4f)
                close()
            }
        drawPath(tapePath, tapeColor, style = Fill)
        drawPath(tapePath, strokeColor, style = Stroke(width = 1.5f))

        // Tape continuing down front
        val frontTapePath =
            Path().apply {
                moveTo(padding + boxWidth * 0.5f - tapeWidth * 0.3f, padding + boxHeight * 0.4f)
                lineTo(padding + boxWidth * 0.5f + tapeWidth * 0.3f, padding + boxHeight * 0.4f)
                lineTo(padding + boxWidth * 0.5f + tapeWidth * 0.3f, padding + boxHeight)
                lineTo(padding + boxWidth * 0.5f - tapeWidth * 0.3f, padding + boxHeight)
                close()
            }
        drawPath(frontTapePath, tapeColor, style = Fill)
        drawPath(frontTapePath, strokeColor, style = Stroke(width = 1.5f))
    }
}
