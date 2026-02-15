package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ImageSortColumn {
    REPOSITORY,
    TAG,
    IMAGE_ID,
    SIZE
}

enum class SortDirection {
    ASC,
    DESC
}

class ImagesScreenViewModel : ViewModel() {

    val repo: DockerRepository = AppModule.dockerRepository

    val images: Flow<List<DockerImage>> = repo.getImages()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedImageId = MutableStateFlow<String?>(null)
    val selectedImageId: StateFlow<String?> = _selectedImageId.asStateFlow()

    private val _sortColumn = MutableStateFlow(ImageSortColumn.REPOSITORY)
    val sortColumn: StateFlow<ImageSortColumn> = _sortColumn.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASC)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun removeImage(id: String) {
        viewModelScope.launch {
            try {
                repo.removeImage(id, force = false)
            } catch (e: Exception) {
                _error.value = e.message
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
        _error.value = null
    }

    fun toggleSort(column: ImageSortColumn) {
        if (_sortColumn.value == column) {
            _sortDirection.value =
                if (_sortDirection.value == SortDirection.ASC) SortDirection.DESC
                else SortDirection.ASC
        } else {
            _sortColumn.value = column
            _sortDirection.value = SortDirection.ASC
        }
    }
}
