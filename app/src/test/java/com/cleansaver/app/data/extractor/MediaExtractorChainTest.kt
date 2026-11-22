package com.cleansaver.app.data.extractor

import com.cleansaver.app.data.remote.dto.ParserConfigDto
import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.data.remote.dto.UrlPatternsDto
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.UrlKind
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExtractorChainTest {

    private fun createDummyConfig(
        strategies: List<StrategyDto>,
        maintenanceMode: Boolean = false
    ): ParserConfigDto = ParserConfigDto(
        schemaVersion = 1,
        minAppVersionCode = 1,
        maintenanceMode = maintenanceMode,
        maintenanceMessage = "",
        configTtlHours = 24,
        userAgents = listOf("TestAgent"),
        urlPatterns = UrlPatternsDto("", "", ""),
        strategies = strategies
    )

    private val sampleParsedUrl = ParsedUrl(
        rawUrl = "https://instagram.com/reel/TEST12345/",
        canonicalUrl = "https://www.instagram.com/reel/TEST12345/",
        shortcode = "TEST12345",
        kind = UrlKind.REEL
    )

    @Test
    fun `returns success when primary strategy succeeds`() = runTest {
        val strategyDto = StrategyDto(id = "s1", type = "STRAT_1", enabled = true, endpointTemplate = "")
        val mockRepo = object : RemoteConfigRepository {
            override suspend fun getActiveConfig(forceRefresh: Boolean): ParserConfigDto =
                createDummyConfig(listOf(strategyDto))
            override suspend fun refreshConfig(force: Boolean): Result<Unit> = Result.success(Unit)
        }

        val mockStrategy = object : MediaExtractorStrategy {
            override val id: String = "STRAT_1"
            override suspend fun extract(
                parsedUrl: ParsedUrl,
                strategyConfig: StrategyDto,
                userAgent: String
            ): Result<List<MediaCandidate>> = Result.success(
                listOf(
                    MediaCandidate(
                        shortcode = parsedUrl.shortcode,
                        slideIndex = 0,
                        mediaType = MediaType.VIDEO,
                        downloadUrl = "https://cdn.instagram.com/video.mp4"
                    )
                )
            )
        }

        val chain = MediaExtractorChain(mockRepo, mapOf("STRAT_1" to mockStrategy))
        val result = chain.extract(sampleParsedUrl)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("https://cdn.instagram.com/video.mp4", result.getOrThrow().first().downloadUrl)
    }

    @Test
    fun `short-circuits instantly when strategy reports private account`() = runTest {
        val s1 = StrategyDto(id = "s1", type = "STRAT_1", enabled = true, endpointTemplate = "")
        val s2 = StrategyDto(id = "s2", type = "STRAT_2", enabled = true, endpointTemplate = "")

        val mockRepo = object : RemoteConfigRepository {
            override suspend fun getActiveConfig(forceRefresh: Boolean): ParserConfigDto =
                createDummyConfig(listOf(s1, s2))
            override suspend fun refreshConfig(force: Boolean): Result<Unit> = Result.success(Unit)
        }

        var s2Called = false

        val strat1 = object : MediaExtractorStrategy {
            override val id: String = "STRAT_1"
            override suspend fun extract(
                parsedUrl: ParsedUrl,
                strategyConfig: StrategyDto,
                userAgent: String
            ): Result<List<MediaCandidate>> = Result.failure(CustomThrowable(AppError.PrivateAccountNotSupported))
        }

        val strat2 = object : MediaExtractorStrategy {
            override val id: String = "STRAT_2"
            override suspend fun extract(
                parsedUrl: ParsedUrl,
                strategyConfig: StrategyDto,
                userAgent: String
            ): Result<List<MediaCandidate>> {
                s2Called = true
                return Result.success(emptyList())
            }
        }

        val chain = MediaExtractorChain(mockRepo, mapOf("STRAT_1" to strat1, "STRAT_2" to strat2))
        val result = chain.extract(sampleParsedUrl)

        assertTrue(result.isFailure)
        val throwable = result.exceptionOrNull() as? CustomThrowable
        assertEquals(AppError.PrivateAccountNotSupported, throwable?.appError)
        assertEquals(false, s2Called)
    }

    @Test
    fun `returns parser outdated when maintenance mode is active`() = runTest {
        val mockRepo = object : RemoteConfigRepository {
            override suspend fun getActiveConfig(forceRefresh: Boolean): ParserConfigDto =
                createDummyConfig(emptyList(), maintenanceMode = true)
            override suspend fun refreshConfig(force: Boolean): Result<Unit> = Result.success(Unit)
        }

        val chain = MediaExtractorChain(mockRepo, emptyMap())
        val result = chain.extract(sampleParsedUrl)

        assertTrue(result.isFailure)
        val throwable = result.exceptionOrNull() as? CustomThrowable
        assertEquals(AppError.ParserOutdated, throwable?.appError)
    }
}

