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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MediaRepositoryImpl(
    private val extractorChain: MediaExtractorChain,
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadedMediaDao: DownloadedMediaDao
) : MediaRepository {

    override suspend fun extractMedia(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        return extractorChain.extract(parsedUrl)
    }

    override fun downloadCandidates(candidates: List<MediaCandidate>): Flow<Map<Int, DownloadProgress>> = flow {
        val progressMap = candidates.indices.associateWith { DownloadProgress.Queued as DownloadProgress }.toMutableMap()
        emit(progressMap.toMap())

        for (candidate in candidates) {
            val index = candidate.slideIndex
            progressMap[index] = DownloadProgress.Running(bytesRead = 0, totalBytes = 0, percent = 0)
            emit(progressMap.toMap())

            val result = mediaStoreWriter.downloadToGallery(candidate) { bytesRead, totalBytes, percent ->
                progressMap[index] = DownloadProgress.Running(bytesRead, totalBytes, percent)
                // Note: we can throttle emits if needed
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
                progressMap[index] = DownloadProgress.Completed(meta.mediaStoreUri, meta.fileName)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown download error"
                progressMap[index] = DownloadProgress.Failed(AppError.StorageError(errorMsg))
            }
            emit(progressMap.toMap())
        }
    }
}

