package com.containerdashboard.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.containerdashboard.ui.icons.automirrored.filled.Article
import com.containerdashboard.ui.icons.automirrored.outlined.Article
import com.containerdashboard.ui.icons.filled.Dashboard
import com.containerdashboard.ui.icons.filled.Hub
import com.containerdashboard.ui.icons.filled.Layers
import com.containerdashboard.ui.icons.filled.MonitorHeart
import com.containerdashboard.ui.icons.filled.Storage
import com.containerdashboard.ui.icons.filled.ViewInAr
import com.containerdashboard.ui.icons.outlined.Dashboard
import com.containerdashboard.ui.icons.outlined.Hub
import com.containerdashboard.ui.icons.outlined.Layers
import com.containerdashboard.ui.icons.outlined.MonitorHeart
import com.containerdashboard.ui.icons.outlined.Storage
import com.containerdashboard.ui.icons.outlined.ViewInAr

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
    AppLogs(
        route = "app_logs",
        title = "App Logs",
        icon = Icons.AutoMirrored.Outlined.Article,
        selectedIcon = Icons.AutoMirrored.Filled.Article,
    ),
    Settings(
        route = "settings",
        title = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    ),
    ;

    companion object {
        val mainScreens = listOf(Dashboard, Containers, Images, Volumes, Networks, Monitoring, AppLogs)
        val allScreens = entries.toList()
    }
}
