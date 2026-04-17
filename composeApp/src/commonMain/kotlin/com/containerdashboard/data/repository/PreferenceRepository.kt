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

object PreferenceRepository {
    private val dataStore: DataStore<Preferences> by lazy { dataStorePreferencesInstance }

    private val ENGINE_HOST by lazy { stringPreferencesKey("engine_host") }
    private val SHOW_SYSTEM_CONTAINERS by lazy { booleanPreferencesKey("show_system_containers") }
    private val DARK_THEME by lazy { booleanPreferencesKey("dark_theme") }
    private val CONFIRM_BEFORE_DELETE by lazy { booleanPreferencesKey("confirm_before_delete") }
    private val LOGS_PANE_LAYOUT by lazy { stringPreferencesKey("logs_pane_layout") }
    private val TRAY_REFRESH_RATE by lazy { intPreferencesKey("tray_refresh_rate_seconds") }
    private val LOGS_WORD_WRAP by lazy { booleanPreferencesKey("logs_word_wrap") }
    private val LOGS_MAX_LINES by lazy { intPreferencesKey("logs_max_lines") }
    private val COL_WIDTH_NAME by lazy { floatPreferencesKey("container_col_width_name") }
    private val COL_WIDTH_IMAGE by lazy { floatPreferencesKey("container_col_width_image") }
    private val COL_WIDTH_STATUS by lazy { floatPreferencesKey("container_col_width_status") }
    private val COL_WIDTH_PORTS by lazy { floatPreferencesKey("container_col_width_ports") }

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

    fun darkTheme(): Flow<Boolean> =
        dataStore.data.map {
            it[DARK_THEME] ?: true
        }

    suspend fun setDarkTheme(value: Boolean) {
        dataStore.edit { it[DARK_THEME] = value }
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
}
