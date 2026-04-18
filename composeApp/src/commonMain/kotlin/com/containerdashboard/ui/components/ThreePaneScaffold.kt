package com.containerdashboard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.AppTheme
import java.awt.Cursor

enum class LogsPaneLayout {
    RIGHT,
    BOTTOM,
    AUTO,
}

enum class ThreePaneNavigationState {
    ListDetail,
    ListDetailExtra,
}

class ThreePaneScaffoldNavigator {
    var currentState by mutableStateOf(ThreePaneNavigationState.ListDetail)
        private set

    fun showExtraPane() {
        currentState = ThreePaneNavigationState.ListDetailExtra
    }

    fun hideExtraPane() {
        currentState = ThreePaneNavigationState.ListDetail
    }

    val isExtraPaneVisible: Boolean
        get() = currentState == ThreePaneNavigationState.ListDetailExtra
}

@Composable
fun rememberThreePaneScaffoldNavigator(): ThreePaneScaffoldNavigator = remember { ThreePaneScaffoldNavigator() }

private val AUTO_BREAKPOINT = 1000.dp

@Composable
fun ThreePaneScaffold(
    navigator: ThreePaneScaffoldNavigator,
    paneLayout: LogsPaneLayout = LogsPaneLayout.AUTO,
    listPaneWidth: Dp = 220.dp,
    minExtraPaneSize: Dp = 250.dp,
    listPaneHeader: @Composable () -> Unit = {},
    detailPaneTopOverlay: @Composable () -> Unit = {},
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    extraPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableContentWidth = maxWidth - listPaneWidth
        val totalHeight = maxHeight
        val effectiveLayout =
            when (paneLayout) {
                LogsPaneLayout.RIGHT -> LogsPaneLayout.RIGHT
                LogsPaneLayout.BOTTOM -> LogsPaneLayout.BOTTOM
                LogsPaneLayout.AUTO ->
                    if (availableContentWidth < AUTO_BREAKPOINT) LogsPaneLayout.BOTTOM else LogsPaneLayout.RIGHT
            }

        Row(modifier = Modifier.fillMaxSize()) {
            // List Pane (Sidebar) with its own header (e.g. window chrome)
            // stacked on top of it, so the detail/extra panes can claim the
            // full window height and the title bar occupies otherwise-unused
            // space above the sidebar.
            Column(modifier = Modifier.width(listPaneWidth).fillMaxHeight()) {
                listPaneHeader()
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    listPane()
                }
            }

            // Detail + Extra Pane area
            if (effectiveLayout == LogsPaneLayout.RIGHT) {
                RightLayout(
                    navigator = navigator,
                    availableWidth = availableContentWidth,
                    minExtraPaneSize = minExtraPaneSize,
                    detailPane = detailPane,
                    detailPaneTopOverlay = detailPaneTopOverlay,
                    extraPane = extraPane,
                )
            } else {
                BottomLayout(
                    navigator = navigator,
                    availableHeight = totalHeight,
                    minExtraPaneSize = minExtraPaneSize,
                    detailPane = detailPane,
                    detailPaneTopOverlay = detailPaneTopOverlay,
                    extraPane = extraPane,
                )
            }
        }
    }
}

@Composable
private fun RowScope.RightLayout(
    navigator: ThreePaneScaffoldNavigator,
    availableWidth: Dp,
    minExtraPaneSize: Dp,
    detailPane: @Composable () -> Unit,
    detailPaneTopOverlay: @Composable () -> Unit,
    extraPane: @Composable () -> Unit,
) {
    val minDetailWidth = 450.dp
    val maxExtraWidth = (availableWidth - minDetailWidth).coerceAtLeast(minExtraPaneSize)
    val persistedRight by PreferenceRepository.logsPaneRightWidth().collectAsState(initial = null)
    var extraWidth by remember(maxExtraWidth, persistedRight) {
        mutableStateOf(
            persistedRight
                ?.dp
                ?.coerceIn(minExtraPaneSize, maxExtraWidth)
                ?: maxExtraWidth,
        )
    }
    if (extraWidth > maxExtraWidth) extraWidth = maxExtraWidth
    LaunchedEffect(extraWidth) {
        kotlinx.coroutines.delay(300)
        PreferenceRepository.setLogsPaneRightWidth(extraWidth.value.toInt())
    }

    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        detailPane()
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)) {
            detailPaneTopOverlay()
        }
    }

    AnimatedVisibility(
        visible = navigator.isExtraPaneVisible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)),
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            ResizableDivider(
                isVertical = true,
                onDrag = { delta ->
                    extraWidth = (extraWidth - delta).coerceIn(minExtraPaneSize, maxExtraWidth)
                },
            )
            Box(modifier = Modifier.width(extraWidth).fillMaxHeight()) {
                extraPane()
            }
        }
    }
}

