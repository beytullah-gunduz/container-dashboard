package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import com.containerdashboard.data.repository.PreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class SettingsScreenViewModel : ViewModel() {
    var engineHost: String
        get() = runBlocking { PreferenceRepository.engineHost }
        set(value) {
            runBlocking { PreferenceRepository.engineHost = value }
        }

    fun engineHost(): Flow<String> = PreferenceRepository.engineHost()

    var darkTheme: Boolean
        get() = runBlocking { PreferenceRepository.darkTheme }
        set(value) {
            runBlocking { PreferenceRepository.darkTheme = value }
        }

    fun darkTheme(): Flow<Boolean> = PreferenceRepository.darkTheme()

    var autoRefresh: Boolean
        get() = runBlocking { PreferenceRepository.autoRefresh }
        set(value) {
            runBlocking { PreferenceRepository.autoRefresh = value }
        }

    fun autoRefresh(): Flow<Boolean> = PreferenceRepository.autoRefresh()

    var refreshInterval: Float
        get() = runBlocking { PreferenceRepository.refreshInterval }
        set(value) {
            runBlocking { PreferenceRepository.refreshInterval = value }
        }

    fun refreshInterval(): Flow<Float> = PreferenceRepository.refreshInterval()

    var showSystemContainers: Boolean
        get() = runBlocking { PreferenceRepository.showSystemContainers }
        set(value) {
            runBlocking { PreferenceRepository.showSystemContainers = value }
        }

    fun showSystemContainers(): Flow<Boolean> = PreferenceRepository.showSystemContainers()

    var confirmBeforeDelete: Boolean
        get() = runBlocking { PreferenceRepository.confirmBeforeDelete }
        set(value) {
            runBlocking { PreferenceRepository.confirmBeforeDelete = value }
        }

    fun confirmBeforeDelete(): Flow<Boolean> = PreferenceRepository.confirmBeforeDelete()
}
