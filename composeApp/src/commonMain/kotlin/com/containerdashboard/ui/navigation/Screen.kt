package com.containerdashboard.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Dashboard(
        route = "dashboard",
        title = "Dashboard",
        icon = Icons.Outlined.Dashboard,
        selectedIcon = Icons.Filled.Dashboard,
    ),
    Containers(
        route = "containers",
        title = "Containers",
        icon = Icons.Outlined.ViewInAr,
        selectedIcon = Icons.Filled.ViewInAr,
    ),
    Images(
        route = "images",
        title = "Images",
        icon = Icons.Outlined.Layers,
        selectedIcon = Icons.Filled.Layers,
    ),
    Volumes(
        route = "volumes",
        title = "Volumes",
        icon = Icons.Outlined.Storage,
        selectedIcon = Icons.Filled.Storage,
    ),
    Networks(
        route = "networks",
        title = "Networks",
        icon = Icons.Outlined.Hub,
        selectedIcon = Icons.Filled.Hub,
    ),
    Monitoring(
        route = "monitoring",
        title = "Monitoring",
        icon = Icons.Outlined.MonitorHeart,
        selectedIcon = Icons.Filled.MonitorHeart,
    ),
    Settings(
        route = "settings",
        title = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    ),
    ;

    companion object {
        val mainScreens = listOf(Dashboard, Containers, Images, Volumes, Networks, Monitoring)
        val allScreens = entries.toList()
    }
}
