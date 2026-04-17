package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImageSortColumn {
    REPOSITORY,
    TAG,
    IMAGE_ID,
    SIZE,
}

enum class SortDirection {
    ASC,
    DESC,
}

class ImagesScreenViewModel : ViewModel() {
    private val repo: DockerRepository get() = AppModule.dockerRepository

    val images: Flow<List<DockerImage>> = repo.getImages()

    /** Emits `false` until the first list of images has been delivered. */
    val hasLoaded: StateFlow<Boolean> =
        images
            .map { true }
            .onStart { emit(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedImageId = MutableStateFlow<String?>(null)
    val selectedImageId: StateFlow<String?> = _selectedImageId.asStateFlow()

    private val _sortColumn = MutableStateFlow(ImageSortColumn.REPOSITORY)
    val sortColumn: StateFlow<ImageSortColumn> = _sortColumn.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASC)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _checkedImageIds = MutableStateFlow(setOf<String>())
    val checkedImageIds: StateFlow<Set<String>> = _checkedImageIds.asStateFlow()

    private val _isDeletingSelected = MutableStateFlow(false)
    val isDeletingSelected: StateFlow<Boolean> = _isDeletingSelected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggleChecked(
        id: String,
        checked: Boolean,
    ) {
        _checkedImageIds.update { if (checked) it + id else it - id }
    }

    fun checkAll(ids: List<String>) {
        _checkedImageIds.value = ids.toSet()
    }

    fun uncheckAll(ids: List<String>) {
        _checkedImageIds.update { it - ids.toSet() }
    }

    fun clearChecked() {
        _checkedImageIds.value = emptySet()
    }

    fun removeImage(id: String) {
        viewModelScope.launch {
            repo.removeImage(id, force = false).onFailure { _error.value = it.message }
        }
    }

    fun deleteSelectedImages() {
        viewModelScope.launch {
            _isDeletingSelected.value = true
            val ids = _checkedImageIds.value.toList()
            val errors = mutableListOf<String>()
            for (id in ids) {
                repo.removeImage(id, force = false).onFailure {
                    errors.add(it.message ?: "Failed to delete image")
                }
            }
            _checkedImageIds.value = emptySet()
            _isDeletingSelected.value = false
            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} image(s)"
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

    /**
     * Trigger a best-effort refresh. The underlying shared flow updates
     * whenever any container mutation happens; explicitly refreshing
     * containers nudges that pipeline and re-runs any waiting fallbacks.
     */
    fun refresh() {
        viewModelScope.launch { repo.refreshContainers() }
    }

    fun toggleSort(column: ImageSortColumn) {
        if (_sortColumn.value == column) {
            _sortDirection.value =
                if (_sortDirection.value == SortDirection.ASC) {
                    SortDirection.DESC
                } else {
                    SortDirection.ASC
                }
        } else {
            _sortColumn.value = column
            _sortDirection.value = SortDirection.ASC
        }
    }
}
