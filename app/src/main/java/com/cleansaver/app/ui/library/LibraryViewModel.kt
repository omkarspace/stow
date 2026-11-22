package com.cleansaver.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.domain.repository.LibraryRepository
import com.cleansaver.app.ui.state.LibraryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _filterType = MutableStateFlow("ALL")
    private val _searchQuery = MutableStateFlow("")

    init {
        pruneStaleEntries()
    }

    fun pruneStaleEntries() {
        viewModelScope.launch {
            libraryRepository.pruneDeletedEntries()
        }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.observeAllMedia(),
        _filterType,
        _searchQuery
    ) { allItems, filter, query ->
        val trimmedQuery = query.trim().lowercase()
        val filtered = allItems.filter { item ->
            val matchesType = filter == "ALL" || item.mediaType == filter
            val matchesQuery = trimmedQuery.isEmpty() ||
                item.fileName.lowercase().contains(trimmedQuery) ||
                item.shortcode.lowercase().contains(trimmedQuery)
            matchesType && matchesQuery
        }

        LibraryUiState(
            filterType = filter,
            searchQuery = query,
            items = filtered,
            totalCount = allItems.size,
            videoCount = allItems.count { it.mediaType == "VIDEO" },
            imageCount = allItems.count { it.mediaType == "IMAGE" },
            totalSizeBytes = allItems.sumOf { it.fileSizeBytes },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(isLoading = true)
    )

    fun setFilter(type: String) {
        _filterType.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(item: DownloadedMediaEntity) {
        viewModelScope.launch {
            libraryRepository.deleteMedia(item.id, item.mediaStoreUri)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            libraryRepository.clearHistory()
        }
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(
                        libraryRepository = container.libraryRepository
                    ) as T
                }
            }
    }
}
