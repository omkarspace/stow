package com.cleansaver.app.data.extractor

import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.model.ParsedUrl
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class EmbedHttpExtractor(
    private val baseOkHttpClient: OkHttpClient
) : MediaExtractorStrategy {

    override val id: String = "HTTP_EMBED"

    override suspend fun extract(
        parsedUrl: ParsedUrl,
        strategyConfig: StrategyDto,
        userAgent: String
    ): Result<List<MediaCandidate>> {
        return try {
            val targetUrl = strategyConfig.endpointTemplate.replace("{shortcode}", parsedUrl.shortcode)

            val client = baseOkHttpClient.newBuilder()
                .connectTimeout(strategyConfig.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(strategyConfig.timeoutMs, TimeUnit.MILLISECONDS)
                .cookieJar(CookieJar.NO_COOKIES)
                .build()

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) return Result.failure(CustomThrowable(AppError.PostNotFound))
                if (response.code == 429) return Result.failure(CustomThrowable(AppError.NetworkRateLimited))
                if (!response.isSuccessful) return Result.failure(CustomThrowable(AppError.ParserOutdated))

                val body = response.body?.string() ?: ""
                if (body.contains("This Account is Private")) {
                    return Result.failure(CustomThrowable(AppError.PrivateAccountNotSupported))
                }

                val candidates = mutableListOf<MediaCandidate>()

                // Check for video first
                strategyConfig.videoRegex?.let { regexStr ->
                    val matcher = Pattern.compile(regexStr).matcher(body)
                    var index = 0
                    while (matcher.find()) {
                        val rawUrl = matcher.group(1) ?: continue
                        val cleanUrl = unescapeUrl(rawUrl)
                        candidates.add(
                            MediaCandidate(
                                shortcode = parsedUrl.shortcode,
                                slideIndex = index++,
                                mediaType = MediaType.VIDEO,
                                downloadUrl = cleanUrl,
                                thumbnailUrl = null,
                                width = null,
                                height = null
                            )
                        )
                    }
                }

                // If no video found, match images
                if (candidates.isEmpty()) {
                    strategyConfig.imageRegex?.let { regexStr ->
                        val matcher = Pattern.compile(regexStr).matcher(body)
                        var index = 0
                        while (matcher.find()) {
                            val rawUrl = matcher.group(1) ?: continue
                            val cleanUrl = unescapeUrl(rawUrl)
                            candidates.add(
                                MediaCandidate(
                                    shortcode = parsedUrl.shortcode,
                                    slideIndex = index++,
                                    mediaType = MediaType.IMAGE,
                                    downloadUrl = cleanUrl,
                                    thumbnailUrl = null,
                                    width = null,
                                    height = null
                                )
                            )
                        }
                    }
                }

                if (candidates.isNotEmpty()) {
                    Result.success(candidates)
                } else {
                    Result.failure(CustomThrowable(AppError.ParserOutdated))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun unescapeUrl(url: String): String {
        return url.replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\&", "&")
    }
}

