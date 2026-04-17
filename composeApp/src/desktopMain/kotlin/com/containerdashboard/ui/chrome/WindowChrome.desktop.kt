package com.containerdashboard.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

private const val DOUBLE_CLICK_MS = 350L

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
                    modifier = Modifier
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
    val chrome = LocalDesktopWindowChrome.current ?: return
    if (currentDesktopOs == DesktopOs.MAC) {
        TrafficLights(
            modifier = modifier,
            onClose = chrome.onClose,
            onMinimize = chrome.onMinimize,
            onZoom = chrome.onToggleMaximize,
        )
    } else {
        Spacer(modifier.width(8.dp))
    }
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

@Composable
private fun TrafficLights(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onZoom: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .padding(start = 12.dp, end = 8.dp)
            .hoverable(interaction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrafficLight(color = Color(0xFFFF5F57), showGlyph = hovered, enabled = true, onClick = onClose)
        TrafficLight(color = Color(0xFFFEBC2E), showGlyph = hovered, enabled = true, onClick = onMinimize)
        TrafficLight(color = Color(0xFF28C840), showGlyph = hovered, enabled = true, onClick = onZoom)
    }
}

@Composable
private fun TrafficLight(
    color: Color,
    showGlyph: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val displayColor = if (enabled) color else color.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(displayColor)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (showGlyph && enabled) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            )
        }
    }
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
            Icon(Icons.Filled.Minimize, contentDescription = "Minimize", modifier = Modifier.size(16.dp))
        }
        TitleBarButton(onClick = onClose, isClose = true) {
            Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
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
        colors = if (isClose) {
            IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        } else {
            IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        content()
    }
}
