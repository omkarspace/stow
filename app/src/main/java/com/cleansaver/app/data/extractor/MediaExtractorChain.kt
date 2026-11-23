package com.cleansaver.app.data.extractor

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.Platform
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import com.cleansaver.app.util.ExtractionLogger

class MediaExtractorChain(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val strategies: Map<String, MediaExtractorStrategy>,
    private val multiPlatformExtractor: MultiPlatformExtractor? = null
) {
    suspend fun extract(
        parsedUrl: ParsedUrl,
        isRetry: Boolean = false,
        preferDataSaver: Boolean = false
    ): Result<List<MediaCandidate>> {
        if (parsedUrl.platform != Platform.INSTAGRAM && multiPlatformExtractor != null) {
            return multiPlatformExtractor.extract(parsedUrl, preferDataSaver = preferDataSaver)
        }

        val config = remoteConfigRepository.getActiveConfig(forceRefresh = false)

        if (config.maintenanceMode) {
            ExtractionLogger.log("CHAIN", "Maintenance mode active")
            return Result.failure(CustomThrowable(AppError.ParserOutdated))
        }

        val userAgent = config.userAgents.randomOrNull()
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

        val enabledStrategies = config.strategies.filter { it.enabled }
        var lastError: AppError = AppError.ParserOutdated

        for (strategyConfig in enabledStrategies) {
            val strategyImpl = strategies[strategyConfig.type] ?: continue
            ExtractionLogger.log("CHAIN", "Trying strategy ${strategyConfig.id} (${strategyConfig.type})")
            val result = strategyImpl.extract(parsedUrl, strategyConfig, userAgent)

            if (result.isSuccess) {
                val candidates = result.getOrNull()
                if (!candidates.isNullOrEmpty()) {
                    ExtractionLogger.log("CHAIN", "Success via ${strategyConfig.id}: ${candidates.size} item(s)")
                    return Result.success(candidates)
                }
            } else {
                val error = (result.exceptionOrNull() as? CustomThrowable)?.appError
                    ?: AppError.Unknown(result.exceptionOrNull()?.message ?: "Extractor failed")
                ExtractionLogger.log("CHAIN", "Failed ${strategyConfig.id}: $error")

                if (error is AppError.PrivateAccountNotSupported ||
                    error is AppError.PostNotFound ||
                    error is AppError.NetworkRateLimited
                ) {
                    return Result.failure(CustomThrowable(error))
                }
                lastError = error
            }
        }

        if (!isRetry && lastError is AppError.ParserOutdated) {
            ExtractionLogger.log("CHAIN", "Refreshing remote config and retrying")
            remoteConfigRepository.getActiveConfig(forceRefresh = true)
            return extract(parsedUrl, isRetry = true)
        }

        return Result.failure(CustomThrowable(lastError))
    }
}
