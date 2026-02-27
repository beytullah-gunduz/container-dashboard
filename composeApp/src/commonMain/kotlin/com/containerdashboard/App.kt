package com.containerdashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.ui.components.LogsPane
import com.containerdashboard.ui.components.Sidebar
import com.containerdashboard.ui.components.ThreePaneScaffold
import com.containerdashboard.ui.components.rememberThreePaneScaffoldNavigator
import com.containerdashboard.ui.navigation.Screen
import com.containerdashboard.ui.screens.AppLogsScreen
import com.containerdashboard.ui.screens.ContainersScreen
import com.containerdashboard.ui.screens.DashboardScreen
import com.containerdashboard.ui.screens.ImagesScreen
import com.containerdashboard.ui.screens.MonitoringScreen
import com.containerdashboard.ui.screens.NetworksScreen
import com.containerdashboard.ui.screens.SettingsScreen
import com.containerdashboard.ui.screens.VolumesScreen
import com.containerdashboard.ui.screens.viewmodel.AppViewModel
import com.containerdashboard.ui.theme.ContainerDashboardTheme

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun App(
    viewModel: AppViewModel = viewModel { AppViewModel() },
    navigateToRoute: String? = null,
    onNavigated: () -> Unit = {},
) {
    val currentRoute by viewModel.currentRoute.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val logsPaneState by viewModel.logsPaneState.collectAsState()
    val darkTheme by viewModel.darkTheme.collectAsState()

    LaunchedEffect(navigateToRoute) {
        if (navigateToRoute != null) {
            viewModel.navigate(navigateToRoute)
            onNavigated()
        }
    }

    val navigator = rememberThreePaneScaffoldNavigator()

    ContainerDashboardTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnimatedContent(
                targetState = isConnected,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.96f))
                        .togetherWith(fadeOut())
                },
                label = "DockerConnectionTransition",
            ) { connected ->
                if (!connected) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Docker is not available",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Please start Docker Desktop and the dashboard will connect automatically.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                } else {
                    ThreePaneScaffold(
                        navigator = navigator,
                        listPaneWidth = 220.dp,
                        extraPaneWidthFraction = 0.7f,
                        listPane = {
                            Sidebar(
                                currentRoute = currentRoute,
                                onNavigate = { screen -> viewModel.navigate(screen.route) },
                                isConnected = isConnected,
                            )
                        },
                        detailPane = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                            ) {
                                when (currentRoute) {
                                    Screen.Dashboard.route -> DashboardScreen()
                                    Screen.Containers.route ->
                                        ContainersScreen(
                                            onShowLogs = { container ->
                                                viewModel.showContainerLogs(container)
                                                navigator.showExtraPane()
                                            },
                                            currentLogsContainerId = logsPaneState.container?.id,
                                        )
                                    Screen.Images.route -> ImagesScreen()
                                    Screen.Volumes.route -> VolumesScreen()
                                    Screen.Networks.route -> NetworksScreen()
                                    Screen.Monitoring.route -> MonitoringScreen()
                                    Screen.AppLogs.route -> AppLogsScreen()
                                    Screen.Settings.route -> SettingsScreen()
                                }
                            }
                        },
                        extraPane = {
                            LogsPane(
                                state = logsPaneState,
                                onRefresh = { viewModel.refreshLogs() },
                                onClose = {
                                    navigator.hideExtraPane()
                                    viewModel.clearLogs()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
