package com.cleansaver.app.data.extractor

import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl

interface MediaExtractorStrategy {
    val id: String
    suspend fun extract(
        parsedUrl: ParsedUrl,
        strategyConfig: StrategyDto,
        userAgent: String
    ): Result<List<MediaCandidate>>
}

