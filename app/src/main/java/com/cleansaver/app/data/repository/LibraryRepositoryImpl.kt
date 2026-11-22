package com.cleansaver.app.data.repository

import android.content.Context
import android.net.Uri
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.domain.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LibraryRepositoryImpl(
    private val context: Context,
    private val downloadedMediaDao: DownloadedMediaDao
) : LibraryRepository {

    override fun observeAllMedia(): Flow<List<DownloadedMediaEntity>> {
        return downloadedMediaDao.observeAll()
    }

    override fun observeMediaByType(type: String): Flow<List<DownloadedMediaEntity>> {
        return downloadedMediaDao.observeByType(type)
    }

    override suspend fun deleteMedia(id: Long, mediaStoreUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (mediaStoreUri.isNotBlank()) {
                try {
                    context.contentResolver.delete(Uri.parse(mediaStoreUri), null, null)
                } catch (_: Exception) {}
            }
            downloadedMediaDao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            downloadedMediaDao.clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

