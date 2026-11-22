package com.cleansaver.app.data.repository

import android.content.Context
import android.net.Uri
import com.cleansaver.app.data.extractor.MediaExtractorChain
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.data.local.MediaStoreWriter
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.repository.MediaRepository
import com.cleansaver.app.domain.usecase.ValidateUrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MediaRepositoryImpl(
    private val context: Context,
    private val extractorChain: MediaExtractorChain,
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val okHttpClient: OkHttpClient,
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val applicationScope: CoroutineScope
) : MediaRepository {

    override suspend fun extractMedia(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        val resolvedUrl = if (parsedUrl.rawUrl.contains("/share/", ignoreCase = true)) {
            resolveShareRedirect(parsedUrl)
        } else {
            parsedUrl
        }
        return extractorChain.extract(resolvedUrl)
    }

    private suspend fun resolveShareRedirect(parsedUrl: ParsedUrl): ParsedUrl = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(parsedUrl.rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (!finalUrl.contains("/share/", ignoreCase = true)) {
                    validateUrlUseCase(finalUrl).getOrNull() ?: parsedUrl
                } else {
                    parsedUrl
                }
            }
        } catch (_: Exception) {
            parsedUrl
        }
    }

    override suspend fun isMediaAlreadyDownloaded(shortcode: String, slideIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val existing = downloadedMediaDao.findExisting(shortcode, slideIndex) ?: return@withContext false
        try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(existing.mediaStoreUri), "r")?.use { }
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun downloadCandidates(candidates: List<MediaCandidate>): Flow<Map<Int, DownloadProgress>> {
        val initialMap = candidates.associate { it.slideIndex to (DownloadProgress.Queued as DownloadProgress) }
        val progressState = MutableStateFlow(initialMap)

        // Launch on applicationScope so downloads finish safely even if the UI or Share sheet is dismissed
        applicationScope.launch(Dispatchers.IO) {
            val currentMap = initialMap.toMutableMap()
            for (candidate in candidates) {
                val key = candidate.slideIndex
                currentMap[key] = DownloadProgress.Running(bytesRead = 0, totalBytes = 0, percent = 0)
                progressState.value = currentMap.toMap()

                val result = mediaStoreWriter.downloadToGallery(candidate) { bytesRead, totalBytes, percent ->
                    currentMap[key] = DownloadProgress.Running(bytesRead, totalBytes, percent)
                    progressState.value = currentMap.toMap()
                }

                if (result.isSuccess) {
                    val meta = result.getOrThrow()
                    downloadedMediaDao.upsert(
                        DownloadedMediaEntity(
                            shortcode = candidate.shortcode,
                            slideIndex = candidate.slideIndex,
                            mediaType = candidate.mediaType.name,
                            mediaStoreUri = meta.mediaStoreUri,
                            fileName = meta.fileName,
                            fileSizeBytes = meta.fileSize,
                            sourceUrl = "https://www.instagram.com/p/${candidate.shortcode}/",
                            createdAtMs = System.currentTimeMillis()
                        )
                    )
                    currentMap[key] = DownloadProgress.Completed(meta.mediaStoreUri, meta.fileName)
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown download error"
                    currentMap[key] = DownloadProgress.Failed(AppError.StorageError(errorMsg))
                }
                progressState.value = currentMap.toMap()
            }
        }

        return progressState.asStateFlow()
    }
}
