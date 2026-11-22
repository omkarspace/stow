package com.cleansaver.app.data.repository

import com.cleansaver.app.data.extractor.MediaExtractorChain
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.data.local.MediaStoreWriter
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

class MediaRepositoryImpl(
    private val extractorChain: MediaExtractorChain,
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadedMediaDao: DownloadedMediaDao
) : MediaRepository {

    override suspend fun extractMedia(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        return extractorChain.extract(parsedUrl)
    }

    override suspend fun isMediaAlreadyDownloaded(shortcode: String): Boolean = withContext(Dispatchers.IO) {
        downloadedMediaDao.findExisting(shortcode, 0) != null
    }

    override fun downloadCandidates(candidates: List<MediaCandidate>): Flow<Map<Int, DownloadProgress>> = channelFlow {
        val progressMap = candidates.associate { it.slideIndex to (DownloadProgress.Queued as DownloadProgress) }.toMutableMap()
        send(progressMap.toMap())

        for (candidate in candidates) {
            val key = candidate.slideIndex
            progressMap[key] = DownloadProgress.Running(bytesRead = 0, totalBytes = 0, percent = 0)
            send(progressMap.toMap())

            val result = mediaStoreWriter.downloadToGallery(candidate) { bytesRead, totalBytes, percent ->
                progressMap[key] = DownloadProgress.Running(bytesRead, totalBytes, percent)
                trySend(progressMap.toMap())
            }

            if (result.isSuccess) {
                val meta = result.getOrThrow()
                withContext(Dispatchers.IO) {
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
                }
                progressMap[key] = DownloadProgress.Completed(meta.mediaStoreUri, meta.fileName)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown download error"
                progressMap[key] = DownloadProgress.Failed(AppError.StorageError(errorMsg))
            }
            send(progressMap.toMap())
        }
    }
}
