package com.containerdashboard.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.AttachedContainer
import com.containerdashboard.data.models.ContainerInspect
import com.containerdashboard.data.models.EnvVar
import com.containerdashboard.data.models.ImageInspect
import com.containerdashboard.data.models.IpamConfigEntry
import com.containerdashboard.data.models.MountInfo
import com.containerdashboard.data.models.NetworkAttachment
import com.containerdashboard.data.models.NetworkInspect
import com.containerdashboard.data.models.PortMapping
import com.containerdashboard.data.models.VolumeInspect
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.theme.Radius
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Target resource the details dialog should load and render.
 *
 * Containers get the full tabbed experience (Overview / Environment /
 * Mounts / Ports / Networks / Labels / Raw JSON). Image/Network/Volume
 * currently render only the raw JSON tab with a "rich tabs coming soon"
 * placeholder — they'll grow richer in a follow-up.
 */
sealed class DetailsTarget {
    abstract val displayName: String

    data class ContainerTarget(
        val id: String,
        override val displayName: String,
    ) : DetailsTarget()

    data class ImageTarget(
        val id: String,
        override val displayName: String,
    ) : DetailsTarget()

    data class NetworkTarget(
        val id: String,
        override val displayName: String,
    ) : DetailsTarget()

    data class VolumeTarget(
        val name: String,
    ) : DetailsTarget() {
        override val displayName: String get() = name
    }
}

private sealed interface DetailsState {
    data object Loading : DetailsState

    data class Error(
        val message: String,
    ) : DetailsState

    data class ContainerLoaded(
        val inspect: ContainerInspect,
    ) : DetailsState

    data class ImageLoaded(
        val inspect: ImageInspect,
    ) : DetailsState

    data class NetworkLoaded(
        val inspect: NetworkInspect,
    ) : DetailsState

    data class VolumeLoaded(
        val inspect: VolumeInspect,
    ) : DetailsState
}

private class ResourceDetailsViewModel(
    private val target: DetailsTarget,
) : ViewModel() {
    private val repo get() = AppModule.dockerRepository

    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = DetailsState.Loading
        viewModelScope.launch {
            val next =
                when (val t = target) {
                    is DetailsTarget.ContainerTarget ->
                        repo.inspectContainer(t.id).fold(
                            onSuccess = { DetailsState.ContainerLoaded(it) },
                            onFailure = { DetailsState.Error(it.message ?: "Failed to inspect container") },
                        )

                    is DetailsTarget.ImageTarget ->
                        repo.inspectImage(t.id).fold(
                            onSuccess = { DetailsState.ImageLoaded(it) },
                            onFailure = { DetailsState.Error(it.message ?: "Failed to inspect image") },
                        )

                    is DetailsTarget.NetworkTarget ->
                        repo.inspectNetwork(t.id).fold(
                            onSuccess = { DetailsState.NetworkLoaded(it) },
                            onFailure = { DetailsState.Error(it.message ?: "Failed to inspect network") },
                        )

                    is DetailsTarget.VolumeTarget ->
                        repo.inspectVolume(t.name).fold(
                            onSuccess = { DetailsState.VolumeLoaded(it) },
                            onFailure = { DetailsState.Error(it.message ?: "Failed to inspect volume") },
                        )
                }
            _state.value = next
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ResourceDetailsDialog(
    target: DetailsTarget,
    onDismiss: () -> Unit,
) {
    val vm =
        viewModel(key = detailsKey(target)) {
            ResourceDetailsViewModel(target)
        }
    val state by collectStateAsState(vm.state)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .width(720.dp)
                    .height(560.dp)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 12.dp,
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DetailsHeader(target = target, onClose = onDismiss)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                )

                when (val s = state) {
                    is DetailsState.Loading -> LoadingState()
                    is DetailsState.Error ->
                        ErrorState(message = s.message, onRetry = { vm.load() })
                    is DetailsState.ContainerLoaded -> ContainerTabs(s.inspect)
                    is DetailsState.ImageLoaded -> ImageTabs(s.inspect)
                    is DetailsState.NetworkLoaded -> NetworkTabs(s.inspect)
                    is DetailsState.VolumeLoaded -> VolumeTabs(s.inspect)
                }
            }
        }
    }
}

