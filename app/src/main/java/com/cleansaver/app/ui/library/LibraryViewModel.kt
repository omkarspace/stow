package com.cleansaver.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.domain.repository.LibraryRepository
import com.cleansaver.app.ui.state.LibraryUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _filterType = MutableStateFlow("ALL")

    val uiState: StateFlow<LibraryUiState> = _filterType.flatMapLatest { filter ->
        if (filter == "ALL") {
            libraryRepository.observeAllMedia()
        } else {
            libraryRepository.observeMediaByType(filter)
        }
    }.combine(_filterType) { items, filter ->
        LibraryUiState(
            filterType = filter,
            items = items,
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

