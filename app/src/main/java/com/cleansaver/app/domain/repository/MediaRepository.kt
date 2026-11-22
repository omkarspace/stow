package com.cleansaver.app.domain.repository

import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun extractMedia(parsedUrl: ParsedUrl): Result<List<MediaCandidate>>
    fun downloadCandidates(candidates: List<MediaCandidate>): Flow<Map<Int, DownloadProgress>>
    suspend fun isMediaAlreadyDownloaded(shortcode: String): Boolean
}
