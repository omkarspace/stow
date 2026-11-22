package com.cleansaver.app.ui.state

import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate

data class HomeUiState(
    val inputUrl: String = "",
    val isValidating: Boolean = false,
    val isExtracting: Boolean = false,
    val extractedCandidates: List<MediaCandidate> = emptyList(),
    val selectedIndices: Set<Int> = emptySet(),
    val downloadProgressMap: Map<Int, DownloadProgress> = emptyMap(),
    val showDuplicatePrompt: Boolean = false,
    val error: AppError? = null
)

sealed interface ShareSheetUiState {
    data object Idle : ShareSheetUiState
    data object Loading : ShareSheetUiState
    data class PreviewReady(val candidates: List<MediaCandidate>) : ShareSheetUiState
    data class Downloading(val progressMap: Map<Int, DownloadProgress>) : ShareSheetUiState
    data class Completed(val savedCount: Int) : ShareSheetUiState
    data class Error(val appError: AppError) : ShareSheetUiState
}

data class LibraryUiState(
    val filterType: String = "ALL", // "ALL", "VIDEO", "IMAGE"
    val items: List<DownloadedMediaEntity> = emptyList(),
    val isLoading: Boolean = false
)

data class SettingsUiState(
    val themeMode: String = "SYSTEM",
    val clipboardAutoDetect: Boolean = false,
    val fairUseAccepted: Boolean = false,
    val isRefreshingConfig: Boolean = false,
    val configRefreshMessage: String? = null,
    val appVersion: String = "1.0.0"
)
