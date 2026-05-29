package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ImageSortColumn {
    REPOSITORY,
    TAG,
    IMAGE_ID,
    SIZE,
}

class ImagesScreenViewModel(
    repoProvider: () -> DockerRepository = { AppModule.dockerRepository },
) : SortableListScreenViewModel<DockerImage, ImageSortColumn>(
        repoProvider = repoProvider,
        items = repoProvider().getImages(),
        initialColumn = ImageSortColumn.REPOSITORY,
    ) {
    val images: Flow<List<DockerImage>> get() = items

    val checkedImageIds: StateFlow<Set<String>> get() = checkedKeys
    val selectedImageId: StateFlow<String?> get() = selectedKey

    fun setSelectedImage(imageId: String?) = setSelectedKey(imageId)

    fun removeImage(id: String) {
        viewModelScope.launch {
            repo.removeImage(id, force = false).onFailure { setError(it.message) }
        }
    }

    fun deleteSelectedImages() = deleteSelected("image") { repo.removeImage(it, force = false) }

    override suspend fun refreshItems() = repo.refreshImages()
}
