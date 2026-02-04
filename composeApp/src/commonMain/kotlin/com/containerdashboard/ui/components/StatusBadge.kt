package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.containerdashboard.ui.theme.DockerColors

enum class ContainerStatus {
    RUNNING, STOPPED, PAUSED, CREATED, RESTARTING, REMOVING, DEAD, EXITED
}

fun String.toContainerStatus(): ContainerStatus {
    return when (this.lowercase()) {
        "running" -> ContainerStatus.RUNNING
        "paused" -> ContainerStatus.PAUSED
        "created" -> ContainerStatus.CREATED
        "restarting" -> ContainerStatus.RESTARTING
        "removing" -> ContainerStatus.REMOVING
        "dead" -> ContainerStatus.DEAD
        "exited", "stopped" -> ContainerStatus.STOPPED
        else -> ContainerStatus.STOPPED
    }
}

@Composable
fun StatusBadge(
    status: ContainerStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (status) {
        ContainerStatus.RUNNING -> DockerColors.Running.copy(alpha = 0.15f) to DockerColors.Running
        ContainerStatus.PAUSED -> DockerColors.Paused.copy(alpha = 0.15f) to DockerColors.Paused
        ContainerStatus.STOPPED, ContainerStatus.EXITED -> DockerColors.Stopped.copy(alpha = 0.15f) to DockerColors.Stopped
        ContainerStatus.CREATED -> DockerColors.DockerBlue.copy(alpha = 0.15f) to DockerColors.DockerBlue
        ContainerStatus.RESTARTING -> DockerColors.Warning.copy(alpha = 0.15f) to DockerColors.Warning
        ContainerStatus.REMOVING, ContainerStatus.DEAD -> DockerColors.Stopped.copy(alpha = 0.15f) to DockerColors.Stopped
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.size(6.dp),
            shape = RoundedCornerShape(3.dp),
            color = textColor
        ) {}
        
        Text(
            text = status.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatusDot(
    isActive: Boolean,
    activeColor: Color = DockerColors.Running,
    inactiveColor: Color = DockerColors.Stopped,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(8.dp),
        shape = RoundedCornerShape(4.dp),
        color = if (isActive) activeColor else inactiveColor
    ) {}
}
