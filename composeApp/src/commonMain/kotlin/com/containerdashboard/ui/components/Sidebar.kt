package com.containerdashboard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.navigation.Screen
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.util.isMacHost
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.info
import com.dockerdashboard.composeapp.generated.resources.search
import org.jetbrains.compose.resources.painterResource

@Composable
fun Sidebar(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    isConnected: Boolean = false,
    engineName: String = "Container Engine",
    // Issue 8 fix: optional hooks so callers can wire the discoverable palette/shortcuts affordance
    onOpenPalette: (() -> Unit)? = null,
    onShowShortcuts: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(220.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = Spacing.md),
        ) {
            // Navigation Items
            Screen.mainScreens.forEach { screen ->
                SidebarItem(
                    screen = screen,
                    isSelected = currentRoute == screen.route,
                    onClick = { onNavigate(screen) },
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Settings at bottom
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.md),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))

            SidebarItem(
                screen = Screen.Settings,
                isSelected = currentRoute == Screen.Settings.route,
                onClick = { onNavigate(Screen.Settings) },
            )

            // Issue 8 fix: discoverable affordance for command palette and keyboard shortcuts
            if (onOpenPalette != null || onShowShortcuts != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.md),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onOpenPalette != null) {
                        val paletteHint = if (isMacHost) "⌘K" else "Ctrl K"
                        AppTooltip(label = "Command palette ($paletteHint)") {
                            IconButton(
                                onClick = onOpenPalette,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.search),
                                    contentDescription = "Open command palette ($paletteHint)",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = paletteHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    if (onOpenPalette != null && onShowShortcuts != null) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (onShowShortcuts != null) {
                        AppTooltip(label = "Keyboard shortcuts (?)") {
                            IconButton(
                                onClick = onShowShortcuts,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.info),
                                    contentDescription = "Show keyboard shortcuts",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Connection status
            ConnectionStatus(
                isConnected = isConnected,
                engineName = engineName,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}

@Composable
private fun SidebarItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                // Match the detail pane (App.kt detailPane Box) so the selection
                // visually continues into the content area across the sidebar seam.
                MaterialTheme.colorScheme.background
            } else {
                Color.Transparent
            },
    )

    val contentColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )

    val stripColor by animateColorAsState(
        targetValue = if (isSelected) AppColors.AccentBlue else Color.Transparent,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(stripColor),
        )
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(
                        RoundedCornerShape(
                            topStart = Radius.md,
                            bottomStart = Radius.md,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp,
                        ),
                    ).background(backgroundColor)
                    .clickable(onClick = onClick)
                    .padding(horizontal = Spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                painter = painterResource(if (isSelected) screen.selectedIcon else screen.icon),
                contentDescription = screen.title,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = screen.title,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ConnectionStatus(
    isConnected: Boolean,
    engineName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = RoundedCornerShape(Radius.sm),
            color = if (isConnected) AppColors.Running else AppColors.Stopped,
        ) {}

        Column {
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = engineName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
