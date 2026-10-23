package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.AppTheme
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.theme.appCorner

enum class ContainerStatus {
    RUNNING,
    STOPPED,
    PAUSED,
    CREATED,
    RESTARTING,
    REMOVING,
    DEAD,
    EXITED,
}

fun String.toContainerStatus(): ContainerStatus =
    when (this.lowercase()) {
        "running" -> ContainerStatus.RUNNING
        "paused" -> ContainerStatus.PAUSED
        "created" -> ContainerStatus.CREATED
        "restarting" -> ContainerStatus.RESTARTING
        "removing" -> ContainerStatus.REMOVING
        "dead" -> ContainerStatus.DEAD
        "exited", "stopped" -> ContainerStatus.STOPPED
        else -> ContainerStatus.STOPPED
    }

@Composable
fun StatusBadge(
    status: ContainerStatus,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor) =
        when (status) {
            ContainerStatus.RUNNING -> AppTheme.extended.running.copy(alpha = 0.15f) to AppTheme.extended.running
            ContainerStatus.PAUSED -> AppTheme.extended.paused.copy(alpha = 0.15f) to AppTheme.extended.paused
            ContainerStatus.STOPPED, ContainerStatus.EXITED -> AppTheme.extended.stopped.copy(alpha = 0.15f) to AppTheme.extended.stopped
            ContainerStatus.CREATED -> AppTheme.extended.accent.copy(alpha = 0.15f) to AppTheme.extended.accent
            ContainerStatus.RESTARTING -> AppTheme.extended.warning.copy(alpha = 0.15f) to AppTheme.extended.warning
            ContainerStatus.REMOVING, ContainerStatus.DEAD -> AppTheme.extended.stopped.copy(alpha = 0.15f) to AppTheme.extended.stopped
        }

    Row(
        modifier =
            modifier
                .clip(Radius.sm.appCorner)
                .background(backgroundColor)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.size(6.dp),
            shape = Radius.xs.appCorner,
            color = textColor,
        ) {}

        Text(
            text = status.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun StatusDot(
    isActive: Boolean,
    activeColor: Color = AppTheme.extended.running,
    inactiveColor: Color = AppTheme.extended.stopped,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(8.dp),
        shape = Radius.sm.appCorner,
        color = if (isActive) activeColor else inactiveColor,
    ) {}
}
