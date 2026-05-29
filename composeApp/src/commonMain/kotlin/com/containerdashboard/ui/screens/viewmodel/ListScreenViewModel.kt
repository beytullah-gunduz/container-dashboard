package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.repository.DockerRepository
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

enum class SortDirection {
    ASC,
    DESC,
}

/**
 * Base ViewModel for list screens that share checked-set, search, selection,
 * deletion, and refresh logic.
 *
 * [items] must be supplied as a constructor argument (not an abstract property)
 * to avoid the init-order pitfall where [hasLoaded] would reference an
 * uninitialized abstract member during base-class construction.
 */
abstract class ListScreenViewModel<T>(
    protected val repoProvider: () -> DockerRepository,
    protected val items: Flow<List<T>>,
) : ViewModel() {
    protected val repo: DockerRepository get() = repoProvider()

    /** Emits `false` until the first list of items has been delivered. */
    val hasLoaded: StateFlow<Boolean> =
        items
            .map { true }
            .onStart { emit(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _checkedKeys = MutableStateFlow(emptySet<String>())
    val checkedKeys: StateFlow<Set<String>> = _checkedKeys.asStateFlow()

    fun toggleChecked(
        key: String,
        checked: Boolean,
    ) {
        _checkedKeys.update { if (checked) it + key else it - key }
    }

    fun checkAll(keys: List<String>) {
        _checkedKeys.value = keys.toSet()
    }

    fun uncheckAll(keys: List<String>) {
        _checkedKeys.update { it - keys.toSet() }
    }

    fun clearChecked() {
        _checkedKeys.value = emptySet()
    }

    private val _selectedKey = MutableStateFlow<String?>(null)
    val selectedKey: StateFlow<String?> = _selectedKey.asStateFlow()

    protected fun setSelectedKey(key: String?) {
        _selectedKey.value = key
    }

    private val _isDeletingSelected = MutableStateFlow(false)
    val isDeletingSelected: StateFlow<Boolean> = _isDeletingSelected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    protected fun setError(msg: String?) {
        _error.value = msg
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Deletes every currently-checked key by calling [delete] for each one.
     * Always clears the checked set and resets [isDeletingSelected] when done.
     * Sets [error] if any individual deletion failed.
     */
    protected fun deleteSelected(
        resourceLabel: String,
        delete: suspend (String) -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _isDeletingSelected.value = true
            val keys = _checkedKeys.value.toList()
            val errors = mutableListOf<String>()
            for (k in keys) {
                delete(k).onFailure { errors.add(it.message ?: "Failed to delete $resourceLabel") }
            }
            _checkedKeys.value = emptySet()
            _isDeletingSelected.value = false
            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} $resourceLabel(s)"
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshItems() }
    }

    protected abstract suspend fun refreshItems()
}

/**
 * Extension of [ListScreenViewModel] for screens that also expose sortable
 * columns. Images, Volumes, and Networks use this.
 */
abstract class SortableListScreenViewModel<T, C : Enum<C>>(
    repoProvider: () -> DockerRepository,
    items: Flow<List<T>>,
    initialColumn: C,
) : ListScreenViewModel<T>(repoProvider, items) {
    private val _sortColumn = MutableStateFlow(initialColumn)
    val sortColumn: StateFlow<C> = _sortColumn.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASC)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    fun toggleSort(column: C) {
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
