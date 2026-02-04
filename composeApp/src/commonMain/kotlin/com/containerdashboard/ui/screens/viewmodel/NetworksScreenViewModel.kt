package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.state.NetworksState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NetworksScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(NetworksState())
    val state: StateFlow<NetworksState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedNetworkId = MutableStateFlow<String?>(null)
    val selectedNetworkId: StateFlow<String?> = _selectedNetworkId.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    init {
        loadNetworks()
    }

    fun loadNetworks() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                AppModule.dockerRepository.getNetworks()
                    .catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
                    .collect { networks ->
                        _state.update { it.copy(networks = networks, isLoading = false) }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun createNetwork(name: String, driver: String) {
        viewModelScope.launch {
            try {
                AppModule.dockerRepository.createNetwork(name, driver)
                loadNetworks()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun removeNetwork(id: String) {
        viewModelScope.launch {
            try {
                AppModule.dockerRepository.removeNetwork(id)
                loadNetworks()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedNetwork(networkId: String?) {
        _selectedNetworkId.value = networkId
    }

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