private fun detailsKey(target: DetailsTarget): String =
    when (target) {
        is DetailsTarget.ContainerTarget -> "container:${target.id}"
        is DetailsTarget.ImageTarget -> "image:${target.id}"
        is DetailsTarget.NetworkTarget -> "network:${target.id}"
        is DetailsTarget.VolumeTarget -> "volume:${target.name}"
    }

@Composable
private fun <T> collectStateAsState(flow: StateFlow<T>): androidx.compose.runtime.State<T> {
    val state = remember(flow) { mutableStateOf(flow.value) }
    LaunchedEffect(flow) {
        flow.collect { state.value = it }
    }
    return state
}

@Composable
private fun DetailsHeader(
    target: DetailsTarget,
    onClose: () -> Unit,
) {
    val icon: ImageVector
    val subtitle: String
    when (target) {
        is DetailsTarget.ContainerTarget -> {
            icon = Icons.Outlined.Inventory2
            subtitle = "Container details"
        }
        is DetailsTarget.ImageTarget -> {
            icon = Icons.Outlined.Image
            subtitle = "Image details"
        }
        is DetailsTarget.NetworkTarget -> {
            icon = Icons.Outlined.Hub
            subtitle = "Network details"
        }
        is DetailsTarget.VolumeTarget -> {
            icon = Icons.Outlined.Folder
            subtitle = "Volume details"
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        RoundedCornerShape(Radius.md),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Failed to load details",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

private enum class ContainerTab(
    val title: String,
) {
    OVERVIEW("Overview"),
    ENVIRONMENT("Environment"),
    MOUNTS("Mounts"),
    PORTS("Ports"),
    NETWORKS("Networks"),
    LABELS("Labels"),
    RAW("Raw JSON"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainerTabs(inspect: ContainerInspect) {
    var selected by remember { mutableStateOf(ContainerTab.OVERVIEW) }
    val tabs = ContainerTab.entries

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    text = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                ContainerTab.OVERVIEW -> OverviewTab(inspect)
                ContainerTab.ENVIRONMENT -> EnvironmentTab(inspect.environment)
                ContainerTab.MOUNTS -> MountsTab(inspect.mounts)
                ContainerTab.PORTS -> PortsTab(inspect.ports)
                ContainerTab.NETWORKS -> NetworksTab(inspect.networks)
                ContainerTab.LABELS -> LabelsTab(inspect.labels)
                ContainerTab.RAW -> RawJsonTab(inspect.rawJson)
            }
        }
    }
}

private enum class ImageTab(
    val title: String,
) {
    OVERVIEW("Overview"),
    LAYERS("Layers"),
    ENVIRONMENT("Environment"),
    CONFIG("Config"),
    LABELS("Labels"),
    RAW("Raw JSON"),
}

private enum class NetworkTab(
    val title: String,
) {
    OVERVIEW("Overview"),
    ATTACHED("Attached"),
    IPAM("IPAM"),
    OPTIONS("Options"),
    LABELS("Labels"),
    RAW("Raw JSON"),
}

private enum class VolumeTab(
    val title: String,
) {
    OVERVIEW("Overview"),
    OPTIONS("Options"),
    LABELS("Labels"),
    RAW("Raw JSON"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageTabs(inspect: ImageInspect) {
    var selected by remember { mutableStateOf(ImageTab.OVERVIEW) }
    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            ImageTab.entries.forEach { tab ->
                Tab(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    text = {
                        Text(tab.title, style = MaterialTheme.typography.labelLarge)
                    },
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                ImageTab.OVERVIEW -> ImageOverviewTab(inspect)
                ImageTab.LAYERS -> ImageLayersTab(inspect.layers)
                ImageTab.ENVIRONMENT -> EnvironmentTab(inspect.environment)
                ImageTab.CONFIG -> ImageConfigTab(inspect)
                ImageTab.LABELS -> LabelsTab(inspect.labels)
                ImageTab.RAW -> RawJsonTab(inspect.rawJson)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkTabs(inspect: NetworkInspect) {
    var selected by remember { mutableStateOf(NetworkTab.OVERVIEW) }
    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            NetworkTab.entries.forEach { tab ->
                Tab(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    text = {
                        Text(tab.title, style = MaterialTheme.typography.labelLarge)
                    },
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                NetworkTab.OVERVIEW -> NetworkOverviewTab(inspect)
                NetworkTab.ATTACHED -> NetworkAttachedTab(inspect.attachedContainers)
                NetworkTab.IPAM -> NetworkIpamTab(inspect.ipamDriver, inspect.ipamConfig)
                NetworkTab.OPTIONS -> OptionsTab(inspect.options)
                NetworkTab.LABELS -> LabelsTab(inspect.labels)
                NetworkTab.RAW -> RawJsonTab(inspect.rawJson)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeTabs(inspect: VolumeInspect) {
    var selected by remember { mutableStateOf(VolumeTab.OVERVIEW) }
    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            VolumeTab.entries.forEach { tab ->
                Tab(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    text = {
                        Text(tab.title, style = MaterialTheme.typography.labelLarge)
                    },
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                VolumeTab.OVERVIEW -> VolumeOverviewTab(inspect)
                VolumeTab.OPTIONS -> OptionsTab(inspect.options)
                VolumeTab.LABELS -> LabelsTab(inspect.labels)
                VolumeTab.RAW -> RawJsonTab(inspect.rawJson)
            }
        }
    }
}

@Composable
private fun OverviewTab(inspect: ContainerInspect) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyValueRow(
            label = "ID",
            value = inspect.id.take(12),
            monospace = true,
            trailing = {
                CopyIconButton(
                    tooltip = "Copy full ID",
                    onClick = { clipboard.setText(AnnotatedString(inspect.id)) },
                )
            },
        )
        KeyValueRow(label = "Name", value = inspect.name)
        KeyValueRow(label = "Image", value = inspect.image)
        KeyValueRow(
            label = "Image ID",
            value = inspect.imageId.removePrefix("sha256:").take(12),
            monospace = true,
        )
        KeyValueRow(label = "Status", value = inspect.status)
        KeyValueRow(label = "State", value = inspect.state)
        KeyValueRow(label = "Created", value = inspect.createdAt)
        KeyValueRow(label = "Started", value = inspect.startedAt)
        KeyValueRow(label = "Command", value = inspect.command, monospace = true)
        KeyValueRow(
            label = "Entrypoint",
            value = inspect.entrypoint.joinToString(" ").ifBlank { "-" },
            monospace = true,
        )
        KeyValueRow(label = "Working dir", value = inspect.workingDir.ifBlank { "-" }, monospace = true)
        KeyValueRow(label = "User", value = inspect.user.ifBlank { "-" })
        KeyValueRow(label = "Hostname", value = inspect.hostname.ifBlank { "-" })
        KeyValueRow(label = "Platform", value = inspect.platform.ifBlank { "-" })
        KeyValueRow(label = "Restart policy", value = inspect.restartPolicy)
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(128.dp),
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value.ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
private fun CopyIconButton(
    tooltip: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(20.dp)) {
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = tooltip,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnvironmentTab(env: List<EnvVar>) {
    if (env.isEmpty()) {
        EmptyState("No environment variables")
        return
    }
    TwoColumnTable(
        left = "Key",
        right = "Value",
        rows = env.map { it.key to it.value },
        monospace = true,
    )
}

@Composable
private fun LabelsTab(labels: Map<String, String>) {
    if (labels.isEmpty()) {
        EmptyState("No labels")
        return
    }
    TwoColumnTable(
        left = "Key",
        right = "Value",
        rows = labels.entries.map { it.key to it.value },
        monospace = true,
    )
}

@Composable
private fun TwoColumnTable(
    left: String,
    right: String,
    rows: List<Pair<String, String>>,
    monospace: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = left,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(220.dp),
            )
            Text(
                text = right,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            rows.forEach { (k, v) ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SelectionContainer(modifier = Modifier.width(220.dp)) {
                        Text(
                            text = k,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (monospace) FontFamily.Monospace else null,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = v,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (monospace) FontFamily.Monospace else null,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun MountsTab(mounts: List<MountInfo>) {
    if (mounts.isEmpty()) {
        EmptyState("No mounts")
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        mounts.forEach { mount ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(6.dp),
                        ).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Badge(text = mount.type.uppercase())
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = mount.source.ifBlank { "-" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SelectionContainer {
                            Text(
                                text = mount.destination,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (mount.mode.isNotBlank()) {
                        Text(
                            text = "mode: ${mount.mode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Badge(
                    text = if (mount.rw) "RW" else "RO",
                    emphasized = mount.rw,
                )
            }
        }
    }
}

@Composable
private fun PortsTab(ports: List<PortMapping>) {
    if (ports.isEmpty()) {
        EmptyState("No published ports")
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            TableHeaderCell("Container port", 140.dp)
            TableHeaderCell("Protocol", 90.dp)
            TableHeaderCell("Host IP", 160.dp)
            TableHeaderCell("Host port", 120.dp)
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            ports.forEach { port ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableCell(port.containerPort.toString(), 140.dp, monospace = true)
                    TableCell(port.protocol, 90.dp)
                    TableCell(port.hostIp ?: "-", 160.dp, monospace = true)
                    TableCell(port.hostPort?.toString() ?: "-", 120.dp, monospace = true)
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    monospace: Boolean = false,
) {
    SelectionContainer(modifier = Modifier.width(width)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworksTab(networks: List<NetworkAttachment>) {
    if (networks.isEmpty()) {
        EmptyState("Not attached to any network")
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        networks.forEach { net ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            RoundedCornerShape(Radius.md),
                        ).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = net.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                NetworkLine(label = "IP", value = net.ipAddress.ifBlank { "-" })
                NetworkLine(label = "Gateway", value = net.gateway.ifBlank { "-" })
                NetworkLine(label = "MAC", value = net.macAddress.ifBlank { "-" })
                if (net.aliases.isNotEmpty()) {
                    Text(
                        text = "Aliases",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        net.aliases.forEach { alias ->
                            Badge(text = alias)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkLine(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Badge(
    text: String,
    emphasized: Boolean = false,
) {
    val container =
        if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val content =
        if (emphasized) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .background(container, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RawJsonTab(json: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            @Suppress("DEPRECATION")
            val clipboard = LocalClipboardManager.current
            CopyIconButton(
                tooltip = "Copy JSON",
                onClick = { clipboard.setText(AnnotatedString(json)) },
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        RoundedCornerShape(Radius.md),
                    ).border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(Radius.md),
                    ),
        ) {
            SelectionContainer(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
            ) {
                Text(
                    text = json,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ImageOverviewTab(inspect: ImageInspect) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyValueRow(
            label = "ID",
            value = inspect.shortId,
            monospace = true,
            trailing = {
                CopyIconButton(tooltip = "Copy ID") {
                    clipboard.setText(
                        androidx.compose.ui.text
                            .AnnotatedString(inspect.id),
                    )
                }
            },
        )
        KeyValueRow(label = "Tags", value = inspect.repoTags.joinToString(", "))
        KeyValueRow(label = "Digests", value = inspect.repoDigests.joinToString(", "), monospace = true)
        KeyValueRow(label = "Architecture", value = inspect.architecture)
        KeyValueRow(label = "OS", value = inspect.os)
        KeyValueRow(label = "Size", value = formatBytes(inspect.size))
        KeyValueRow(label = "Virtual size", value = formatBytes(inspect.virtualSize))
        KeyValueRow(label = "Created", value = inspect.createdAt)
        KeyValueRow(label = "Docker version", value = inspect.dockerVersion)
        KeyValueRow(label = "Author", value = inspect.author)
    }
}

@Composable
private fun ImageLayersTab(layers: List<String>) {
    if (layers.isEmpty()) {
        EmptyState("No layer information available")
        return
    }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        layers.forEachIndexed { index, digest ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = digest,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                CopyIconButton(tooltip = "Copy digest") {
                    clipboard.setText(
                        androidx.compose.ui.text
                            .AnnotatedString(digest),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageConfigTab(inspect: ImageInspect) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyValueRow(label = "Entrypoint", value = inspect.entrypoint.joinToString(" "), monospace = true)
        KeyValueRow(label = "Command", value = inspect.command.joinToString(" "), monospace = true)
        KeyValueRow(label = "Working dir", value = inspect.workingDir, monospace = true)
        KeyValueRow(label = "User", value = inspect.user)
        KeyValueRow(
            label = "Exposed ports",
            value = if (inspect.exposedPorts.isEmpty()) "" else inspect.exposedPorts.joinToString(", "),
        )
    }
}

@Composable
private fun NetworkOverviewTab(inspect: NetworkInspect) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyValueRow(
            label = "ID",
            value = inspect.shortId,
            monospace = true,
            trailing = {
                CopyIconButton(tooltip = "Copy ID") {
                    clipboard.setText(
                        androidx.compose.ui.text
                            .AnnotatedString(inspect.id),
                    )
                }
            },
        )
        KeyValueRow(label = "Name", value = inspect.name)
        KeyValueRow(label = "Driver", value = inspect.driver)
        KeyValueRow(label = "Scope", value = inspect.scope)
        KeyValueRow(label = "Attachable", value = inspect.attachable.toString())
        KeyValueRow(label = "Internal", value = inspect.internal.toString())
        KeyValueRow(label = "IPv6 enabled", value = inspect.ipv6Enabled.toString())
    }
}

@Composable
private fun NetworkAttachedTab(attached: List<AttachedContainer>) {
    if (attached.isEmpty()) {
        EmptyState("No containers attached to this network")
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        attached.forEach { c ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(Radius.sm),
                        ).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = c.name.ifBlank { "(unnamed)" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (c.id.isNotBlank()) {
                    KeyValueRow(label = "ID", value = c.id.take(12), monospace = true)
                }
                if (c.ipv4Address.isNotBlank()) {
                    KeyValueRow(label = "IPv4", value = c.ipv4Address, monospace = true)
                }
                if (c.ipv6Address.isNotBlank()) {
                    KeyValueRow(label = "IPv6", value = c.ipv6Address, monospace = true)
                }
                if (c.macAddress.isNotBlank()) {
                    KeyValueRow(label = "MAC", value = c.macAddress, monospace = true)
                }
            }
        }
    }
}

@Composable
private fun NetworkIpamTab(
    driver: String,
    entries: List<IpamConfigEntry>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeyValueRow(label = "Driver", value = driver)
        if (entries.isEmpty()) {
            Text(
                text = "No IPAM config entries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        entries.forEachIndexed { idx, entry ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(Radius.sm),
                        ).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Config #${idx + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                KeyValueRow(label = "Subnet", value = entry.subnet, monospace = true)
                KeyValueRow(label = "Gateway", value = entry.gateway, monospace = true)
                if (entry.ipRange.isNotBlank()) {
                    KeyValueRow(label = "IP range", value = entry.ipRange, monospace = true)
                }
            }
        }
    }
}

@Composable
private fun VolumeOverviewTab(inspect: VolumeInspect) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KeyValueRow(label = "Name", value = inspect.name, monospace = true)
        KeyValueRow(label = "Driver", value = inspect.driver)
        KeyValueRow(label = "Mountpoint", value = inspect.mountpoint, monospace = true)
        KeyValueRow(label = "Scope", value = inspect.scope)
        KeyValueRow(label = "Created", value = inspect.createdAt)
    }
}

@Composable
private fun OptionsTab(options: Map<String, String>) {
    if (options.isEmpty()) {
        EmptyState("No options set")
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.entries.sortedBy { it.key }.forEach { (k, v) ->
            KeyValueRow(label = k, value = v, monospace = true)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "-"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
