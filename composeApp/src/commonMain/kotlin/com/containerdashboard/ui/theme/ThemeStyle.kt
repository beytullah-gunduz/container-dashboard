package com.containerdashboard.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Orthogonal to [ThemeMode]: the mode picks light or dark, the style picks the look. */
enum class ThemeStyle { DEFAULT, RETRO }

/**
 * The active style, global so non-composable token helpers ([appCorner], [appRoundShape]) can read
 * it. Snapshot state: reads during composition or drawing update when it changes.
 */
object ThemeStyleState {
    var style: ThemeStyle by mutableStateOf(ThemeStyle.DEFAULT)

    val isRetro: Boolean get() = style == ThemeStyle.RETRO
}
