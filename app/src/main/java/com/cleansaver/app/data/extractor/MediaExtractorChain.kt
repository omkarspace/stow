package com.cleansaver.app.data.extractor

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.repository.RemoteConfigRepository

class MediaExtractorChain(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val strategies: Map<String, MediaExtractorStrategy>
) {
    suspend fun extract(parsedUrl: ParsedUrl, isRetry: Boolean = false): Result<List<MediaCandidate>> {
        val config = remoteConfigRepository.getActiveConfig(forceRefresh = false)

        if (config.maintenanceMode) {
            return Result.failure(CustomThrowable(AppError.ParserOutdated))
        }

        val userAgent = config.userAgents.randomOrNull()
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

        val enabledStrategies = config.strategies.filter { it.enabled }
        var lastError: AppError = AppError.ParserOutdated

        for (strategyConfig in enabledStrategies) {
            val strategyImpl = strategies[strategyConfig.type] ?: continue
            val result = strategyImpl.extract(parsedUrl, strategyConfig, userAgent)

            if (result.isSuccess) {
                val candidates = result.getOrNull()
                if (!candidates.isNullOrEmpty()) {
                    return Result.success(candidates)
                }
            } else {
                val error = (result.exceptionOrNull() as? CustomThrowable)?.appError
                    ?: AppError.Unknown(result.exceptionOrNull()?.message ?: "Extractor failed")

                // Terminal error short-circuit check
                if (error is AppError.PrivateAccountNotSupported ||
                    error is AppError.PostNotFound ||
                    error is AppError.NetworkRateLimited
                ) {
                    return Result.failure(CustomThrowable(error))
                }
                lastError = error
            }
        }

        // Automatic remote config refresh fallback on parse failure
        if (!isRetry && lastError is AppError.ParserOutdated) {
            remoteConfigRepository.getActiveConfig(forceRefresh = true)
            return extract(parsedUrl, isRetry = true)
        }

        return Result.failure(CustomThrowable(lastError))
    }
}

