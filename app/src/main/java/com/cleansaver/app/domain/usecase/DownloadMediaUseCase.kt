package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

class DownloadMediaUseCase(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(candidates: List<MediaCandidate>): Flow<Map<Int, DownloadProgress>> {
        return mediaRepository.downloadCandidates(candidates)
    }
}

