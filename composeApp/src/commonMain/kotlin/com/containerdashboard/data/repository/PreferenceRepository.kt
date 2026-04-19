package com.containerdashboard.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.containerdashboard.data.DockerHostConfig
import com.containerdashboard.data.datastore.dataStorePreferencesInstance
import com.containerdashboard.ui.screens.viewmodel.MonitoringAggregation
import com.containerdashboard.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

data class ContainerColumnWidths(
    val name: Float,
    val image: Float,
    val status: Float,
    val ports: Float,
) {
    companion object {
        val Default = ContainerColumnWidths(name = 180f, image = 200f, status = 110f, ports = 180f)
        const val MIN = 60f
    }
}

data class VolumeColumnWeights(
    val name: Float,
    val driver: Float,
    val mountpoint: Float,
) {
    companion object {
        val Default = VolumeColumnWeights(name = 1.5f, driver = 0.7f, mountpoint = 2f)
    }
}

data class WindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean,
)

object PreferenceRepository {
    private val dataStore: DataStore<Preferences> by lazy { dataStorePreferencesInstance }

    private val ENGINE_HOST by lazy { stringPreferencesKey("engine_host") }
    private val SHOW_SYSTEM_CONTAINERS by lazy { booleanPreferencesKey("show_system_containers") }
    private val DARK_THEME by lazy { booleanPreferencesKey("dark_theme") }
    private val THEME_MODE by lazy { stringPreferencesKey("theme_mode") }
    private val MONITORING_AGGREGATION by lazy { stringPreferencesKey("monitoring_aggregation") }
    private val CONFIRM_BEFORE_DELETE by lazy { booleanPreferencesKey("confirm_before_delete") }
    private val LOGS_PANE_LAYOUT by lazy { stringPreferencesKey("logs_pane_layout") }
    private val TRAY_REFRESH_RATE by lazy { intPreferencesKey("tray_refresh_rate_seconds") }
    private val LOGS_WORD_WRAP by lazy { booleanPreferencesKey("logs_word_wrap") }
    private val LOGS_MAX_LINES by lazy { intPreferencesKey("logs_max_lines") }
    private val COL_WIDTH_NAME by lazy { floatPreferencesKey("container_col_width_name") }
    private val COL_WIDTH_IMAGE by lazy { floatPreferencesKey("container_col_width_image") }
    private val COL_WIDTH_STATUS by lazy { floatPreferencesKey("container_col_width_status") }
    private val COL_WIDTH_PORTS by lazy { floatPreferencesKey("container_col_width_ports") }
    private val VOL_COL_WEIGHT_NAME by lazy { floatPreferencesKey("volume_col_weight_name") }
    private val VOL_COL_WEIGHT_DRIVER by lazy { floatPreferencesKey("volume_col_weight_driver") }
    private val VOL_COL_WEIGHT_MOUNT by lazy { floatPreferencesKey("volume_col_weight_mount") }
    private val WINDOW_X by lazy { intPreferencesKey("window_x") }
    private val WINDOW_Y by lazy { intPreferencesKey("window_y") }
    private val WINDOW_W by lazy { intPreferencesKey("window_width") }
    private val WINDOW_H by lazy { intPreferencesKey("window_height") }
    private val WINDOW_MAXIMIZED by lazy { booleanPreferencesKey("window_maximized") }
    private val LAST_ROUTE by lazy { stringPreferencesKey("last_route") }
    private val LOGS_PANE_RIGHT_W by lazy { intPreferencesKey("logs_pane_right_width_dp") }
    private val LOGS_PANE_BOTTOM_H by lazy { intPreferencesKey("logs_pane_bottom_height_dp") }

    private val DEFAULT_ENGINE_HOST by lazy { DockerHostConfig.detectDockerHost() }

    /**
     * Synchronous read used only for initial app startup (AppModule lazy init).
     * All other reads should use the Flow-based [engineHost] function.
     */
    val initialEngineHost: String
        get() = runBlocking { dataStore.data.firstOrNull()?.get(ENGINE_HOST) ?: DEFAULT_ENGINE_HOST }

    fun engineHost(): Flow<String> =
        dataStore.data.map {
            it[ENGINE_HOST] ?: DEFAULT_ENGINE_HOST
        }

    suspend fun setEngineHost(value: String) {
        dataStore.edit { it[ENGINE_HOST] = value }
    }

    fun themeMode(): Flow<ThemeMode> =
        dataStore.data.map { prefs ->
            val stored = prefs[THEME_MODE]
            if (stored != null) {
                ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.AUTO
            } else {
                // Legacy fallback: honor the pre-migration boolean so existing users
                // keep whatever dark/light choice they explicitly made.
                when (prefs[DARK_THEME]) {
                    true -> ThemeMode.DARK
                    false -> ThemeMode.LIGHT
                    null -> ThemeMode.AUTO
                }
            }
        }

    suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = value.name }
    }

    fun monitoringAggregation(): Flow<MonitoringAggregation> =
        dataStore.data.map { prefs ->
            val stored = prefs[MONITORING_AGGREGATION]
            MonitoringAggregation.entries.firstOrNull { it.name == stored } ?: MonitoringAggregation.ENGINE
        }

    suspend fun setMonitoringAggregation(value: MonitoringAggregation) {
        dataStore.edit { it[MONITORING_AGGREGATION] = value.name }
    }

    fun showSystemContainers(): Flow<Boolean> =
        dataStore.data.map {
            it[SHOW_SYSTEM_CONTAINERS] ?: false
        }

    suspend fun setShowSystemContainers(value: Boolean) {
        dataStore.edit { it[SHOW_SYSTEM_CONTAINERS] = value }
    }

    fun confirmBeforeDelete(): Flow<Boolean> =
        dataStore.data.map {
            it[CONFIRM_BEFORE_DELETE] ?: true
        }

    suspend fun setConfirmBeforeDelete(value: Boolean) {
        dataStore.edit { it[CONFIRM_BEFORE_DELETE] = value }
    }

    fun logsPaneLayout(): Flow<String> =
        dataStore.data.map {
            it[LOGS_PANE_LAYOUT] ?: "AUTO"
        }

    suspend fun setLogsPaneLayout(value: String) {
        dataStore.edit { it[LOGS_PANE_LAYOUT] = value }
    }

    fun trayRefreshRateSeconds(): Flow<Int> =
        dataStore.data.map {
            it[TRAY_REFRESH_RATE] ?: 5
        }

    suspend fun setTrayRefreshRateSeconds(value: Int) {
        dataStore.edit { it[TRAY_REFRESH_RATE] = value }
    }

    fun logsWordWrap(): Flow<Boolean> =
        dataStore.data.map {
            it[LOGS_WORD_WRAP] ?: true
        }

    suspend fun setLogsWordWrap(value: Boolean) {
        dataStore.edit { it[LOGS_WORD_WRAP] = value }
    }

    fun logsMaxLines(): Flow<Int> =
        dataStore.data.map {
            it[LOGS_MAX_LINES] ?: 1000
        }

    suspend fun setLogsMaxLines(value: Int) {
        dataStore.edit { it[LOGS_MAX_LINES] = value }
    }

    val logsMaxLinesSync: Int
        get() = runBlocking { dataStore.data.firstOrNull()?.get(LOGS_MAX_LINES) ?: 1000 }

    fun containerColumnWidths(): Flow<ContainerColumnWidths> =
        dataStore.data.map { prefs ->
            ContainerColumnWidths(
                name = prefs[COL_WIDTH_NAME] ?: ContainerColumnWidths.Default.name,
                image = prefs[COL_WIDTH_IMAGE] ?: ContainerColumnWidths.Default.image,
                status = prefs[COL_WIDTH_STATUS] ?: ContainerColumnWidths.Default.status,
                ports = prefs[COL_WIDTH_PORTS] ?: ContainerColumnWidths.Default.ports,
            )
        }

    suspend fun setContainerColumnWidths(widths: ContainerColumnWidths) {
        dataStore.edit { prefs ->
            prefs[COL_WIDTH_NAME] = widths.name
            prefs[COL_WIDTH_IMAGE] = widths.image
            prefs[COL_WIDTH_STATUS] = widths.status
            prefs[COL_WIDTH_PORTS] = widths.ports
        }
    }

    fun volumeColumnWeights(): Flow<VolumeColumnWeights> =
        dataStore.data.map { prefs ->
            VolumeColumnWeights(
                name = prefs[VOL_COL_WEIGHT_NAME] ?: VolumeColumnWeights.Default.name,
                driver = prefs[VOL_COL_WEIGHT_DRIVER] ?: VolumeColumnWeights.Default.driver,
                mountpoint = prefs[VOL_COL_WEIGHT_MOUNT] ?: VolumeColumnWeights.Default.mountpoint,
            )
        }

    suspend fun setVolumeColumnWeights(weights: VolumeColumnWeights) {
        dataStore.edit { prefs ->
            prefs[VOL_COL_WEIGHT_NAME] = weights.name
            prefs[VOL_COL_WEIGHT_DRIVER] = weights.driver
            prefs[VOL_COL_WEIGHT_MOUNT] = weights.mountpoint
        }
    }

    val windowBoundsSync: WindowBounds?
        get() =
            runBlocking {
                val prefs = dataStore.data.firstOrNull() ?: return@runBlocking null
                val x = prefs[WINDOW_X] ?: return@runBlocking null
                val y = prefs[WINDOW_Y] ?: return@runBlocking null
                val w = prefs[WINDOW_W] ?: return@runBlocking null
                val h = prefs[WINDOW_H] ?: return@runBlocking null
                WindowBounds(
                    x = x,
                    y = y,
                    width = w,
                    height = h,
                    maximized = prefs[WINDOW_MAXIMIZED] ?: false,
                )
            }

    suspend fun setWindowBounds(bounds: WindowBounds) {
        dataStore.edit { prefs ->
            prefs[WINDOW_X] = bounds.x
            prefs[WINDOW_Y] = bounds.y
            prefs[WINDOW_W] = bounds.width
            prefs[WINDOW_H] = bounds.height
            prefs[WINDOW_MAXIMIZED] = bounds.maximized
        }
    }

    val lastRouteSync: String?
        get() = runBlocking { dataStore.data.firstOrNull()?.get(LAST_ROUTE) }

    suspend fun setLastRoute(route: String) {
        dataStore.edit { it[LAST_ROUTE] = route }
    }

    fun logsPaneRightWidth(): Flow<Int?> = dataStore.data.map { it[LOGS_PANE_RIGHT_W] }

    val logsPaneRightWidthSync: Int?
        get() = runBlocking { dataStore.data.firstOrNull()?.get(LOGS_PANE_RIGHT_W) }

    suspend fun setLogsPaneRightWidth(dp: Int) {
        dataStore.edit { it[LOGS_PANE_RIGHT_W] = dp }
    }

    fun logsPaneBottomHeight(): Flow<Int?> = dataStore.data.map { it[LOGS_PANE_BOTTOM_H] }

    val logsPaneBottomHeightSync: Int?
        get() = runBlocking { dataStore.data.firstOrNull()?.get(LOGS_PANE_BOTTOM_H) }

    suspend fun setLogsPaneBottomHeight(dp: Int) {
        dataStore.edit { it[LOGS_PANE_BOTTOM_H] = dp }
    }
}
