package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.repository.MediaRepository

class ExtractMediaUseCase(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        return mediaRepository.extractMedia(parsedUrl)
    }
}

