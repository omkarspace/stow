package com.cleansaver.app.data.extractor

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.Platform
import com.cleansaver.app.util.ExtractionLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.regex.Pattern

class MultiPlatformExtractor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val ua =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val ogVideoPattern = Pattern.compile(
        """<meta[^>]+property=["']og:video(?::secure_url)?["'][^>]+content=["'](https?://[^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val ogImagePattern = Pattern.compile(
        """<meta[^>]+property=["']og:image["'][^>]+content=["'](https?://[^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val ogDescPattern = Pattern.compile(
        """<meta[^>]+property=["']og:(?:description|title)["'][^>]+content=["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val mp4Pattern = Pattern.compile("""(https?:(?:\\/|/){2}[^"'\s\\<>]+?\.mp4[^"'\s\\<>]*)""")
    private val ytStreamUrlPattern = Pattern.compile(
        """"url"\s*:\s*"(https://[^"]*googlevideo\.com/videoplayback[^"]*mime=video%2Fmp4[^"]*)""""
    )

    suspend fun extract(parsedUrl: ParsedUrl, preferDataSaver: Boolean = false): Result<List<MediaCandidate>> =
        withContext(Dispatchers.IO) {
            ExtractionLogger.log("MULTI", "Extracting ${parsedUrl.platform.displayName} (dataSaver=$preferDataSaver)")
            try {
                when (parsedUrl.platform) {
                    Platform.X_TWITTER -> extractXTwitter(parsedUrl)
                    Platform.REDDIT -> extractReddit(parsedUrl)
                    Platform.THREADS -> extractGenericMetaHtml(parsedUrl)
                    Platform.PINTEREST -> extractGenericMetaHtml(parsedUrl)
                    Platform.TIKTOK -> extractTikTok(parsedUrl, preferDataSaver)
                    Platform.YOUTUBE_SHORTS -> extractYouTubeShorts(parsedUrl, preferDataSaver)
                    Platform.INSTAGRAM -> Result.failure(CustomThrowable(AppError.ParserOutdated))
                }
            } catch (e: UnknownHostException) {
                Result.failure(CustomThrowable(AppError.NoInternet))
            } catch (e: IOException) {
                Result.failure(CustomThrowable(AppError.NoInternet))
            } catch (e: Exception) {
                ExtractionLogger.log("MULTI", "Error: ${e.message}")
                Result.failure(e)
            }
        }

    private fun extractXTwitter(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        val apiUrl = parsedUrl.canonicalUrl
            .replace("https://x.com/", "https://api.vxtwitter.com/")
            .replace("https://twitter.com/", "https://api.vxtwitter.com/")
        val request = Request.Builder().url(apiUrl).header("User-Agent", ua).build()

        okHttpClient.newCall(request).execute().use { response ->
            ExtractionLogger.log("X_API", "HTTP ${response.code}")
            if (response.code == 404) return Result.failure(CustomThrowable(AppError.PostNotFound))
            if (!response.isSuccessful) return Result.failure(CustomThrowable(AppError.ParserOutdated))

            val body = response.body?.string() ?: return Result.failure(CustomThrowable(AppError.ParserOutdated))
            val root = json.parseToJsonElement(body).jsonObject
            val caption = root["text"]?.jsonPrimitive?.content
            val creator = root["user_screen_name"]?.jsonPrimitive?.content
            val mediaExtended = root["media_extended"]?.jsonArray ?: return Result.failure(CustomThrowable(AppError.PostNotFound))

            val candidates = mediaExtended.mapIndexedNotNull { idx, elem ->
                val obj = elem.jsonObject
                val url = obj["url"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                val typeStr = obj["type"]?.jsonPrimitive?.content?.lowercase() ?: "image"
                val thumb = obj["thumbnail_url"]?.jsonPrimitive?.content
                MediaCandidate(
                    shortcode = parsedUrl.shortcode,
                    slideIndex = idx,
                    mediaType = if (typeStr == "video" || typeStr == "gif") MediaType.VIDEO else MediaType.IMAGE,
                    downloadUrl = url,
                    thumbnailUrl = thumb ?: url.takeIf { typeStr == "image" },
                    caption = caption,
                    creatorHandle = creator,
                    platform = Platform.X_TWITTER,
                    sourceUrl = parsedUrl.canonicalUrl
                )
            }
            return if (candidates.isNotEmpty()) Result.success(candidates)
            else Result.failure(CustomThrowable(AppError.PostNotFound))
        }
    }

    private fun extractReddit(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        val jsonUrl = parsedUrl.canonicalUrl.trimEnd('/') + ".json"
        val request = Request.Builder().url(jsonUrl).header("User-Agent", "Stow/3.0 (Android)").build()

        okHttpClient.newCall(request).execute().use { response ->
            ExtractionLogger.log("REDDIT", "HTTP ${response.code}")
            if (response.code == 404) return Result.failure(CustomThrowable(AppError.PostNotFound))
            if (response.code == 429) return Result.failure(CustomThrowable(AppError.NetworkRateLimited))
            if (!response.isSuccessful) return Result.failure(CustomThrowable(AppError.ParserOutdated))

            val body = response.body?.string() ?: return Result.failure(CustomThrowable(AppError.ParserOutdated))
            val listing = json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject
            val postData = listing?.get("data")?.jsonObject
                ?.get("children")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("data")?.jsonObject
                ?: return Result.failure(CustomThrowable(AppError.PostNotFound))

            val title = postData["title"]?.jsonPrimitive?.content
            val author = postData["author"]?.jsonPrimitive?.content
            val thumb = postData["thumbnail"]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }

            val videoUrl = postData["secure_media"]?.jsonObject
                ?.get("reddit_video")?.jsonObject
                ?.get("fallback_url")?.jsonPrimitive?.content
                ?.replace("&amp;", "&")

            if (!videoUrl.isNullOrBlank()) {
                // Reddit serves DASH video & audio separately; derive companion DASH audio URL for MediaMuxer
                val basePath = videoUrl.substringBeforeLast("/")
                val audioUrl = "$basePath/DASH_AUDIO_128.mp4"
                return Result.success(
                    listOf(
                        MediaCandidate(
                            shortcode = parsedUrl.shortcode,
                            slideIndex = 0,
                            mediaType = MediaType.VIDEO,
                            downloadUrl = videoUrl,
                            thumbnailUrl = thumb,
                            audioUrl = audioUrl,
                            caption = title,
                            creatorHandle = author,
                            platform = Platform.REDDIT,
                            sourceUrl = parsedUrl.canonicalUrl
                        )
                    )
                )
            }

            val directUrl = postData["url_overridden_by_dest"]?.jsonPrimitive?.content
                ?.replace("&amp;", "&")
            if (!directUrl.isNullOrBlank() && (directUrl.endsWith(".jpg") || directUrl.endsWith(".jpeg") || directUrl.endsWith(".png") || directUrl.endsWith(".webp"))) {
                return Result.success(
                    listOf(
                        MediaCandidate(
                            shortcode = parsedUrl.shortcode,
                            slideIndex = 0,
                            mediaType = MediaType.IMAGE,
                            downloadUrl = directUrl,
                            thumbnailUrl = directUrl,
                            caption = title,
                            creatorHandle = author,
                            platform = Platform.REDDIT,
                            sourceUrl = parsedUrl.canonicalUrl
                        )
                    )
                )
            }

            return Result.failure(CustomThrowable(AppError.PostNotFound))
        }
    }

    private fun extractTikTok(parsedUrl: ParsedUrl, preferDataSaver: Boolean): Result<List<MediaCandidate>> {
        val encoded = URLEncoder.encode(parsedUrl.canonicalUrl, "UTF-8")
        val apiUrl = "https://www.tikwm.com/api/?url=$encoded&hd=1"
        val request = Request.Builder().url(apiUrl).header("User-Agent", ua).build()

        okHttpClient.newCall(request).execute().use { response ->
            ExtractionLogger.log("TIKTOK", "HTTP ${response.code}")
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject
                if (data != null) {
                    val hdPlay = data["hdplay"]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
                    val sdPlay = data["play"]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
                    val chosen = if (preferDataSaver) (sdPlay ?: hdPlay) else (hdPlay ?: sdPlay)
                    val cover = data["cover"]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
                    val title = data["title"]?.jsonPrimitive?.content
                    val author = data["author"]?.jsonObject?.get("unique_id")?.jsonPrimitive?.content

                    if (!chosen.isNullOrBlank()) {
                        return Result.success(
                            listOf(
                                MediaCandidate(
                                    shortcode = parsedUrl.shortcode,
                                    slideIndex = 0,
                                    mediaType = MediaType.VIDEO,
                                    downloadUrl = chosen,
                                    thumbnailUrl = cover,
                                    caption = title,
                                    creatorHandle = author,
                                    platform = Platform.TIKTOK,
                                    sourceUrl = parsedUrl.canonicalUrl
                                )
                            )
                        )
                    }
                }
            }
        }
        return extractGenericMetaHtml(parsedUrl)
    }

    private fun extractYouTubeShorts(parsedUrl: ParsedUrl, preferDataSaver: Boolean): Result<List<MediaCandidate>> {
        val request = Request.Builder()
            .url(parsedUrl.canonicalUrl)
            .header("User-Agent", ua)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            ExtractionLogger.log("YOUTUBE", "HTTP ${response.code}")
            if (response.code == 404) return Result.failure(CustomThrowable(AppError.PostNotFound))
            if (!response.isSuccessful) return Result.failure(CustomThrowable(AppError.ParserOutdated))

            val html = response.body?.string() ?: ""
            val streams = mutableListOf<String>()
            val matcher = ytStreamUrlPattern.matcher(html)
            while (matcher.find()) {
                val raw = matcher.group(1) ?: continue
                streams.add(raw.replace("\\u0026", "&").replace("\\/", "/"))
            }

            val chosen = if (preferDataSaver) streams.firstOrNull() else streams.lastOrNull()
            val thumb = "https://i.ytimg.com/vi/${parsedUrl.shortcode}/hqdefault.jpg"
            val title = matchFirst(ogDescPattern, html)?.replace("&amp;", "&")?.replace("&quot;", "\"")

            if (!chosen.isNullOrBlank()) {
                return Result.success(
                    listOf(
                        MediaCandidate(
                            shortcode = parsedUrl.shortcode,
                            slideIndex = 0,
                            mediaType = MediaType.VIDEO,
                            downloadUrl = chosen,
                            thumbnailUrl = thumb,
                            caption = title,
                            platform = Platform.YOUTUBE_SHORTS,
                            sourceUrl = parsedUrl.canonicalUrl
                        )
                    )
                )
            }
            return Result.failure(CustomThrowable(AppError.ParserOutdated))
        }
    }

    private fun extractGenericMetaHtml(parsedUrl: ParsedUrl): Result<List<MediaCandidate>> {
        val request = Request.Builder()
            .url(parsedUrl.canonicalUrl)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            ExtractionLogger.log(parsedUrl.platform.name, "HTTP ${response.code}")
            if (response.code == 404) return Result.failure(CustomThrowable(AppError.PostNotFound))
            if (!response.isSuccessful) return Result.failure(CustomThrowable(AppError.ParserOutdated))

            val html = response.body?.string() ?: ""
            val ogVideo = matchFirst(ogVideoPattern, html)
                ?: matchFirst(mp4Pattern, html)?.replace("\\/", "/")?.replace("\\u0026", "&")
            val ogImage = matchFirst(ogImagePattern, html)?.replace("&amp;", "&")
            val caption = matchFirst(ogDescPattern, html)?.replace("&amp;", "&")?.replace("&quot;", "\"")

            val candidate = when {
                !ogVideo.isNullOrBlank() -> MediaCandidate(
                    shortcode = parsedUrl.shortcode,
                    slideIndex = 0,
                    mediaType = MediaType.VIDEO,
                    downloadUrl = ogVideo.replace("&amp;", "&"),
                    thumbnailUrl = ogImage,
                    caption = caption,
                    platform = parsedUrl.platform,
                    sourceUrl = parsedUrl.canonicalUrl
                )
                !ogImage.isNullOrBlank() -> MediaCandidate(
                    shortcode = parsedUrl.shortcode,
                    slideIndex = 0,
                    mediaType = MediaType.IMAGE,
                    downloadUrl = ogImage,
                    thumbnailUrl = ogImage,
                    caption = caption,
                    platform = parsedUrl.platform,
                    sourceUrl = parsedUrl.canonicalUrl
                )
                else -> null
            }

            return if (candidate != null) Result.success(listOf(candidate))
            else Result.failure(CustomThrowable(AppError.ParserOutdated))
        }
    }

    private fun matchFirst(pattern: Pattern, text: String): String? {
        val m = pattern.matcher(text)
        return if (m.find()) m.group(1)?.trim() else null
    }
}
