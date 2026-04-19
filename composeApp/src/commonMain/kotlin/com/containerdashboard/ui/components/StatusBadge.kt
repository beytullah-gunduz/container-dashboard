package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing

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
            ContainerStatus.RUNNING -> AppColors.Running.copy(alpha = 0.15f) to AppColors.Running
            ContainerStatus.PAUSED -> AppColors.Paused.copy(alpha = 0.15f) to AppColors.Paused
            ContainerStatus.STOPPED, ContainerStatus.EXITED -> AppColors.Stopped.copy(alpha = 0.15f) to AppColors.Stopped
            ContainerStatus.CREATED -> AppColors.AccentBlue.copy(alpha = 0.15f) to AppColors.AccentBlue
            ContainerStatus.RESTARTING -> AppColors.Warning.copy(alpha = 0.15f) to AppColors.Warning
            ContainerStatus.REMOVING, ContainerStatus.DEAD -> AppColors.Stopped.copy(alpha = 0.15f) to AppColors.Stopped
        }

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.sm))
                .background(backgroundColor)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.size(6.dp),
            shape = RoundedCornerShape(Radius.xs),
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
    activeColor: Color = AppColors.Running,
    inactiveColor: Color = AppColors.Stopped,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(8.dp),
        shape = RoundedCornerShape(Radius.sm),
        color = if (isActive) activeColor else inactiveColor,
    ) {}
}
