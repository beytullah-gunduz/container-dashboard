package com.containerdashboard.ui.shortcuts

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.containerdashboard.ui.navigation.Screen

val LocalSearchFocusRequester = compositionLocalOf<FocusRequester?> { null }

private val isMac = System.getProperty("os.name", "").contains("mac", ignoreCase = true)

@Composable
fun AppShortcutScope(
    onNavigate: (Screen) -> Unit,
    onOpenPalette: () -> Unit,
    onCloseOverlays: () -> Unit,
    onFocusSearch: () -> Unit,
    onRefresh: () -> Unit,
    onShowCheatsheet: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { rootFocus.requestFocus() }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val mod = if (isMac) ev.isMetaPressed else ev.isCtrlPressed

                    when {
                        mod && ev.key == Key.K -> {
                            onOpenPalette()
                            true
                        }
                        mod && ev.key == Key.F -> {
                            onFocusSearch()
                            true
                        }
                        mod && ev.key == Key.Comma -> {
                            onNavigate(Screen.Settings)
                            true
                        }
                        mod && ev.key == Key.Slash -> {
                            onShowCheatsheet()
                            true
                        }
                        mod && ev.key == Key.One -> nav(onNavigate, 0)
                        mod && ev.key == Key.Two -> nav(onNavigate, 1)
                        mod && ev.key == Key.Three -> nav(onNavigate, 2)
                        mod && ev.key == Key.Four -> nav(onNavigate, 3)
                        mod && ev.key == Key.Five -> nav(onNavigate, 4)
                        mod && ev.key == Key.Six -> nav(onNavigate, 5)
                        mod && ev.key == Key.Seven -> nav(onNavigate, 6)
                        ev.key == Key.Escape -> {
                            onCloseOverlays()
                            true
                        }
                        ev.isShiftPressed && ev.key == Key.Slash -> {
                            onShowCheatsheet()
                            true
                        }
                        else -> false
                    }
                },
    ) { content() }
}

private fun nav(
    onNavigate: (Screen) -> Unit,
    index: Int,
): Boolean {
    val list = Screen.mainScreens
    if (index in list.indices) onNavigate(list[index])
    return true
}
