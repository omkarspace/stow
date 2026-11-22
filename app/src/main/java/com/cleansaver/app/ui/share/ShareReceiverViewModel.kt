package com.cleansaver.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.usecase.DownloadMediaUseCase
import com.cleansaver.app.domain.usecase.ExtractMediaUseCase
import com.cleansaver.app.domain.usecase.ValidateUrlUseCase
import com.cleansaver.app.ui.state.ShareSheetUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShareReceiverViewModel(
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val extractMediaUseCase: ExtractMediaUseCase,
    private val downloadMediaUseCase: DownloadMediaUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareSheetUiState>(ShareSheetUiState.Idle)
    val uiState: StateFlow<ShareSheetUiState> = _uiState.asStateFlow()

    fun handleSharedContent(sharedText: String?) {
        if (sharedText.isNullOrBlank()) {
            _uiState.value = ShareSheetUiState.Error(AppError.InvalidUrl)
            return
        }

        viewModelScope.launch {
            _uiState.value = ShareSheetUiState.Loading

            val validation = validateUrlUseCase(sharedText)
            if (validation.isFailure) {
                val error = (validation.exceptionOrNull() as? CustomThrowable)?.appError ?: AppError.InvalidUrl
                _uiState.value = ShareSheetUiState.Error(error)
                return@launch
            }

            val parsedUrl = validation.getOrThrow()
            val extraction = extractMediaUseCase(parsedUrl)
            if (extraction.isSuccess) {
                val candidates = extraction.getOrThrow()
                _uiState.value = ShareSheetUiState.PreviewReady(candidates)
            } else {
                val error = (extraction.exceptionOrNull() as? CustomThrowable)?.appError
                    ?: AppError.Unknown("Failed to extract media")
                _uiState.value = ShareSheetUiState.Error(error)
            }
        }
    }

    fun startDownload(candidates: List<MediaCandidate>) {
        if (candidates.isEmpty()) return

        viewModelScope.launch {
            val progressMap = candidates.indices.associateWith { DownloadProgress.Queued as DownloadProgress }
            _uiState.value = ShareSheetUiState.Downloading(progressMap)

            downloadMediaUseCase(candidates).collect { currentMap ->
                val allCompleted = currentMap.values.all { it is DownloadProgress.Completed }
                if (allCompleted) {
                    _uiState.value = ShareSheetUiState.Completed(currentMap.size)
                } else {
                    _uiState.value = ShareSheetUiState.Downloading(currentMap)
                }
            }
        }
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ShareReceiverViewModel(
                        validateUrlUseCase = container.validateUrlUseCase,
                        extractMediaUseCase = container.extractMediaUseCase,
                        downloadMediaUseCase = container.downloadMediaUseCase
                    ) as T
                }
            }
    }
}

