package com.cleansaver.app.ui.state

import com.cleansaver.app.data.engine.StowMediaInfo
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.util.ReleaseUpdateInfo

data class HomeUiState(
    val inputUrl: String = "",
    val isValidating: Boolean = false,
    val isExtracting: Boolean = false,
    val extractionStatus: String? = null,
    val isDownloading: Boolean = false,
    val pendingMediaInfo: StowMediaInfo? = null,
    val extractedCandidates: List<MediaCandidate> = emptyList(),
    val selectedIndices: Set<Int> = emptySet(),
    val downloadProgressMap: Map<Int, DownloadProgress> = emptyMap(),
    val actionBannerMessage: String? = null,
    val showDuplicatePrompt: Boolean = false,
    val error: AppError? = null
)

sealed interface ShareSheetUiState {
    data object Idle : ShareSheetUiState
    data object Loading : ShareSheetUiState
    data class FormatPickerReady(val mediaInfo: StowMediaInfo) : ShareSheetUiState
    data class Enqueued(val title: String) : ShareSheetUiState
    data class PreviewReady(val candidates: List<MediaCandidate>) : ShareSheetUiState
    data class Downloading(val progressMap: Map<Int, DownloadProgress>) : ShareSheetUiState
    data class Completed(val savedCount: Int) : ShareSheetUiState
    data class Error(val appError: AppError) : ShareSheetUiState
}

data class LibraryUiState(
    val filterType: String = "ALL", // "ALL", "VIDEO", "IMAGE", "AUDIO", "FAVORITES"
    val sortBy: String = "NEWEST", // "NEWEST", "OLDEST", "LARGEST", "CREATOR"
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val items: List<DownloadedMediaEntity> = emptyList(),
    val totalCount: Int = 0,
    val videoCount: Int = 0,
    val imageCount: Int = 0,
    val audioCount: Int = 0,
    val favoriteCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val videoSizeBytes: Long = 0L,
    val imageSizeBytes: Long = 0L,
    val audioSizeBytes: Long = 0L,
    val isLoading: Boolean = false
)

data class SettingsUiState(
    val themeMode: String = "SYSTEM",
    val clipboardAutoDetect: Boolean = false,
    val fairUseAccepted: Boolean = false,
    val instantShareSave: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val vaultLockEnabled: Boolean = false,
    val qualityPreference: String = "BEST",
    val fileNamingPattern: String = "DEFAULT",
    val customAlbumName: String = "Stow",
    val isLoaded: Boolean = false,
    val isRefreshingConfig: Boolean = false,
    val configRefreshMessage: String? = null,
    val configRefreshSuccess: Boolean = true,
    val isCheckingUpdate: Boolean = false,
    val updateInfo: ReleaseUpdateInfo? = null,
    val updateCheckMessage: String? = null,
    val isUpdatingYtDlp: Boolean = false,
    val ytDlpUpdateMessage: String? = null,
    val ytDlpUpdateSuccess: Boolean = true,
    val diagnosticLogs: List<String> = emptyList(),
    val appVersion: String = "1.0.0"
)
