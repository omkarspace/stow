package com.cleansaver.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
                    extractedCandidates = emptyList(),
                    selectedIndices = emptySet(),
                    downloadProgressMap = emptyMap(),
                    showDuplicatePrompt = false
                )
            }

            // Support batch paste (multiple URLs separated by newlines or spaces)
            val urls = validateUrlUseCase.extractAllUrls(rawInput).ifEmpty { listOf(rawInput) }
            val parsedUrls = mutableListOf<com.cleansaver.app.domain.model.ParsedUrl>()
            var firstError: AppError? = null

            for (url in urls) {
                val validation = validateUrlUseCase(url)
                if (validation.isSuccess) {
                    parsedUrls.add(validation.getOrThrow())
                } else if (firstError == null) {
                    firstError = (validation.exceptionOrNull() as? CustomThrowable)?.appError ?: AppError.InvalidUrl
                }
            }

            if (parsedUrls.isEmpty()) {
                _uiState.update { it.copy(isValidating = false, error = firstError ?: AppError.InvalidUrl) }
                return@launch
            }

            _uiState.update { it.copy(isValidating = false, isExtracting = true) }

            val allCandidates = mutableListOf<MediaCandidate>()
            var lastExtractError: AppError? = null
            var globalSlideIndex = 0

            parsedUrls.forEachIndexed { idx, parsed ->
                if (parsedUrls.size > 1) {
                    _uiState.update {
                        it.copy(extractionStatus = "Fetching link ${idx + 1} of ${parsedUrls.size}...")
                    }
                    if (idx > 0) delay(1200L) // Polite rate-limit spacing for batch queue
                }

                val result = extractMediaUseCase(parsed)
                if (result.isSuccess) {
                    result.getOrThrow().forEach { candidate ->
                        allCandidates.add(candidate.copy(slideIndex = globalSlideIndex++))
                    }
                } else {
                    lastExtractError = (result.exceptionOrNull() as? CustomThrowable)?.appError
                        ?: AppError.Unknown(result.exceptionOrNull()?.message ?: "Extraction failed")
                }
            }

            if (allCandidates.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractionStatus = null,
                        extractedCandidates = allCandidates,
                        selectedIndices = allCandidates.indices.toSet(),
                        downloadProgressMap = emptyMap()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractionStatus = null,
                        error = lastExtractError ?: AppError.ParserOutdated
                    )
                }
            }
        }
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
        val audioCandidate = candidate.copy(
            mediaType = MediaType.AUDIO,
            slideIndex = candidate.slideIndex
        )
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
                        mediaRepository = container.mediaRepository
                    ) as T
                }
            }
    }
}
