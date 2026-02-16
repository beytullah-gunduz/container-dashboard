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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.Container
import com.containerdashboard.di.AppModule
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
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.theme.ContainerDashboardTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun App(viewModel: AppViewModel = viewModel { AppViewModel() }) {
    var currentRoute by remember { mutableStateOf(Screen.Dashboard.route) }
    val isConnected by viewModel.isConnected.collectAsState()
    var logsPaneState by remember { mutableStateOf(LogsPaneState()) }
    val scope = rememberCoroutineScope()

    // Create the navigator for the three-pane scaffold
    val navigator = rememberThreePaneScaffoldNavigator()

    // Function to show logs for a container
    fun showContainerLogs(container: Container) {
        scope.launch {
            logsPaneState =
                LogsPaneState(
                    container = container,
                    isLoading = true,
                )
            // Navigate to show the extra pane
            navigator.showExtraPane()

            try {
                val result = AppModule.dockerRepository.getContainerLogs(container.id)
                result.fold(
                    onSuccess = { logs ->
                        logsPaneState = logsPaneState.copy(logs = logs, isLoading = false)
                    },
                    onFailure = { e ->
                        logsPaneState = logsPaneState.copy(error = e.message, isLoading = false)
                    },
                )
            } catch (e: Exception) {
                logsPaneState = logsPaneState.copy(error = e.message, isLoading = false)
            }
        }
    }

    // Function to refresh logs
    fun refreshLogs() {
        val container = logsPaneState.container ?: return
        scope.launch {
            logsPaneState = logsPaneState.copy(isLoading = true, error = null)
            try {
                val result = AppModule.dockerRepository.getContainerLogs(container.id)
                result.fold(
                    onSuccess = { logs ->
                        logsPaneState = logsPaneState.copy(logs = logs, isLoading = false)
                    },
                    onFailure = { e ->
                        logsPaneState = logsPaneState.copy(error = e.message, isLoading = false)
                    },
                )
            } catch (e: Exception) {
                logsPaneState = logsPaneState.copy(error = e.message, isLoading = false)
            }
        }
    }

    // Function to close logs pane
    fun closeLogs() {
        navigator.hideExtraPane()
        logsPaneState = LogsPaneState()
    }

    ContainerDashboardTheme(darkTheme = true) {
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
                                onNavigate = { screen -> currentRoute = screen.route },
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
                                            onShowLogs = { container -> showContainerLogs(container) },
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
                                onRefresh = { refreshLogs() },
                                onClose = { closeLogs() },
                            )
                        },
                    )
                }
            }
        }
    }
}
