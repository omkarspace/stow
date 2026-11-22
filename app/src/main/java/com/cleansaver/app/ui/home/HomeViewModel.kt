package com.cleansaver.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.repository.MediaRepository
import com.cleansaver.app.domain.usecase.DownloadMediaUseCase
import com.cleansaver.app.domain.usecase.ExtractMediaUseCase
import com.cleansaver.app.domain.usecase.ValidateUrlUseCase
import com.cleansaver.app.ui.state.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val extractMediaUseCase: ExtractMediaUseCase,
    private val downloadMediaUseCase: DownloadMediaUseCase,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(inputUrl = newUrl, error = null) }
    }

    fun onPaste(text: String) {
        onUrlChanged(text)
        if (text.isNotBlank()) {
            onExtract()
        }
    }

    fun onExtract() {
        val currentUrl = _uiState.value.inputUrl.trim()
        if (currentUrl.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isValidating = true,
                    error = null,
                    extractedCandidates = emptyList(),
                    selectedIndices = emptySet(),
                    downloadProgressMap = emptyMap(),
                    showDuplicatePrompt = false
                )
            }

            val validationResult = validateUrlUseCase(currentUrl)
            if (validationResult.isFailure) {
                val error = (validationResult.exceptionOrNull() as? CustomThrowable)?.appError ?: AppError.InvalidUrl
                _uiState.update { it.copy(isValidating = false, error = error) }
                return@launch
            }

            val parsedUrl = validationResult.getOrThrow()
            _uiState.update { it.copy(isValidating = false, isExtracting = true) }

            val extractionResult = extractMediaUseCase(parsedUrl)
            if (extractionResult.isSuccess) {
                val candidates = extractionResult.getOrThrow()
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractedCandidates = candidates,
                        selectedIndices = candidates.indices.toSet(),
                        downloadProgressMap = candidates.indices.associateWith { DownloadProgress.Queued }
                    )
                }
            } else {
                val error = (extractionResult.exceptionOrNull() as? CustomThrowable)?.appError
                    ?: AppError.Unknown(extractionResult.exceptionOrNull()?.message ?: "Extraction failed")
                _uiState.update { it.copy(isExtracting = false, error = error) }
            }
        }
    }

    fun toggleCandidateSelection(index: Int) {
        _uiState.update { state ->
            val updated = state.selectedIndices.toMutableSet()
            if (updated.contains(index)) {
                updated.remove(index)
            } else {
                updated.add(index)
            }
            state.copy(selectedIndices = updated)
        }
    }

    fun selectAllCandidates() {
        _uiState.update { state ->
            state.copy(selectedIndices = state.extractedCandidates.indices.toSet())
        }
    }

    fun deselectAllCandidates() {
        _uiState.update { state ->
            state.copy(selectedIndices = emptySet())
        }
    }

    fun onDownload(force: Boolean = false) {
        val candidates = _uiState.value.extractedCandidates
        val selected = _uiState.value.selectedIndices
        val candidatesToDownload = candidates.filterIndexed { index, _ -> selected.contains(index) }
        if (candidatesToDownload.isEmpty()) return

        viewModelScope.launch {
            if (!force) {
                val anyDuplicate = candidatesToDownload.any { mediaRepository.isMediaAlreadyDownloaded(it.shortcode) }
                if (anyDuplicate) {
                    _uiState.update { it.copy(showDuplicatePrompt = true) }
                    return@launch
                }
            }

            _uiState.update { it.copy(showDuplicatePrompt = false) }
            downloadMediaUseCase(candidatesToDownload).collect { progressMap ->
                _uiState.update { it.copy(downloadProgressMap = progressMap) }
            }
        }
    }

    fun dismissDuplicatePrompt() {
        _uiState.update { it.copy(showDuplicatePrompt = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        validateUrlUseCase = container.validateUrlUseCase,
                        extractMediaUseCase = container.extractMediaUseCase,
                        downloadMediaUseCase = container.downloadMediaUseCase,
                        mediaRepository = container.mediaRepository
                    ) as T
                }
            }
    }
}
