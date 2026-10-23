package com.containerdashboard.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val xs = 3.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
}

object IconSize {
    val sm = 16.dp
    val md = 18.dp
    val lg = 20.dp
}

/** A rounded shape of this radius; square in Retro. */
val Dp.appCorner: RoundedCornerShape
    get() = RoundedCornerShape(if (ThemeStyleState.isRetro) 0.dp else this)

/** [appCorner] with a radius per corner. */
fun appCorners(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): RoundedCornerShape =
    if (ThemeStyleState.isRetro) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomEnd = bottomEnd, bottomStart = bottomStart)
    }

/** A circle; a square in Retro. */
val appRoundShape: CornerBasedShape
    get() = if (ThemeStyleState.isRetro) RoundedCornerShape(0.dp) else CircleShape

/** Line-end cap for arcs and strokes: round in Default, flat in Retro. */
val appStrokeCap: StrokeCap
    get() = if (ThemeStyleState.isRetro) StrokeCap.Butt else StrokeCap.Round
