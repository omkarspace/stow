package com.cleansaver.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cleansaver.app.data.engine.DownloadQueueManager
import com.cleansaver.app.data.engine.QueueTask
import com.cleansaver.app.data.engine.StowFormatOption
import com.cleansaver.app.data.engine.StowMediaInfo
import com.cleansaver.app.data.engine.YtDlpEngine
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.repository.MediaRepository
import com.cleansaver.app.domain.usecase.DownloadMediaUseCase
import com.cleansaver.app.domain.usecase.ExtractMediaUseCase
import com.cleansaver.app.domain.usecase.ValidateUrlUseCase
import com.cleansaver.app.ui.state.HomeUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val extractMediaUseCase: ExtractMediaUseCase,
    private val downloadMediaUseCase: DownloadMediaUseCase,
    private val mediaRepository: MediaRepository,
    private val ytDlpEngine: YtDlpEngine,
    private val downloadQueueManager: DownloadQueueManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val queueTasks: StateFlow<List<QueueTask>> = downloadQueueManager.tasks

    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(inputUrl = newUrl, error = null, actionBannerMessage = null) }
    }

    fun onPaste(text: String) {
        onUrlChanged(text)
        if (text.isNotBlank()) {
            onExtract()
        }
    }

    fun onExtract() {
        val rawInput = _uiState.value.inputUrl.trim()
        if (rawInput.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isValidating = true,
                    isDownloading = false,
                    error = null,
                    actionBannerMessage = null,
                    extractionStatus = null,
                    pendingMediaInfo = null,
                    extractedCandidates = emptyList(),
                    selectedIndices = emptySet(),
                    downloadProgressMap = emptyMap(),
                    showDuplicatePrompt = false
                )
            }

            val urls = urlRegex.findAll(rawInput)
                .map { it.value.trimEnd(',', '.', ')', ']', '>', '"', '\'') }
                .distinct()
                .toList()

            if (urls.isEmpty()) {
                _uiState.update { it.copy(isValidating = false, error = AppError.InvalidUrl) }
                return@launch
            }

            if (urls.any { it.contains("instagram.com/stories/", ignoreCase = true) }) {
                _uiState.update { it.copy(isValidating = false, error = AppError.StoriesNotSupported) }
                return@launch
            }

            _uiState.update { it.copy(isValidating = false, isExtracting = true) }

            // Single link -> fetch formats via yt-dlp and open FormatPickerBottomSheet
            if (urls.size == 1) {
                val url = urls.first()
                _uiState.update { it.copy(extractionStatus = "Fetching available formats...") }
                val infoResult = ytDlpEngine.fetchMediaInfo(url)

                if (infoResult.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            extractionStatus = null,
                            pendingMediaInfo = infoResult.getOrThrow()
                        )
                    }
                    return@launch
                }

                // Fallback to legacy extractor chain (handles Instagram photo-only posts / carousels)
                val parsed = validateUrlUseCase(url).getOrNull()
                if (parsed != null) {
                    _uiState.update { it.copy(extractionStatus = "Checking photo/carousel extractor...") }
                    val fallbackResult = extractMediaUseCase(parsed)
                    if (fallbackResult.isSuccess) {
                        val candidates = fallbackResult.getOrThrow()
                        if (candidates.isNotEmpty()) {
                            _uiState.update {
                                it.copy(
                                    isExtracting = false,
                                    extractionStatus = null,
                                    extractedCandidates = candidates,
                                    selectedIndices = candidates.indices.toSet()
                                )
                            }
                            return@launch
                        }
                    }
                }

                val err = (infoResult.exceptionOrNull() as? CustomThrowable)?.appError
                    ?: AppError.Unknown(infoResult.exceptionOrNull()?.message ?: "Unable to fetch media")
                _uiState.update {
                    it.copy(isExtracting = false, extractionStatus = null, error = err)
                }
                return@launch
            }

            // Batch paste (multiple URLs) -> resolve each via yt-dlp & enqueue best MP4 automatically
            var queuedCount = 0
            var lastError: AppError? = null
            urls.forEachIndexed { idx, url ->
                _uiState.update { it.copy(extractionStatus = "Queuing ${idx + 1} of ${urls.size}...") }
                if (idx > 0) delay(900L)
                val infoResult = ytDlpEngine.fetchMediaInfo(url)
                if (infoResult.isSuccess) {
                    val info = infoResult.getOrThrow()
                    val bestFormat = info.videoFormats.firstOrNull() ?: info.audioFormats.first()
                    downloadQueueManager.enqueue(mediaInfo = info, format = bestFormat)
                    queuedCount++
                } else {
                    lastError = (infoResult.exceptionOrNull() as? CustomThrowable)?.appError
                        ?: AppError.Unknown(infoResult.exceptionOrNull()?.message ?: "Failed to queue link")
                }
            }

            if (queuedCount > 0) {
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractionStatus = null,
                        inputUrl = "",
                        actionBannerMessage = "Queued $queuedCount download${if (queuedCount > 1) "s" else ""}"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractionStatus = null,
                        error = lastError ?: AppError.ParserOutdated
                    )
                }
            }
        }
    }

    fun enqueueDownload(
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
        _uiState.update {
            it.copy(
                pendingMediaInfo = null,
                inputUrl = "",
                actionBannerMessage = "Downloading ${mediaInfo.title.take(36)}"
            )
        }
    }

    fun dismissFormatSheet() {
        _uiState.update { it.copy(pendingMediaInfo = null) }
    }

    fun cancelQueueTask(taskId: String) {
        downloadQueueManager.cancel(taskId)
    }

    fun retryQueueTask(taskId: String) {
        downloadQueueManager.retry(taskId)
    }

    fun removeQueueTask(taskId: String) {
        downloadQueueManager.remove(taskId)
    }

    fun clearCompletedQueue() {
        downloadQueueManager.clearCompleted()
    }

    fun toggleCandidateSelection(index: Int) {
        _uiState.update { state ->
            val updated = state.selectedIndices.toMutableSet()
            if (updated.contains(index)) updated.remove(index) else updated.add(index)
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
                val anyDuplicate = candidatesToDownload.any {
                    mediaRepository.isMediaAlreadyDownloaded(it.shortcode, it.slideIndex)
                }
                if (anyDuplicate) {
                    _uiState.update { it.copy(showDuplicatePrompt = true) }
                    return@launch
                }
            }

            _uiState.update { it.copy(showDuplicatePrompt = false, isDownloading = true, actionBannerMessage = null) }
            collectDownloadFlow(candidatesToDownload)
        }
    }

    fun onDownloadAudio(candidate: MediaCandidate) {
        if (candidate.mediaType != MediaType.VIDEO) return
        val audioCandidate = candidate.copy(mediaType = MediaType.AUDIO)
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, actionBannerMessage = "Extracting audio (.m4a)...") }
            collectDownloadFlow(listOf(audioCandidate))
            _uiState.update { it.copy(actionBannerMessage = "Audio saved to Music folder") }
        }
    }

    fun onDownloadCover(candidate: MediaCandidate) {
        val thumbUrl = candidate.thumbnailUrl ?: return
        val coverCandidate = candidate.copy(
            mediaType = MediaType.IMAGE,
            downloadUrl = thumbUrl,
            slideIndex = candidate.slideIndex + 1000
        )
        viewModelScope.launch {
            _uiState.update { it.copy(actionBannerMessage = "Saving cover image...") }
            collectDownloadFlow(listOf(coverCandidate))
            _uiState.update { it.copy(actionBannerMessage = "Cover image saved to Gallery") }
        }
    }

    private suspend fun collectDownloadFlow(targets: List<MediaCandidate>) {
        var finished = false
        downloadMediaUseCase(targets)
            .takeWhile { !finished }
            .collect { progressBySlide ->
                val isStillRunning = progressBySlide.values.any {
                    it is DownloadProgress.Queued || it is DownloadProgress.Running
                }
                if (!isStillRunning && progressBySlide.isNotEmpty()) {
                    finished = true
                }
                _uiState.update { state ->
                    state.copy(
                        isDownloading = isStillRunning,
                        downloadProgressMap = state.downloadProgressMap + progressBySlide
                    )
                }
            }
        _uiState.update { it.copy(isDownloading = false) }
    }

    fun dismissDuplicatePrompt() {
        _uiState.update { it.copy(showDuplicatePrompt = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(actionBannerMessage = null) }
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
                        mediaRepository = container.mediaRepository,
                        ytDlpEngine = container.ytDlpEngine,
                        downloadQueueManager = container.downloadQueueManager
                    ) as T
                }
            }
    }
}
