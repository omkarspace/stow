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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _filterType = MutableStateFlow("ALL")
    private val _sortBy = MutableStateFlow("NEWEST")
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    init {
        pruneStaleEntries()
    }

    fun pruneStaleEntries() {
        viewModelScope.launch {
            libraryRepository.pruneDeletedEntries()
        }
    }

    private data class FilterParams(
        val filter: String,
        val sort: String,
        val query: String,
        val tag: String?,
        val selectedIds: Set<Long>
    )

    private val filterParams = combine(
        _filterType,
        _sortBy,
        _searchQuery,
        _selectedTag,
        _selectedIds
    ) { filter, sort, query, tag, selected ->
        FilterParams(filter, sort, query, tag, selected)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.observeAllMedia(),
        filterParams
    ) { allItems, params ->
        val trimmedQuery = params.query.trim().lowercase()
        val filtered = allItems.filter { item ->
            val matchesType = when (params.filter) {
                "ALL" -> true
                "FAVORITES" -> item.isFavorite
                else -> item.mediaType == params.filter
            }
            val matchesTag = params.tag == null || item.tag.equals(params.tag, ignoreCase = true)
            val matchesQuery = trimmedQuery.isEmpty() ||
                item.fileName.lowercase().contains(trimmedQuery) ||
                item.shortcode.lowercase().contains(trimmedQuery) ||
                item.caption.lowercase().contains(trimmedQuery) ||
                item.creatorHandle.lowercase().contains(trimmedQuery) ||
                item.tag.lowercase().contains(trimmedQuery) ||
                item.platform.lowercase().contains(trimmedQuery)
            matchesType && matchesTag && matchesQuery
        }

        val sorted = when (params.sort) {
            "OLDEST" -> filtered.sortedBy { it.createdAtMs }
            "LARGEST" -> filtered.sortedByDescending { it.fileSizeBytes }
            "CREATOR" -> filtered.sortedBy { it.creatorHandle.lowercase().ifBlank { "zzz" } }
            else -> filtered.sortedByDescending { it.createdAtMs }
        }

        val tags = allItems.map { it.tag }.filter { it.isNotBlank() }.distinct().sorted()

        LibraryUiState(
            filterType = params.filter,
            sortBy = params.sort,
            searchQuery = params.query,
            selectedTag = params.tag,
            availableTags = tags,
            selectedIds = params.selectedIds.filter { id -> allItems.any { it.id == id } }.toSet(),
            items = sorted,
            totalCount = allItems.size,
            videoCount = allItems.count { it.mediaType == "VIDEO" },
            imageCount = allItems.count { it.mediaType == "IMAGE" },
            audioCount = allItems.count { it.mediaType == "AUDIO" },
            favoriteCount = allItems.count { it.isFavorite },
            totalSizeBytes = allItems.sumOf { it.fileSizeBytes },
            videoSizeBytes = allItems.filter { it.mediaType == "VIDEO" }.sumOf { it.fileSizeBytes },
            imageSizeBytes = allItems.filter { it.mediaType == "IMAGE" }.sumOf { it.fileSizeBytes },
            audioSizeBytes = allItems.filter { it.mediaType == "AUDIO" }.sumOf { it.fileSizeBytes },
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

    fun setSortBy(sort: String) {
        _sortBy.value = sort
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTag(tag: String?) {
        _selectedTag.value = if (_selectedTag.value == tag) null else tag
    }

    fun toggleSelect(id: Long) {
        _selectedIds.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun toggleFavorite(item: DownloadedMediaEntity) {
        viewModelScope.launch {
            libraryRepository.setFavorite(item.id, !item.isFavorite)
        }
    }

    fun setTag(item: DownloadedMediaEntity, tag: String) {
        viewModelScope.launch {
            libraryRepository.setTag(item.id, tag)
        }
    }

    fun deleteItem(item: DownloadedMediaEntity) {
        viewModelScope.launch {
            libraryRepository.deleteMedia(item.id, item.mediaStoreUri)
        }
    }

    fun deleteSelected() {
        val selected = uiState.value.items.filter { uiState.value.selectedIds.contains(it.id) }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            libraryRepository.deleteMultiple(selected)
            _selectedIds.value = emptySet()
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
