package com.containerdashboard.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.close_filled
import com.dockerdashboard.composeapp.generated.resources.minimize_filled
import org.jetbrains.compose.resources.painterResource
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private const val DOUBLE_CLICK_MS = 350L

// Room the title bar leaves for macOS's own close, minimize and zoom buttons.
private val MAC_TRAFFIC_LIGHTS_WIDTH = 78.dp

enum class DesktopOs { MAC, WINDOWS, LINUX }

val currentDesktopOs: DesktopOs by lazy {
    val name = System.getProperty("os.name")?.lowercase().orEmpty()
    when {
        name.contains("mac") || name.contains("darwin") -> DesktopOs.MAC
        name.contains("win") -> DesktopOs.WINDOWS
        else -> DesktopOs.LINUX
    }
}

class DesktopWindowChrome(
    val windowState: WindowState,
    val onMinimize: () -> Unit,
    val onToggleMaximize: () -> Unit,
    val onClose: () -> Unit,
    val dragArea: @Composable (content: @Composable () -> Unit) -> Unit,
)

val LocalDesktopWindowChrome = compositionLocalOf<DesktopWindowChrome?> { null }

@Composable
fun FrameWindowScope.rememberDesktopWindowChrome(
    windowState: WindowState,
    onClose: () -> Unit,
): DesktopWindowChrome {
    val onMinimize = { windowState.isMinimized = true }
    val onToggleMaximize = {
        windowState.placement =
            if (windowState.placement == WindowPlacement.Maximized) {
                WindowPlacement.Floating
            } else {
                WindowPlacement.Maximized
            }
    }
    // On macOS route drags through AppKit's `performWindowDragWithEvent:`
    // so the window follows the cursor across displays on multi-monitor
    // setups — Compose Desktop's `WindowDraggableArea` clamps at the primary
    // display's edge because it drives drags via AWT's `setLocation`, whose
    // screen-coordinate math doesn't agree with the macOS window server.
    // On Windows / Linux `WindowDraggableArea` works fine.
    val dragArea: @Composable (@Composable () -> Unit) -> Unit =
        if (currentDesktopOs == DesktopOs.MAC) {
            { content ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                var lastPressUptime = 0L
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        if (event.type == PointerEventType.Press &&
                                            event.changes.none { it.isConsumed }
                                        ) {
                                            val now = event.changes.first().uptimeMillis
                                            val isDoubleClick = now - lastPressUptime in 1..DOUBLE_CLICK_MS
                                            if (isDoubleClick) {
                                                onToggleMaximize()
                                                event.changes.forEach { it.consume() }
                                                lastPressUptime = 0L
                                            } else {
                                                lastPressUptime = now
                                                if (NativeWindowDrag.startDrag()) {
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                ) {
                    content()
                }
            }
        } else {
            { content ->
                WindowDraggableArea(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    return remember(windowState) {
        DesktopWindowChrome(
            windowState = windowState,
            onMinimize = onMinimize,
            onToggleMaximize = onToggleMaximize,
            onClose = onClose,
            dragArea = dragArea,
        )
    }
}

@Composable
actual fun WindowChromeLeading(modifier: Modifier) {
    LocalDesktopWindowChrome.current ?: return
    // On macOS the window keeps its own traffic lights (see TransparentTitleBarOnMac);
    // the bar only leaves room for them.
    Spacer(modifier.width(if (currentDesktopOs == DesktopOs.MAC) MAC_TRAFFIC_LIGHTS_WIDTH else 8.dp))
}

@Composable
actual fun WindowChromeTrailing(modifier: Modifier) {
    val chrome = LocalDesktopWindowChrome.current ?: return
    if (currentDesktopOs != DesktopOs.MAC) {
        WindowControls(
            modifier = modifier,
            onMinimize = chrome.onMinimize,
            onClose = chrome.onClose,
        )
    }
}

@Composable
actual fun TopBarDragArea(content: @Composable () -> Unit) {
    val chrome = LocalDesktopWindowChrome.current
    if (chrome == null) {
        content()
    } else {
        chrome.dragArea(content)
    }
}

/**
 * Gives a macOS [window] a transparent title bar with the app's content drawn under it, so the
 * window keeps the system's own traffic lights. Only those offer the ⌥-hover tiling menu (Move &
 * Resize, Fill & Arrange) and native full screen: macOS never attaches them to buttons the app
 * draws itself. A decorated window also zooms natively: for an undecorated one, AWT imitates
 * maximize and restores the bounds it saved, on the display the window was maximized on, even
 * after the user moved it to another one.
 *
 * Once the window is shown it also gets an empty toolbar ([MacTitleBarToolbar]), which makes the
 * system title bar as tall as the app's, so the traffic lights sit in its middle.
 *
 * The window must be created with `undecorated = false` on macOS. No-op elsewhere.
 */
@Composable
fun TransparentTitleBarOnMac(window: ComposeWindow) {
    if (currentDesktopOs != DesktopOs.MAC) return
    DisposableEffect(window) {
        // CPlatformWindow client properties; applied when set after the window exists too.
        window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
        val listener =
            object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent?) = MacTitleBarToolbar.refresh()
            }
        window.addWindowListener(listener)
        // Already on screen when this effect runs: windowOpened has fired.
        if (window.isShowing) MacTitleBarToolbar.refresh()
        onDispose { window.removeWindowListener(listener) }
    }
}

/**
 * Height of the app's title bar. On macOS it is twice the traffic lights' measured center, so the
 * bar's content lines up with them; 38 until the measurement lands (the value on macOS 26), so the
 * bar does not jump at launch.
 */
@Composable
actual fun windowChromeBarHeight(): Dp =
    when {
        LocalDesktopWindowChrome.current == null -> 32.dp
        currentDesktopOs != DesktopOs.MAC -> 32.dp
        else -> MacTrafficLights.centerFromTopPt?.let { (it * 2).dp } ?: 38.dp
    }

@Composable
private fun WindowControls(
    modifier: Modifier = Modifier,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        TitleBarButton(onClick = onMinimize) {
            Icon(painterResource(Res.drawable.minimize_filled), contentDescription = "Minimize", modifier = Modifier.size(16.dp))
        }
        TitleBarButton(onClick = onClose, isClose = true) {
            Icon(painterResource(Res.drawable.close_filled), contentDescription = "Close", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TitleBarButton(
    onClick: () -> Unit,
    isClose: Boolean = false,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(width = 40.dp, height = 32.dp),
        colors =
            if (isClose) {
                IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            } else {
                IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            },
    ) {
        content()
    }
}
