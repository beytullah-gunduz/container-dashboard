package com.containerdashboard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/**
 * Navigation state for the three-pane scaffold.
 */
enum class ThreePaneNavigationState {
    /** Only list and detail panes are visible */
    ListDetail,
    /** List, detail, and extra panes are all visible */
    ListDetailExtra
}

/**
 * Navigator for the ThreePaneScaffold.
 */
class ThreePaneScaffoldNavigator {
    var currentState by mutableStateOf(ThreePaneNavigationState.ListDetail)
        private set
    
    /**
     * Shows the extra pane (navigates to ListDetailExtra state).
     */
    fun showExtraPane() {
        currentState = ThreePaneNavigationState.ListDetailExtra
    }
    
    /**
     * Hides the extra pane (navigates back to ListDetail state).
     */
    fun hideExtraPane() {
        currentState = ThreePaneNavigationState.ListDetail
    }
    
    /**
     * Checks if the extra pane is currently visible.
     */
    val isExtraPaneVisible: Boolean
        get() = currentState == ThreePaneNavigationState.ListDetailExtra
}

/**
 * Creates and remembers a ThreePaneScaffoldNavigator.
 */
@Composable
fun rememberThreePaneScaffoldNavigator(): ThreePaneScaffoldNavigator {
    return remember { ThreePaneScaffoldNavigator() }
}

/**
 * A custom three-pane scaffold for desktop applications with resizable extra pane.
 * 
 * @param navigator The navigator controlling pane visibility.
 * @param listPaneWidth The width of the list (sidebar) pane.
 * @param extraPaneWidthFraction The fraction of available width for the extra pane (0.0 to 1.0).
 * @param minExtraPaneWidth The minimum width the extra pane can be resized to.
 * @param listPane The content for the list pane (typically a sidebar).
 * @param detailPane The content for the detail pane (main content area).
 * @param extraPane The content for the extra pane (e.g., logs panel).
 * @param modifier The modifier for the scaffold.
 */
@Composable
fun ThreePaneScaffold(
    navigator: ThreePaneScaffoldNavigator,
    listPaneWidth: Dp = 220.dp,
    extraPaneWidthFraction: Float = 0.5f,
    minExtraPaneWidth: Dp = 300.dp,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    extraPane: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Calculate initial extra pane width as a fraction of the available content area
        val availableContentWidth = maxWidth - listPaneWidth
        val calculatedExtraPaneWidth = (availableContentWidth * extraPaneWidthFraction).coerceAtLeast(minExtraPaneWidth)
        
        var extraPaneWidth by remember(calculatedExtraPaneWidth) { mutableStateOf(calculatedExtraPaneWidth) }
        
        Row(modifier = Modifier.fillMaxSize()) {
            // List Pane (Sidebar) - Fixed width
            Box(modifier = Modifier.width(listPaneWidth).fillMaxHeight()) {
                listPane()
            }
            
            // Detail Pane (Main Content) - Takes remaining space
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                detailPane()
            }
            
            // Extra Pane (Logs) with resizable divider - Animated sliding in/out
            AnimatedVisibility(
                visible = navigator.isExtraPaneVisible,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 300)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 300)
                )
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    // Resizable Divider
                    ResizableDivider(
                        onDrag = { delta ->
                            val newWidth = extraPaneWidth - delta
                            extraPaneWidth = newWidth.coerceAtLeast(minExtraPaneWidth)
                        }
                    )
                    
                    // Extra Pane Content
                    Box(modifier = Modifier.width(extraPaneWidth).fillMaxHeight()) {
                        extraPane()
                    }
                }
            }
        }
    }
}

/**
 * A vertical divider that can be dragged to resize adjacent panes.
 */
@Composable
private fun ResizableDivider(
    onDrag: (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    
    val dividerColor = when {
        isDragging -> Color(0xFF0DB7ED) // Docker blue when dragging
        isHovered -> Color(0xFF0DB7ED).copy(alpha = 0.7f) // Lighter blue on hover
        else -> Color(0xFF404040) // Dark gray normally
    }
    
    Box(
        modifier = modifier
            .width(6.dp)
            .fillMaxHeight()
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        with(density) {
                            onDrag(dragAmount.x.toDp())
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Visual divider line
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(dividerColor)
        )
        
        // Drag handle indicator (visible on hover/drag)
        if (isHovered || isDragging) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Three dots as resize indicator
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .size(4.dp)
                            .background(dividerColor, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }
    }
}
