package com.cleansaver.app.data.repository

import android.content.Context
import android.net.Uri
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.domain.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    override suspend fun deleteMultiple(items: List<DownloadedMediaEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            items.forEach { item ->
                if (item.mediaStoreUri.isNotBlank()) {
                    try {
                        context.contentResolver.delete(Uri.parse(item.mediaStoreUri), null, null)
                    } catch (_: Exception) {}
                }
            }
            downloadedMediaDao.deleteByIds(items.map { it.id })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        downloadedMediaDao.setFavorite(id, isFavorite)
    }

    override suspend fun setTag(id: Long, tag: String) = withContext(Dispatchers.IO) {
        downloadedMediaDao.setTag(id, tag.trim().take(24))
    }

    override suspend fun clearHistory(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            downloadedMediaDao.clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pruneDeletedEntries() = withContext(Dispatchers.IO) {
        try {
            val allItems = downloadedMediaDao.observeAll().first()
            for (item in allItems) {
                if (item.mediaStoreUri.isBlank()) continue
                val exists = try {
                    context.contentResolver.openAssetFileDescriptor(Uri.parse(item.mediaStoreUri), "r")?.use { }
                    true
                } catch (_: Exception) {
                    false
                }
                if (!exists) {
                    downloadedMediaDao.deleteById(item.id)
                }
            }
        } catch (_: Exception) {}
    }
}
