package com.containerdashboard.ui.navigation

import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.article
import com.dockerdashboard.composeapp.generated.resources.article_filled
import com.dockerdashboard.composeapp.generated.resources.dashboard
import com.dockerdashboard.composeapp.generated.resources.dashboard_filled
import com.dockerdashboard.composeapp.generated.resources.hub
import com.dockerdashboard.composeapp.generated.resources.hub_filled
import com.dockerdashboard.composeapp.generated.resources.layers
import com.dockerdashboard.composeapp.generated.resources.layers_filled
import com.dockerdashboard.composeapp.generated.resources.monitor_heart
import com.dockerdashboard.composeapp.generated.resources.monitor_heart_filled
import com.dockerdashboard.composeapp.generated.resources.settings
import com.dockerdashboard.composeapp.generated.resources.settings_filled
import com.dockerdashboard.composeapp.generated.resources.storage
import com.dockerdashboard.composeapp.generated.resources.storage_filled
import com.dockerdashboard.composeapp.generated.resources.view_in_ar
import com.dockerdashboard.composeapp.generated.resources.view_in_ar_filled
import org.jetbrains.compose.resources.DrawableResource

enum class Screen(
    val route: String,
    val title: String,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
) {
    Dashboard(
        route = "dashboard",
        title = "Dashboard",
        icon = Res.drawable.dashboard,
        selectedIcon = Res.drawable.dashboard_filled,
    ),
    Containers(
        route = "containers",
        title = "Containers",
        icon = Res.drawable.view_in_ar,
        selectedIcon = Res.drawable.view_in_ar_filled,
    ),
    Images(
        route = "images",
        title = "Images",
        icon = Res.drawable.layers,
        selectedIcon = Res.drawable.layers_filled,
    ),
    Volumes(
        route = "volumes",
        title = "Volumes",
        icon = Res.drawable.storage,
        selectedIcon = Res.drawable.storage_filled,
    ),
    Networks(
        route = "networks",
        title = "Networks",
        icon = Res.drawable.hub,
        selectedIcon = Res.drawable.hub_filled,
    ),
    Monitoring(
        route = "monitoring",
        title = "Monitoring",
        icon = Res.drawable.monitor_heart,
        selectedIcon = Res.drawable.monitor_heart_filled,
    ),
    AppLogs(
        route = "app_logs",
        title = "App Logs",
        icon = Res.drawable.article,
        selectedIcon = Res.drawable.article_filled,
    ),
    Settings(
        route = "settings",
        title = "Settings",
        icon = Res.drawable.settings,
        selectedIcon = Res.drawable.settings_filled,
    ),
    ;

    companion object {
        val mainScreens = listOf(Dashboard, Containers, Images, Volumes, Networks, Monitoring, AppLogs)
        val allScreens = entries.toList()
    }
}
