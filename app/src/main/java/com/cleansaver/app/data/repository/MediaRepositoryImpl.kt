package com.cleansaver.app.data.repository

import android.content.Context
import android.net.Uri
import com.cleansaver.app.data.extractor.MediaExtractorChain
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.data.local.MediaStoreWriter
import com.cleansaver.app.data.local.SaveOptions
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.repository.MediaRepository
import com.cleansaver.app.domain.repository.UserPrefsRepository
import com.cleansaver.app.domain.usecase.ValidateUrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MediaRepositoryImpl(
    private val context: Context,
    private val extractorChain: MediaExtractorChain,
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val userPrefsRepository: UserPrefsRepository,
    private val okHttpClient: OkHttpClient,
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val applicationScope: CoroutineScope
) : MediaRepository {

    override suspend fun extractMedia(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        val resolvedUrl = if (parsedUrl.rawUrl.contains("/share/", ignoreCase = true) ||
            parsedUrl.rawUrl.contains("pin.it/", ignoreCase = true)
        ) {
            resolveRedirect(parsedUrl)
        } else {
            parsedUrl
        }
        return extractorChain.extract(resolvedUrl)
    }

    private suspend fun resolveRedirect(parsedUrl: ParsedUrl): ParsedUrl = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(parsedUrl.rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl != parsedUrl.rawUrl) {
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

        applicationScope.launch(Dispatchers.IO) {
            val saveOptions = SaveOptions(
                wifiOnly = userPrefsRepository.wifiOnlyDownloads.first(),
                fileNamingPattern = userPrefsRepository.fileNamingPattern.first(),
                albumName = userPrefsRepository.customAlbumName.first()
            )
            val currentMap = initialMap.toMutableMap()

            for (candidate in candidates) {
                val key = candidate.slideIndex
                currentMap[key] = DownloadProgress.Running(bytesRead = 0, totalBytes = 0, percent = 0)
                progressState.value = currentMap.toMap()

                val result = mediaStoreWriter.downloadToGallery(candidate, saveOptions) { bytesRead, totalBytes, percent ->
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
                            sourceUrl = candidate.sourceUrl ?: "https://www.instagram.com/p/${candidate.shortcode}/",
                            createdAtMs = System.currentTimeMillis(),
                            caption = candidate.caption.orEmpty(),
                            creatorHandle = candidate.creatorHandle.orEmpty(),
                            platform = candidate.platform.name
                        )
                    )
                    currentMap[key] = DownloadProgress.Completed(meta.mediaStoreUri, meta.fileName)
                } else {
                    val appError = (result.exceptionOrNull() as? CustomThrowable)?.appError
                        ?: AppError.StorageError(result.exceptionOrNull()?.message ?: "Unknown download error")
                    currentMap[key] = DownloadProgress.Failed(appError)
                }
                progressState.value = currentMap.toMap()
            }
        }

        return progressState.asStateFlow()
    }
}
