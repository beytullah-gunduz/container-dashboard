package com.containerdashboard.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
expect fun WindowChromeLeading(modifier: Modifier = Modifier)

@Composable
expect fun WindowChromeTrailing(modifier: Modifier = Modifier)

@Composable
expect fun TopBarDragArea(content: @Composable () -> Unit)

/** Height of the app's own title bar, which on macOS lines up with the system's traffic lights. */
@Composable
expect fun windowChromeBarHeight(): Dp
