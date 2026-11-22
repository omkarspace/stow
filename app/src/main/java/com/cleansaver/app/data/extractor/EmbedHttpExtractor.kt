package com.cleansaver.app.data.extractor

import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.UrlKind
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
                if (response.code == 404) return@withContext Result.failure(CustomThrowable(AppError.PostNotFound))
                if (response.code == 429) return@withContext Result.failure(CustomThrowable(AppError.NetworkRateLimited))
                if (!response.isSuccessful) return@withContext Result.failure(CustomThrowable(AppError.ParserOutdated))

                val body = response.body?.string() ?: ""
                if (body.contains("This Account is Private", ignoreCase = true) ||
                    body.contains("\"is_private\":true", ignoreCase = true)
                ) {
                    return@withContext Result.failure(CustomThrowable(AppError.PrivateAccountNotSupported))
                }

                val candidates = parseCandidatesFromHtml(body, parsedUrl.shortcode, parsedUrl.kind, strategyConfig)

                if (candidates.isNotEmpty()) {
                    Result.success(candidates)
                } else {
                    Result.failure(CustomThrowable(AppError.ParserOutdated))
                }
            }
        } catch (e: UnknownHostException) {
            Result.failure(CustomThrowable(AppError.NoInternet))
        } catch (e: IOException) {
            Result.failure(CustomThrowable(AppError.NoInternet))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseCandidatesFromHtml(
        body: String,
        shortcode: String,
        urlKind: UrlKind,
        strategyConfig: StrategyDto
    ): List<MediaCandidate> {
        val seenUrls = LinkedHashSet<String>()
        val candidates = mutableListOf<MediaCandidate>()

        // Collect all image/thumbnail URLs first so videos can attach a matching thumbnail
        val imageUrls = LinkedHashSet<String>()
        strategyConfig.imageRegex?.takeIf { it.isNotBlank() }?.let { regexStr ->
            extractMatches(Pattern.compile(regexStr), body, imageUrls)
        }
        extractMatches(displayUrlFallbackPattern, body, imageUrls)
        extractMatches(embeddedImgPattern, body, imageUrls)
        extractMatches(ogImagePattern, body, imageUrls)
        val imageList = imageUrls.toList()

        // Collect video URLs from configured regex + modern Instagram JSON/HTML patterns
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

        // Filter out non-media or static asset URLs
        val validVideos = videoUrls.filter { url ->
            !url.contains("static.cdninstagram.com/rsrc.php", ignoreCase = true)
        }

        val isKnownVideoPost = urlKind == UrlKind.REEL ||
            body.contains("\"is_video\":true", ignoreCase = true) ||
            body.contains("EmbedIsVideo", ignoreCase = true)

        // CRITICAL: Never return only a thumbnail image for a Reel or video post.
        // Returning empty lets MediaExtractorChain fall back to EphemeralWebViewExtractor.
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
                        width = null,
                        height = null
                    )
                )
            }
        }

        // Include image slides only for non-Reel posts (or mixed carousels)
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
                            width = null,
                            height = null
                        )
                    )
                }
            }
        }

        return candidates
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
}
