package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.DockerNetwork
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworksScreenViewModel(
    repoProvider: () -> DockerRepository = { AppModule.dockerRepository },
) : ListScreenViewModel<DockerNetwork>(
        repoProvider = repoProvider,
        items = repoProvider().getNetworks(),
    ) {
    val networks: Flow<List<DockerNetwork>> get() = items

    val checkedNetworkIds: StateFlow<Set<String>> get() = checkedKeys
    val selectedNetworkId: StateFlow<String?> get() = selectedKey

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun setSelectedNetwork(networkId: String?) = setSelectedKey(networkId)

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    fun deleteSelectedNetworks() = deleteSelected("network") { repo.removeNetwork(it) }

    fun createNetwork(
        name: String,
        driver: String,
    ) {
        viewModelScope.launch {
            repo.createNetwork(name, driver).onFailure { setError(it.message) }
        }
    }

    fun removeNetwork(id: String) {
        viewModelScope.launch {
            repo.removeNetwork(id).onFailure { setError(it.message) }
        }
    }

    override suspend fun refreshItems() = repo.refreshNetworks()
}
