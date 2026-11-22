package com.cleansaver.app.domain.model

sealed interface DownloadProgress {
    data object Queued : DownloadProgress
    data class Running(val bytesRead: Long, val totalBytes: Long, val percent: Int) : DownloadProgress
    data class Completed(val mediaStoreUri: String, val fileName: String) : DownloadProgress
    data class Failed(val error: AppError) : DownloadProgress
    data object Cancelled : DownloadProgress
}

