package com.cleansaver.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.data.local.MediaStoreWriter
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.util.NotificationHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json

class MediaDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val json: Json
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CANDIDATES_JSON = "key_candidates_json"
        const val KEY_SHORTCODE = "key_shortcode"
        const val KEY_SOURCE_URL = "key_source_url"
    }

    override suspend fun doWork(): Result = coroutineScope {
        val candidatesJson = inputData.getString(KEY_CANDIDATES_JSON) ?: return@coroutineScope Result.failure()
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: ""
        val candidates = json.decodeFromString<List<MediaCandidate>>(candidatesJson)

        if (candidates.isEmpty()) return@coroutineScope Result.success()

        // Promote to foreground service notification to prevent process death on Android 10+
        try {
            setForeground(NotificationHelper.createForegroundInfo(applicationContext, "Saving ${candidates.size} item(s) to Gallery..."))
        } catch (_: Exception) {
            // Non-fatal if foreground notification cannot be posted (e.g. permission omitted on older OS)
        }

        val semaphore = Semaphore(permits = 2) // Enforce maximum 2 concurrent file downloads

        val tasks = candidates.map { candidate ->
            async {
                semaphore.withPermit {
                    // Check local DB deduplication state
                    val existing = downloadedMediaDao.findExisting(candidate.shortcode, candidate.slideIndex)
                    if (existing != null && verifyUriExists(existing.mediaStoreUri)) {
                        return@async true
                    }

                    val downloadResult = mediaStoreWriter.downloadToGallery(candidate) { _, _, _ -> }
                    if (downloadResult.isSuccess) {
                        val meta = downloadResult.getOrThrow()
                        downloadedMediaDao.upsert(
                            DownloadedMediaEntity(
                                shortcode = candidate.shortcode,
                                slideIndex = candidate.slideIndex,
                                mediaType = candidate.mediaType.name,
                                mediaStoreUri = meta.mediaStoreUri,
                                fileName = meta.fileName,
                                fileSizeBytes = meta.fileSize,
                                sourceUrl = sourceUrl,
                                createdAtMs = System.currentTimeMillis()
                            )
                        )
                        true
                    } else {
                        false
                    }
                }
            }
        }

        val results = tasks.awaitAll()
        if (results.all { it }) Result.success() else Result.retry()
    }

    private fun verifyUriExists(uriStr: String): Boolean {
        return try {
            applicationContext.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(uriStr), "r")?.use { }
            true
        } catch (e: Exception) {
            false
        }
    }
}
