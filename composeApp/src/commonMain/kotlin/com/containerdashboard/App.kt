package com.containerdashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.chrome.TopBarDragArea
import com.containerdashboard.ui.chrome.WindowChromeLeading
import com.containerdashboard.ui.chrome.WindowChromeTrailing
import com.containerdashboard.ui.components.ConfirmActionDialog
import com.containerdashboard.ui.components.ContainerExtraPane
import com.containerdashboard.ui.components.FilesTabContent
import com.containerdashboard.ui.components.Sidebar
import com.containerdashboard.ui.components.ThreePaneScaffold
import com.containerdashboard.ui.components.rememberThreePaneScaffoldNavigator
import com.containerdashboard.ui.components.saveBytesToFile
import com.containerdashboard.ui.components.saveLogsToFile
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
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.EngineConnectionState
import com.containerdashboard.ui.shortcuts.AppShortcutScope
import com.containerdashboard.ui.shortcuts.CommandPalette
import com.containerdashboard.ui.shortcuts.KeyboardShortcutsOverlay
import com.containerdashboard.ui.shortcuts.LocalSearchFocusRequester
import com.containerdashboard.ui.shortcuts.PaletteAction
import com.containerdashboard.ui.theme.ContainerDashboardTheme
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.theme.ThemeMode
import com.containerdashboard.ui.util.isMacHost
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.arrow_back_filled
import com.dockerdashboard.composeapp.generated.resources.settings
import com.dockerdashboard.composeapp.generated.resources.warning
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun App(
    viewModel: AppViewModel = viewModel { AppViewModel() },
    navigateToRoute: String? = null,
    onNavigated: () -> Unit = {},
    consoleContent: @Composable (containerId: String, darkTheme: Boolean) -> Unit = { _, _ -> },
) {
    val currentRoute by viewModel.currentRoute.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val logsPaneState by viewModel.logsPaneState.collectAsState()
    val filesPaneState by viewModel.filesPaneState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme =
        when (themeMode) {
            ThemeMode.AUTO -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    val logsPaneLayout by viewModel.logsPaneLayout.collectAsState()
    val engineName by viewModel.engineName.collectAsState()

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
                modifier = Modifier.fillMaxSize(),
                targetState = connectionState,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.96f))
                        .togetherWith(fadeOut())
                },
                label = "DockerConnectionTransition",
            ) { state ->
                if (state != EngineConnectionState.CONNECTED) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WindowChromeBar()
                        if (currentRoute == Screen.Settings.route) {
                            Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                                IconButton(
                                    onClick = { viewModel.navigate(Screen.Dashboard.route) },
                                    modifier = Modifier.padding(Spacing.sm),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.arrow_back_filled),
                                        contentDescription = "Back",
                                    )
                                }
                                SettingsScreen(modifier = Modifier.weight(1f))
                            }
                        } else {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state == EngineConnectionState.CHECKING) {
                                    // Initial poll hasn't returned yet — show a calm connecting
                                    // state rather than the alarming "not available" panel, which
                                    // would otherwise flash for every user whose engine IS running.
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 3.dp,
                                        )
                                        Text(
                                            text = "Connecting to $engineName…",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Looking for your container engine.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.warning),
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Container engine is not available",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Please start your container engine and the dashboard will connect automatically.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        OutlinedButton(
                                            onClick = { viewModel.navigate(Screen.Settings.route) },
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.settings),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(Spacing.sm))
                                            Text("Settings")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val containers by viewModel.containers.collectAsState()
                    val containersVm: ContainersScreenViewModel =
                        viewModel { ContainersScreenViewModel() }
                    var showPalette by remember { mutableStateOf(false) }
                    var showCheatsheet by remember { mutableStateOf(false) }
                    val searchFocus = remember { FocusRequester() }
                    val coroutineScope = rememberCoroutineScope()

                    // P0 fix: palette-triggered delete confirmation state
                    var pendingPaletteDeleteName by remember { mutableStateOf("") }
                    var pendingPaletteDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }

                    val paletteActions =
                        buildPaletteActions(
                            containers = containers,
                            onNavigate = { screen -> viewModel.navigate(screen.route) },
                            onShowLogs = { c ->
                                viewModel.showContainerLogs(c)
                                navigator.showExtraPane()
                            },
                            onStart = { id -> containersVm.startContainer(id) },
                            onStop = { id -> containersVm.stopContainer(id) },
                            onRestart = { id -> containersVm.restartContainer(id) },
                            onRemove = { id -> containersVm.removeContainer(id) },
                            onAskConfirmRemove = { name, action ->
                                pendingPaletteDeleteName = name
                                pendingPaletteDeleteAction = action
                            },
                        )

                    CompositionLocalProvider(LocalSearchFocusRequester provides searchFocus) {
                        AppShortcutScope(
                            onNavigate = { screen -> viewModel.navigate(screen.route) },
                            onOpenPalette = { showPalette = true },
                            onCloseOverlays = {
                                when {
                                    showPalette -> showPalette = false
                                    showCheatsheet -> showCheatsheet = false
                                    else -> navigator.hideExtraPane()
                                }
                            },
                            onFocusSearch = {
                                runCatching { searchFocus.requestFocus() }
                            },
                            onRefresh = {
                                coroutineScope.launch {
                                    runCatching {
                                        AppModule.dockerRepository.refreshContainers()
                                    }
                                }
                            },
                            onShowCheatsheet = { showCheatsheet = true },
                        ) {
                            ThreePaneScaffold(
                                navigator = navigator,
                                paneLayout = logsPaneLayout,
                                listPaneWidth = 220.dp,
                                listPaneHeader = {
                                    WindowChromeBar()
                                },
                                detailPaneTopOverlay = {
                                    TopBarDragArea {
                                        Spacer(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(32.dp),
                                        )
                                    }
                                },
                                listPane = {
                                    Sidebar(
                                        currentRoute = currentRoute,
                                        onNavigate = { screen -> viewModel.navigate(screen.route) },
                                        connectionState = connectionState,
                                        engineName = engineName,
                                        // Issue 8 fix: wire discoverable affordance
                                        onOpenPalette = { showPalette = true },
                                        onShowShortcuts = { showCheatsheet = true },
                                    )
                                },
                                detailPane = {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.background),
                                    ) {
                                        AnimatedContent(
                                            targetState = currentRoute,
                                            transitionSpec = {
                                                (fadeIn() + scaleIn(initialScale = 0.96f))
                                                    .togetherWith(fadeOut())
                                            },
                                            label = "DetailPaneScreenTransition",
                                        ) { route ->
                                            when (route) {
                                                Screen.Dashboard.route ->
                                                    DashboardScreen(
                                                        onNavigate = { screen -> viewModel.navigate(screen.route) },
                                                    )
                                                Screen.Containers.route ->
                                                    ContainersScreen(
                                                        onShowLogs = { container ->
                                                            viewModel.showContainerLogs(container)
                                                            navigator.showExtraPane()
                                                        },
                                                        onShowGroupLogs = { containersList ->
                                                            viewModel.showGroupLogs(containersList)
                                                            navigator.showExtraPane()
                                                        },
                                                        currentLogsContainerId = logsPaneState.container?.id,
                                                        paneActionContainerId =
                                                            logsPaneState.container
                                                                ?.id
                                                                ?.takeIf { logsPaneState.isPauseActionInProgress },
                                                        logsPaneLayout = logsPaneLayout,
                                                        onLogsPaneLayoutChange = { viewModel.setLogsPaneLayout(it) },
                                                    )
                                                Screen.Images.route -> ImagesScreen()
                                                Screen.Volumes.route -> VolumesScreen()
                                                Screen.Networks.route -> NetworksScreen()
                                                Screen.Monitoring.route -> MonitoringScreen()
                                                Screen.AppLogs.route -> AppLogsScreen()
                                                Screen.Settings.route -> SettingsScreen()
                                            }
                                        }
                                    }
                                },
                                extraPane = {
                                    ContainerExtraPane(
                                        logsState = logsPaneState,
                                        onRefreshLogs = { viewModel.refreshLogs() },
                                        onClose = {
                                            navigator.hideExtraPane()
                                            viewModel.clearLogs()
                                        },
                                        onSaveLogs = {
                                            viewModel.saveAllLogs { name, allLogs ->
                                                saveLogsToFile("$name.log", allLogs)
                                            }
                                        },
                                        onPauseContainer = { viewModel.pauseLogsContainer() },
                                        onUnpauseContainer = { viewModel.unpauseLogsContainer() },
                                        onRestartContainer = { viewModel.restartLogsContainer() },
                                        onRemoveContainer = {
                                            viewModel.removeLogsContainer()
                                            navigator.hideExtraPane()
                                        },
                                        consoleContent = {
                                            logsPaneState.container?.let { container ->
                                                consoleContent(container.id, darkTheme)
                                            }
                                        },
                                        onFilesTabSelected = {
                                            logsPaneState.container?.let { viewModel.openFilesTab(it) }
                                        },
                                        filesContent = {
                                            FilesTabContent(
                                                state = filesPaneState,
                                                onToggleNode = { viewModel.toggleNode(it) },
                                                onFileClick = { viewModel.openFile(it) },
                                                onRefresh = { viewModel.refreshFiles() },
                                                onCloseViewer = { viewModel.closeFileViewer() },
                                                onDownload = { entry ->
                                                    viewModel.downloadFile(entry) { name, bytes ->
                                                        saveBytesToFile(name, bytes)
                                                    }
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        }
                    }

                    if (showPalette) {
                        CommandPalette(
                            actions = paletteActions,
                            onClose = { showPalette = false },
                        )
                    }
                    if (showCheatsheet) {
                        KeyboardShortcutsOverlay(onDismiss = { showCheatsheet = false })
                    }

                    // P0 fix: confirm dialog for palette-triggered deletes
                    pendingPaletteDeleteAction?.let { action ->
                        ConfirmActionDialog(
                            title = "Delete container?",
                            body = "This will force-stop and remove \"$pendingPaletteDeleteName\".",
                            confirmLabel = "Delete",
                            destructive = true,
                            onConfirm = {
                                action()
                                pendingPaletteDeleteAction = null
                            },
                            onDismiss = { pendingPaletteDeleteAction = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowChromeBar() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowChromeLeading()
            Box(modifier = Modifier.weight(1f)) {
                TopBarDragArea {
                    Spacer(modifier = Modifier.fillMaxWidth().height(32.dp))
                }
            }
            WindowChromeTrailing()
        }
    }
}

private fun buildPaletteActions(
    containers: List<com.containerdashboard.data.models.Container>,
    onNavigate: (Screen) -> Unit,
    onShowLogs: (com.containerdashboard.data.models.Container) -> Unit,
    onStart: (String) -> Unit,
    onStop: (String) -> Unit,
    onRestart: (String) -> Unit,
    onRemove: (String) -> Unit,
    // P0 fix: confirm callback — caller must show the dialog and call onRemove if user confirms
    onAskConfirmRemove: (containerName: String, onConfirmed: () -> Unit) -> Unit,
): List<PaletteAction> {
    val actions = mutableListOf<PaletteAction>()

    Screen.mainScreens.forEach { screen ->
        actions.add(
            PaletteAction(
                id = "nav-${screen.route}",
                label = "Go to ${screen.title}",
                section = "Navigation",
                icon = screen.icon,
                onRun = { onNavigate(screen) },
            ),
        )
    }
    actions.add(
        PaletteAction(
            id = "nav-settings",
            label = "Go to Settings",
            section = "Navigation",
            keyboardHint = if (isMacHost) "\u2318 ," else "Ctrl ,",
            icon = Screen.Settings.icon,
            onRun = { onNavigate(Screen.Settings) },
        ),
    )

    containers.forEach { c ->
        val name = c.displayName
        actions.add(
            PaletteAction(
                id = "logs-${c.id}",
                label = "View logs: $name",
                subtitle = if (c.isRunning) "Running" else "Stopped",
                section = "Containers",
                onRun = { onShowLogs(c) },
            ),
        )
        if (c.isRunning) {
            actions.add(
                PaletteAction(
                    id = "stop-${c.id}",
                    label = "Stop: $name",
                    section = "Containers",
                    onRun = { onStop(c.id) },
                ),
            )
            actions.add(
                PaletteAction(
                    id = "restart-${c.id}",
                    label = "Restart: $name",
                    section = "Containers",
                    onRun = { onRestart(c.id) },
                ),
            )
        } else {
            actions.add(
                PaletteAction(
                    id = "start-${c.id}",
                    label = "Start: $name",
                    section = "Containers",
                    onRun = { onStart(c.id) },
                ),
            )
        }
        actions.add(
            PaletteAction(
                id = "remove-${c.id}",
                label = "Delete: $name",
                section = "Containers",
                // P0 fix: funnel through confirm dialog instead of calling removeContainer directly
                onRun = { onAskConfirmRemove(name) { onRemove(c.id) } },
            ),
        )
    }

    return actions
}
