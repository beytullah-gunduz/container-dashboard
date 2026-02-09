package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.state.ImagesState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImagesScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(ImagesState())
    val state: StateFlow<ImagesState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedImageId = MutableStateFlow<String?>(null)
    val selectedImageId: StateFlow<String?> = _selectedImageId.asStateFlow()

    init {
        loadImages()
    }

    fun autoRefresh(): Flow<Boolean> = PreferenceRepository.autoRefresh()

    fun refreshInterval(): Flow<Float> = PreferenceRepository.refreshInterval()

    fun loadImages() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                AppModule.dockerRepository.getImages()
                    .catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
                    .collect { images ->
                        _state.update { it.copy(images = images, isLoading = false) }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun removeImage(id: String) {
        viewModelScope.launch {
            try {
                AppModule.dockerRepository.removeImage(id, force = false)
                loadImages()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedImage(imageId: String?) {
        _selectedImageId.value = imageId
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
