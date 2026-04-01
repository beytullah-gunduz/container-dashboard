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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NetworksScreenViewModel : ViewModel() {
    private val repo: DockerRepository get() = AppModule.dockerRepository

    val networks: Flow<List<DockerNetwork>> = repo.getNetworks()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedNetworkId = MutableStateFlow<String?>(null)
    val selectedNetworkId: StateFlow<String?> = _selectedNetworkId.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _checkedNetworkIds = MutableStateFlow(setOf<String>())
    val checkedNetworkIds: StateFlow<Set<String>> = _checkedNetworkIds.asStateFlow()

    private val _isDeletingSelected = MutableStateFlow(false)
    val isDeletingSelected: StateFlow<Boolean> = _isDeletingSelected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggleChecked(
        id: String,
        checked: Boolean,
    ) {
        _checkedNetworkIds.update { if (checked) it + id else it - id }
    }

    fun checkAll(ids: List<String>) {
        _checkedNetworkIds.value = ids.toSet()
    }

    fun uncheckAll(ids: List<String>) {
        _checkedNetworkIds.update { it - ids.toSet() }
    }

    fun clearChecked() {
        _checkedNetworkIds.value = emptySet()
    }

    fun deleteSelectedNetworks() {
        viewModelScope.launch {
            _isDeletingSelected.value = true
            val ids = _checkedNetworkIds.value.toList()
            val errors = mutableListOf<String>()
            for (id in ids) {
                repo.removeNetwork(id).onFailure {
                    errors.add(it.message ?: "Failed to delete network")
                }
            }
            _checkedNetworkIds.value = emptySet()
            _isDeletingSelected.value = false
            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} network(s)"
            }
        }
    }

    fun createNetwork(
        name: String,
        driver: String,
    ) {
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
