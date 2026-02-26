package com.containerdashboard.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.containerdashboard.data.datastore.dataStorePreferencesInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

object PreferenceRepository {
    private val dataStore: DataStore<Preferences> by lazy { dataStorePreferencesInstance }

    private val ENGINE_HOST by lazy { stringPreferencesKey("engine_host") }
    private val SHOW_SYSTEM_CONTAINERS by lazy { booleanPreferencesKey("show_system_containers") }
    private val DARK_THEME by lazy { booleanPreferencesKey("dark_theme") }
    private val CONFIRM_BEFORE_DELETE by lazy { booleanPreferencesKey("confirm_before_delete") }

    var engineHost: String
        get() = runBlocking { dataStore.data.firstOrNull()?.get(ENGINE_HOST) ?: "unix:///var/run/docker.sock" }
        set(value) {
            runBlocking {
                dataStore.edit {
                    it[ENGINE_HOST] = value
                }
            }
        }

    fun engineHost(): Flow<String> =
        dataStore.data.map {
            it[ENGINE_HOST] ?: "unix:///var/run/docker.sock"
        }

    var darkTheme: Boolean
        get() = runBlocking { dataStore.data.firstOrNull()?.get(DARK_THEME) ?: true }
        set(value) {
            runBlocking {
                dataStore.edit {
                    it[DARK_THEME] = value
                }
            }
        }

    fun darkTheme(): Flow<Boolean> =
        dataStore.data.map {
            it[DARK_THEME] ?: true
        }

    var showSystemContainers: Boolean
        get() = runBlocking { dataStore.data.firstOrNull()?.get(SHOW_SYSTEM_CONTAINERS) ?: false }
        set(value) {
            runBlocking {
                dataStore.edit {
                    it[SHOW_SYSTEM_CONTAINERS] = value
                }
            }
        }

    fun showSystemContainers(): Flow<Boolean> =
        dataStore.data.map {
            it[SHOW_SYSTEM_CONTAINERS] ?: false
        }

    var confirmBeforeDelete: Boolean
        get() = runBlocking { dataStore.data.firstOrNull()?.get(CONFIRM_BEFORE_DELETE) ?: true }
        set(value) {
            runBlocking {
                dataStore.edit {
                    it[CONFIRM_BEFORE_DELETE] = value
                }
            }
        }

    fun confirmBeforeDelete(): Flow<Boolean> =
        dataStore.data.map {
            it[CONFIRM_BEFORE_DELETE] ?: true
        }
}
