package com.cleansaver.app.data.extractor

import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.Platform
import com.cleansaver.app.domain.model.UrlKind
import com.cleansaver.app.util.ExtractionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class EmbedHttpExtractor(
    private val baseOkHttpClient: OkHttpClient
) : MediaExtractorStrategy {

    override val id: String = "HTTP_EMBED"

    private val videoUrlFallbackPattern = Pattern.compile(
        """\\?"(?:video_url|playback_url)\\?"\s*:\s*\\?"(https?:[^"\\]*(?:\\[^"\\][^"\\]*)*)\\?""""
    )
    private val videoVersionsUrlPattern = Pattern.compile(
        """\\?"video_versions\\?"\s*:\s*\[\s*\{[^}]*?\\?"url\\?"\s*:\s*\\?"(https?:[^"\\]*(?:\\[^"\\][^"\\]*)*)\\?""""
    )
    private val mp4DirectPattern = Pattern.compile(
        """(https?:(?:\\/|/){2}[^"'\s\\<>]+?\.mp4[^"'\s\\<>]*)"""
    )
    private val displayUrlFallbackPattern = Pattern.compile(
        """\\?"display_url\\?"\s*:\s*\\?"(https?:[^"\\]*(?:\\[^"\\][^"\\]*)*)\\?""""
    )
    private val ogVideoPattern = Pattern.compile(
        """<meta[^>]+property=["']og:video(?::secure_url)?["'][^>]+content=["'](https?://[^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val ogImagePattern = Pattern.compile(
        """<meta[^>]+property=["']og:image["'][^>]+content=["'](https?://[^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val ogDescPattern = Pattern.compile(
        """<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val usernameJsonPattern = Pattern.compile(
        """\\?"username\\?"\s*:\s*\\?"([A-Za-z0-9._]{1,30})\\?""""
    )
    private val embeddedImgPattern = Pattern.compile(
        """<img[^>]+class=["'][^"']*EmbeddedMediaImage[^"']*["'][^>]+src=["'](https?://[^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val videoTagPattern = Pattern.compile(
        """<video[^>]+src=["'](https?://[^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )

    override suspend fun extract(
        parsedUrl: ParsedUrl,
        strategyConfig: StrategyDto,
        userAgent: String
    ): Result<List<MediaCandidate>> = withContext(Dispatchers.IO) {
        try {
            val baseTemplate = if (parsedUrl.kind == UrlKind.REEL && strategyConfig.endpointTemplate.contains("/p/{shortcode}/")) {
                strategyConfig.endpointTemplate.replace("/p/{shortcode}/", "/reel/{shortcode}/")
            } else {
                strategyConfig.endpointTemplate
            }
            val targetUrl = baseTemplate.replace("{shortcode}", parsedUrl.shortcode)
            ExtractionLogger.log("HTTP_EMBED", "GET $targetUrl")

            val client = baseOkHttpClient.newBuilder()
                .connectTimeout(strategyConfig.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(strategyConfig.timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .cookieJar(CookieJar.NO_COOKIES)
                .build()

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .build()

            client.newCall(request).execute().use { response ->
                ExtractionLogger.log("HTTP_EMBED", "HTTP ${response.code}")
                if (response.code == 404) return@withContext Result.failure(CustomThrowable(AppError.PostNotFound))
                if (response.code == 429) return@withContext Result.failure(CustomThrowable(AppError.NetworkRateLimited))
                if (!response.isSuccessful) return@withContext Result.failure(CustomThrowable(AppError.ParserOutdated))

                val body = response.body?.string() ?: ""
                if (body.contains("This Account is Private", ignoreCase = true) ||
                    body.contains("\"is_private\":true", ignoreCase = true)
                ) {
                    ExtractionLogger.log("HTTP_EMBED", "Private account detected")
                    return@withContext Result.failure(CustomThrowable(AppError.PrivateAccountNotSupported))
                }

                val candidates = parseCandidatesFromHtml(
                    body = body,
                    shortcode = parsedUrl.shortcode,
                    urlKind = parsedUrl.kind,
                    sourceUrl = parsedUrl.canonicalUrl,
                    strategyConfig = strategyConfig
                )

                if (candidates.isNotEmpty()) {
                    ExtractionLogger.log("HTTP_EMBED", "Extracted ${candidates.size} item(s)")
                    Result.success(candidates)
                } else {
                    ExtractionLogger.log("HTTP_EMBED", "No matching media in HTML; falling back")
                    Result.failure(CustomThrowable(AppError.ParserOutdated))
                }
            }
        } catch (e: UnknownHostException) {
            ExtractionLogger.log("HTTP_EMBED", "Offline: ${e.message}")
            Result.failure(CustomThrowable(AppError.NoInternet))
        } catch (e: IOException) {
            ExtractionLogger.log("HTTP_EMBED", "IO error: ${e.message}")
            Result.failure(CustomThrowable(AppError.NoInternet))
        } catch (e: Exception) {
            ExtractionLogger.log("HTTP_EMBED", "Error: ${e.message}")
            Result.failure(e)
        }
    }

    internal fun parseCandidatesFromHtml(
        body: String,
        shortcode: String,
        urlKind: UrlKind,
        sourceUrl: String = "https://www.instagram.com/p/$shortcode/",
        strategyConfig: StrategyDto
    ): List<MediaCandidate> {
        val seenUrls = LinkedHashSet<String>()
        val candidates = mutableListOf<MediaCandidate>()

        val caption = extractFirstGroup(ogDescPattern, body)?.let { decodeHtmlEntities(it) }
        val creatorHandle = extractFirstGroup(usernameJsonPattern, body)

        val imageUrls = LinkedHashSet<String>()
        strategyConfig.imageRegex?.takeIf { it.isNotBlank() }?.let { regexStr ->
            extractMatches(Pattern.compile(regexStr), body, imageUrls)
        }
        extractMatches(displayUrlFallbackPattern, body, imageUrls)
        extractMatches(embeddedImgPattern, body, imageUrls)
        extractMatches(ogImagePattern, body, imageUrls)
        val imageList = imageUrls.toList()

        val videoUrls = LinkedHashSet<String>()
        strategyConfig.videoRegex?.takeIf { it.isNotBlank() }?.let { regexStr ->
            extractMatches(Pattern.compile(regexStr), body, videoUrls)
        }
        extractMatches(videoUrlFallbackPattern, body, videoUrls)
        extractMatches(videoVersionsUrlPattern, body, videoUrls)
        extractMatches(videoTagPattern, body, videoUrls)
        extractMatches(ogVideoPattern, body, videoUrls)
        if (videoUrls.isEmpty()) {
            extractMatches(mp4DirectPattern, body, videoUrls)
        }

        val validVideos = videoUrls.filter { url ->
            !url.contains("static.cdninstagram.com/rsrc.php", ignoreCase = true)
        }

        val isKnownVideoPost = urlKind == UrlKind.REEL ||
            body.contains("\"is_video\":true", ignoreCase = true) ||
            body.contains("EmbedIsVideo", ignoreCase = true)

        if (isKnownVideoPost && validVideos.isEmpty()) {
            return emptyList()
        }

        var slideIndex = 0
        validVideos.forEachIndexed { idx, videoUrl ->
            if (seenUrls.add(videoUrl)) {
                val thumb = imageList.getOrNull(idx) ?: imageList.firstOrNull()
                candidates.add(
                    MediaCandidate(
                        shortcode = shortcode,
                        slideIndex = slideIndex++,
                        mediaType = MediaType.VIDEO,
                        downloadUrl = videoUrl,
                        thumbnailUrl = thumb,
                        caption = caption,
                        creatorHandle = creatorHandle,
                        platform = Platform.INSTAGRAM,
                        sourceUrl = sourceUrl
                    )
                )
            }
        }

        if (urlKind != UrlKind.REEL) {
            val imagesToAdd = if (validVideos.isEmpty()) {
                imageList
            } else if (imageList.size > validVideos.size) {
                imageList.drop(validVideos.size)
            } else {
                emptyList()
            }

            imagesToAdd.forEach { imageUrl ->
                if (seenUrls.add(imageUrl)) {
                    candidates.add(
                        MediaCandidate(
                            shortcode = shortcode,
                            slideIndex = slideIndex++,
                            mediaType = MediaType.IMAGE,
                            downloadUrl = imageUrl,
                            thumbnailUrl = imageUrl,
                            caption = caption,
                            creatorHandle = creatorHandle,
                            platform = Platform.INSTAGRAM,
                            sourceUrl = sourceUrl
                        )
                    )
                }
            }
        }

        return candidates
    }

    private fun extractFirstGroup(pattern: Pattern, body: String): String? {
        val m = pattern.matcher(body)
        return if (m.find()) m.group(1)?.trim()?.takeIf { it.isNotBlank() } else null
    }

    private fun extractMatches(pattern: Pattern, body: String, destination: MutableSet<String>) {
        val matcher = pattern.matcher(body)
        while (matcher.find()) {
            val rawUrl = matcher.group(1) ?: continue
            val cleanUrl = unescapeUrl(rawUrl)
            if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                destination.add(cleanUrl)
            }
        }
    }

    internal fun unescapeUrl(url: String): String {
        return url
            .replace("\\\\/", "/")
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\\\u0026", "&")
            .replace("&amp;", "&")
            .replace("\\&", "&")
            .trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
