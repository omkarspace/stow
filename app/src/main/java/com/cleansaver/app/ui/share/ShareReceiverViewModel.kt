package com.cleansaver.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cleansaver.app.data.engine.DownloadQueueManager
import com.cleansaver.app.data.engine.StowFormatOption
import com.cleansaver.app.data.engine.StowMediaInfo
import com.cleansaver.app.data.engine.YtDlpEngine
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.repository.UserPrefsRepository
import com.cleansaver.app.domain.usecase.DownloadMediaUseCase
import com.cleansaver.app.domain.usecase.ExtractMediaUseCase
import com.cleansaver.app.domain.usecase.ValidateUrlUseCase
import com.cleansaver.app.ui.state.ShareSheetUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

class ShareReceiverViewModel(
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val extractMediaUseCase: ExtractMediaUseCase,
    private val downloadMediaUseCase: DownloadMediaUseCase,
    private val userPrefsRepository: UserPrefsRepository,
    private val ytDlpEngine: YtDlpEngine,
    private val downloadQueueManager: DownloadQueueManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareSheetUiState>(ShareSheetUiState.Idle)
    val uiState: StateFlow<ShareSheetUiState> = _uiState.asStateFlow()

    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun handleSharedContent(sharedText: String?) {
        val url = sharedText?.let { urlRegex.find(it)?.value }
            ?.trimEnd(',', '.', ')', ']', '>', '"', '\'')
        if (url.isNullOrBlank()) {
            _uiState.value = ShareSheetUiState.Error(AppError.InvalidUrl)
            return
        }
        if (url.contains("instagram.com/stories/", ignoreCase = true)) {
            _uiState.value = ShareSheetUiState.Error(AppError.StoriesNotSupported)
            return
        }

        viewModelScope.launch {
            _uiState.value = ShareSheetUiState.Loading
            val instantSave = userPrefsRepository.instantShareSave.first()

            val ytResult = ytDlpEngine.fetchMediaInfo(url)
            if (ytResult.isSuccess) {
                val info = ytResult.getOrThrow()
                if (instantSave) {
                    val best = info.videoFormats.firstOrNull() ?: info.audioFormats.first()
                    downloadQueueManager.enqueue(mediaInfo = info, format = best)
                    _uiState.value = ShareSheetUiState.Enqueued(info.title)
                } else {
                    _uiState.value = ShareSheetUiState.FormatPickerReady(info)
                }
                return@launch
            }

            // Fallback to legacy extractor (for Instagram photos / carousels)
            val validation = validateUrlUseCase(url)
            if (validation.isSuccess) {
                val extraction = extractMediaUseCase(validation.getOrThrow())
                if (extraction.isSuccess) {
                    val candidates = extraction.getOrThrow()
                    if (instantSave) {
                        startDownload(candidates)
                    } else {
                        _uiState.value = ShareSheetUiState.PreviewReady(candidates)
                    }
                    return@launch
                }
            }

            val error = (ytResult.exceptionOrNull() as? CustomThrowable)?.appError
                ?: AppError.Unknown(ytResult.exceptionOrNull()?.message ?: "Failed to extract media")
            _uiState.value = ShareSheetUiState.Error(error)
        }
    }

    fun enqueueFormat(
        mediaInfo: StowMediaInfo,
        format: StowFormatOption,
        embedSubtitles: Boolean,
        embedThumbnail: Boolean
    ) {
        downloadQueueManager.enqueue(
            mediaInfo = mediaInfo,
            format = format,
            embedSubtitles = embedSubtitles,
            embedThumbnail = embedThumbnail
        )
        _uiState.value = ShareSheetUiState.Enqueued(mediaInfo.title)
    }

    fun startDownload(candidates: List<MediaCandidate>) {
        if (candidates.isEmpty()) return

        viewModelScope.launch {
            val initialMap = candidates.associate { it.slideIndex to (DownloadProgress.Queued as DownloadProgress) }
            _uiState.value = ShareSheetUiState.Downloading(initialMap)

            var finished = false
            downloadMediaUseCase(candidates)
                .takeWhile { !finished }
                .collect { currentMap ->
                    val allCompleted = currentMap.isNotEmpty() && currentMap.values.all { it is DownloadProgress.Completed }
                    val firstFailure = currentMap.values.filterIsInstance<DownloadProgress.Failed>().firstOrNull()
                    val anyRunning = currentMap.values.any { it is DownloadProgress.Queued || it is DownloadProgress.Running }

                    when {
                        allCompleted -> {
                            finished = true
                            _uiState.value = ShareSheetUiState.Completed(currentMap.size)
                        }
                        !anyRunning && firstFailure != null -> {
                            finished = true
                            _uiState.value = ShareSheetUiState.Error(firstFailure.error)
                        }
                        else -> {
                            _uiState.value = ShareSheetUiState.Downloading(currentMap)
                        }
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
                        downloadMediaUseCase = container.downloadMediaUseCase,
                        userPrefsRepository = container.userPrefsRepository,
                        ytDlpEngine = container.ytDlpEngine,
                        downloadQueueManager = container.downloadQueueManager
                    ) as T
                }
            }
    }
}
