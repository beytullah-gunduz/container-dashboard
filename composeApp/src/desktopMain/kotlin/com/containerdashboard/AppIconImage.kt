package com.containerdashboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * AWT / Java2D render of the container dashboard app icon — a cardboard
 * package box with tape, matching the Compose [AppIconPainter]. Used at
 * runtime to set the macOS Dock icon via `java.awt.Taskbar` during dev
 * runs (`./gradlew run`). A nearly identical renderer lives in
 * `composeApp/build.gradle.kts` for the build-time `.icns` generation —
 * the two must stay visually in sync.
 */
fun renderAppIconAwt(size: Int): BufferedImage {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    val padding = size * 0.1f
    val boxWidth = size - padding * 2
    val boxHeight = size - padding * 2

    val boxColor = Color(0xD4, 0xA5, 0x74)
    val boxDark = Color(0xB8, 0x95, 0x6A)
    val boxLight = Color(0xE8, 0xC9, 0xA0)
    val tapeColor = Color(0xF5, 0xE6, 0xD3)
    val strokeColor = Color(0x8B, 0x73, 0x55)

    fun poly(vararg points: Pair<Float, Float>): Polygon {
        val xs = IntArray(points.size)
        val ys = IntArray(points.size)
        points.forEachIndexed { i, (x, y) ->
            xs[i] = x.toInt()
            ys[i] = y.toInt()
        }
        return Polygon(xs, ys, points.size)
    }

    fun fillAndStroke(polygon: Polygon, fill: Color, strokeWidth: Float) {
        g.color = fill
        g.fill(polygon)
        g.color = strokeColor
        g.stroke = BasicStroke(strokeWidth.coerceAtLeast(1f))
        g.draw(polygon)
    }

    // Box front face
    fillAndStroke(
        poly(
            padding to (padding + boxHeight * 0.25f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth) to (padding + boxHeight * 0.25f),
            (padding + boxWidth) to (padding + boxHeight * 0.85f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight),
            padding to (padding + boxHeight * 0.85f),
        ),
        boxColor,
        size * 0.006f,
    )

    // Left face (darker)
    fillAndStroke(
        poly(
            padding to (padding + boxHeight * 0.25f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight),
            padding to (padding + boxHeight * 0.85f),
        ),
        boxDark,
        size * 0.004f,
    )

    // Right face (lighter)
    fillAndStroke(
        poly(
            (padding + boxWidth * 0.5f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth) to (padding + boxHeight * 0.25f),
            (padding + boxWidth) to (padding + boxHeight * 0.85f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight),
        ),
        boxLight,
        size * 0.004f,
    )

    // Top (lid)
    fillAndStroke(
        poly(
            (padding + boxWidth * 0.5f) to padding,
            (padding + boxWidth) to (padding + boxHeight * 0.15f),
            (padding + boxWidth) to (padding + boxHeight * 0.25f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight * 0.4f),
            padding to (padding + boxHeight * 0.25f),
            padding to (padding + boxHeight * 0.15f),
        ),
        boxLight,
        size * 0.006f,
    )

    // Tape on top
    val tapeWidth = boxWidth * 0.15f
    fillAndStroke(
        poly(
            (padding + boxWidth * 0.5f - tapeWidth * 0.5f) to (padding + boxHeight * 0.02f),
            (padding + boxWidth * 0.5f + tapeWidth * 0.5f) to (padding + boxHeight * 0.02f),
            (padding + boxWidth * 0.5f + tapeWidth * 0.5f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth * 0.5f - tapeWidth * 0.5f) to (padding + boxHeight * 0.4f),
        ),
        tapeColor,
        size * 0.003f,
    )

    // Front tape
    fillAndStroke(
        poly(
            (padding + boxWidth * 0.5f - tapeWidth * 0.3f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth * 0.5f + tapeWidth * 0.3f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth * 0.5f + tapeWidth * 0.3f) to (padding + boxHeight),
            (padding + boxWidth * 0.5f - tapeWidth * 0.3f) to (padding + boxHeight),
        ),
        tapeColor,
        size * 0.003f,
    )

    g.dispose()
    return img
}
