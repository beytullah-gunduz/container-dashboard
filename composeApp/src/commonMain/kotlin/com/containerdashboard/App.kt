package com.containerdashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.containerdashboard.data.models.Container
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.components.LogsPane
import com.containerdashboard.ui.components.Sidebar
import com.containerdashboard.ui.components.ThreePaneScaffold
import com.containerdashboard.ui.components.rememberThreePaneScaffoldNavigator
import com.containerdashboard.ui.navigation.Screen
import com.containerdashboard.ui.screens.*
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.theme.ContainerDashboardTheme
import kotlinx.coroutines.launch

@Composable
fun App() {
    var currentRoute by remember { mutableStateOf(Screen.Dashboard.route) }
    var isConnected by remember { mutableStateOf(false) }
    var logsPaneState by remember { mutableStateOf(LogsPaneState()) }
    val scope = rememberCoroutineScope()
    
    // Create the navigator for the three-pane scaffold
    val navigator = rememberThreePaneScaffoldNavigator()
    
    // Check container engine connection status
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                if (AppModule.isInitialized) {
                    val result = AppModule.dockerRepository.getSystemInfo()
                    isConnected = result.isSuccess
                }
            } catch (e: Exception) {
                isConnected = false
            }
        }
    }
    
    // Function to show logs for a container
    fun showContainerLogs(container: Container) {
        scope.launch {
            logsPaneState = LogsPaneState(
                container = container,
                isLoading = true
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
                    }
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
                    }
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
            color = MaterialTheme.colorScheme.background
        ) {
            ThreePaneScaffold(
                navigator = navigator,
                listPaneWidth = 220.dp,
                initialExtraPaneWidth = 500.dp,
                listPane = {
                    Sidebar(
                        currentRoute = currentRoute,
                        onNavigate = { screen -> currentRoute = screen.route },
                        isConnected = isConnected
                    )
                },
                detailPane = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (currentRoute) {
                            Screen.Dashboard.route -> DashboardScreen()
                            Screen.Containers.route -> ContainersScreen(
                                onShowLogs = { container -> showContainerLogs(container) },
                                currentLogsContainerId = logsPaneState.container?.id
                            )
                            Screen.Images.route -> ImagesScreen()
                            Screen.Volumes.route -> VolumesScreen()
                            Screen.Networks.route -> NetworksScreen()
                            Screen.Settings.route -> SettingsScreen()
                        }
                    }
                },
                extraPane = {
                    LogsPane(
                        state = logsPaneState,
                        onRefresh = { refreshLogs() },
                        onClose = { closeLogs() }
                    )
                }
            )
        }
    }
}
