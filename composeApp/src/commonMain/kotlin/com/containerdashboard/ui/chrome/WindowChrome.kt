package com.containerdashboard.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun WindowChromeLeading(modifier: Modifier = Modifier)

@Composable
expect fun WindowChromeTrailing(modifier: Modifier = Modifier)

@Composable
expect fun TopBarDragArea(content: @Composable () -> Unit)
