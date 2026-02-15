package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.DockerNetwork
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworksScreenViewModel : ViewModel() {

    val repo: DockerRepository = AppModule.dockerRepository

    val networks: Flow<List<DockerNetwork>> = repo.getNetworks()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedNetworkId = MutableStateFlow<String?>(null)
    val selectedNetworkId: StateFlow<String?> = _selectedNetworkId.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun createNetwork(name: String, driver: String) {
        viewModelScope.launch {
            try {
                repo.createNetwork(name, driver)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun removeNetwork(id: String) {
        viewModelScope.launch {
            try {
                repo.removeNetwork(id)
            } catch (e: Exception) {
                _error.value = e.message
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
        _error.value = null
    }
}
