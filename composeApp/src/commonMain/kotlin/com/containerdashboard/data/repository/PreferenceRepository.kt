package com.containerdashboard.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.containerdashboard.data.datastore.dataStorePreferencesInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

object PreferenceRepository {

    private val dataStore: DataStore<Preferences> by lazy { dataStorePreferencesInstance }

    private val ENGINE_HOST by lazy { stringPreferencesKey("engine_host") }

    var engineHost: String
        get() = runBlocking { dataStore.data.firstOrNull()?.get(ENGINE_HOST) ?: "unix:///var/run/docker.sock" }
        set(value) {
            runBlocking {
                dataStore.edit {
                    it[ENGINE_HOST] = value
                }
            }
        }
}