@Composable
private fun RowScope.BottomLayout(
    navigator: ThreePaneScaffoldNavigator,
    availableHeight: Dp,
    minExtraPaneSize: Dp,
    detailPane: @Composable () -> Unit,
    detailPaneTopOverlay: @Composable () -> Unit,
    extraPane: @Composable () -> Unit,
) {
    val minDetailHeight = 300.dp
    val maxExtraHeight = (availableHeight - minDetailHeight).coerceAtLeast(minExtraPaneSize)
    val initialHeight = (availableHeight * 0.4f).coerceIn(minExtraPaneSize, maxExtraHeight)
    val persistedBottom by PreferenceRepository.logsPaneBottomHeight().collectAsState(initial = null)
    var extraHeight by remember(maxExtraHeight, persistedBottom) {
        mutableStateOf(
            persistedBottom
                ?.dp
                ?.coerceIn(minExtraPaneSize, maxExtraHeight)
                ?: initialHeight,
        )
    }
    if (extraHeight > maxExtraHeight) extraHeight = maxExtraHeight
    LaunchedEffect(extraHeight) {
        kotlinx.coroutines.delay(300)
        PreferenceRepository.setLogsPaneBottomHeight(extraHeight.value.toInt())
    }

    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            detailPane()
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)) {
                detailPaneTopOverlay()
            }
        }

        AnimatedVisibility(
            visible = navigator.isExtraPaneVisible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ResizableDivider(
                    isVertical = false,
                    onDrag = { delta ->
                        extraHeight = (extraHeight - delta).coerceIn(minExtraPaneSize, maxExtraHeight)
                    },
                )
                Box(modifier = Modifier.height(extraHeight).fillMaxWidth()) {
                    extraPane()
                }
            }
        }
    }
}

@Composable
private fun ResizableDivider(
    isVertical: Boolean,
    onDrag: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val dividerColor =
        when {
            isDragging -> AppColors.AccentBlue
            isHovered -> AppColors.AccentBlue.copy(alpha = 0.7f)
            else -> AppTheme.extended.dividerStrong
        }

    val cursor =
        if (isVertical) Cursor(Cursor.W_RESIZE_CURSOR) else Cursor(Cursor.N_RESIZE_CURSOR)

    Box(
        modifier =
            modifier
                .then(
                    if (isVertical) {
                        Modifier.width(6.dp).fillMaxHeight()
                    } else {
                        Modifier.height(6.dp).fillMaxWidth()
                    },
                ).hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon(cursor))
                .pointerInput(isVertical) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            with(density) {
                                val delta =
                                    if (isVertical) dragAmount.x.toDp() else dragAmount.y.toDp()
                                onDrag(delta)
                            }
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        // Divider line
        Box(
            modifier =
                if (isVertical) {
                    Modifier.width(2.dp).fillMaxHeight()
                } else {
                    Modifier.height(2.dp).fillMaxWidth()
                }.background(dividerColor),
        )

        // Drag handle dots
        if (isHovered || isDragging) {
            if (isVertical) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    repeat(3) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(vertical = 2.dp)
                                    .size(4.dp)
                                    .background(
                                        dividerColor,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                    ),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(3) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(4.dp)
                                    .background(
                                        dividerColor,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                    ),
                        )
                    }
                }
            }
        }
    }
}
