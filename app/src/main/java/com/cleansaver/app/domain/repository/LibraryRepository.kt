package com.cleansaver.app.domain.repository

import com.cleansaver.app.data.local.DownloadedMediaEntity
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeAllMedia(): Flow<List<DownloadedMediaEntity>>
    fun observeMediaByType(type: String): Flow<List<DownloadedMediaEntity>>
    suspend fun deleteMedia(id: Long, mediaStoreUri: String): Result<Unit>
    suspend fun clearHistory(): Result<Unit>
    suspend fun pruneDeletedEntries()
}
