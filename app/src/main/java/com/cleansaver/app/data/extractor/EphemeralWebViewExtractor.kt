package com.cleansaver.app.data.extractor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.UrlKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

class EphemeralWebViewExtractor(
    private val context: Context
) : MediaExtractorStrategy {

    override val id: String = "WEBVIEW_EPHEMERAL"

    override suspend fun extract(
        parsedUrl: ParsedUrl,
        strategyConfig: StrategyDto,
        userAgent: String
    ): Result<List<MediaCandidate>> = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        val mainHandler = Handler(Looper.getMainLooper())
        try {
            val targetUrl = parsedUrl.canonicalUrl
            val timeoutMs = maxOf(strategyConfig.timeoutMs, 14_000L)

            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val interceptedMp4 = AtomicReference<String?>(null)
                    val interceptedThumb = AtomicReference<String?>(null)
                    var pollAttempts = 0
                    val maxPolls = 18 // 18 * 600ms ~= 10.8s polling after page load

                    fun completeIfVideoOrImage(videoUrl: String?, imageUrl: String?, finalAttempt: Boolean) {
                        if (!continuation.isActive) return
                        val bestVideo = listOfNotNull(videoUrl, interceptedMp4.get())
                            .firstOrNull { it.startsWith("http") && !it.startsWith("blob:") }
                        val bestImage = listOfNotNull(imageUrl, interceptedThumb.get())
                            .firstOrNull { it.startsWith("http") }

                        if (bestVideo != null) {
                            continuation.resume(
                                Result.success(
                                    listOf(
                                        MediaCandidate(
                                            shortcode = parsedUrl.shortcode,
                                            slideIndex = 0,
                                            mediaType = MediaType.VIDEO,
                                            downloadUrl = bestVideo,
                                            thumbnailUrl = bestImage,
                                            width = null,
                                            height = null
                                        )
                                    )
                                )
                            )
                            return
                        }

                        // For Reels, NEVER return only a thumbnail image
                        if (parsedUrl.kind != UrlKind.REEL && bestImage != null && finalAttempt) {
                            continuation.resume(
                                Result.success(
                                    listOf(
                                        MediaCandidate(
                                            shortcode = parsedUrl.shortcode,
                                            slideIndex = 0,
                                            mediaType = MediaType.IMAGE,
                                            downloadUrl = bestImage,
                                            thumbnailUrl = bestImage,
                                            width = null,
                                            height = null
                                        )
                                    )
                                )
                            )
                            return
                        }

                        if (finalAttempt) {
                            continuation.resume(Result.failure(CustomThrowable(AppError.ParserOutdated)))
                        }
                    }

                    val extractJs = """
                        (function() {
                            var video = '';
                            var image = '';

                            var ogVideo = document.querySelector('meta[property="og:video"]')?.content
                                || document.querySelector('meta[property="og:video:secure_url"]')?.content || '';
                            if (ogVideo && ogVideo.indexOf('http') === 0) video = ogVideo;

                            if (!video) {
                                var vids = document.querySelectorAll('video');
                                for (var i = 0; i < vids.length; i++) {
                                    var src = vids[i].currentSrc || vids[i].src || '';
                                    if (!src) {
                                        var s = vids[i].querySelector('source');
                                        if (s) src = s.src || '';
                                    }
                                    if (src && src.indexOf('http') === 0 && src.indexOf('blob:') !== 0) {
                                        video = src;
                                    }
                                    if (!image && vids[i].poster && vids[i].poster.indexOf('http') === 0) {
                                        image = vids[i].poster;
                                    }
                                }
                            }

                            if (!video) {
                                var scripts = document.querySelectorAll('script');
                                for (var j = 0; j < scripts.length; j++) {
                                    var txt = scripts[j].textContent || '';
                                    if (txt.indexOf('video_url') !== -1 || txt.indexOf('video_versions') !== -1 || txt.indexOf('.mp4') !== -1) {
                                        var m1 = txt.match(/"video_url"\s*:\s*"(https?:[^"]+)"/);
                                        if (m1 && m1[1]) { video = m1[1]; break; }
                                        var m2 = txt.match(/"video_versions"\s*:\s*\[\s*\{[^}]*?"url"\s*:\s*"(https?:[^"]+)"/);
                                        if (m2 && m2[1]) { video = m2[1]; break; }
                                    }
                                }
                            }

                            if (!image) {
                                image = document.querySelector('meta[property="og:image"]')?.content
                                    || document.querySelector('img.EmbeddedMediaImage')?.src
                                    || '';
                            }

                            return video + '|||' + image;
                        })();
                    """.trimIndent()

                    fun schedulePoll(view: WebView?) {
                        if (!continuation.isActive) return
                        pollAttempts++
                        val isFinal = pollAttempts >= maxPolls

                        // If we already intercepted a clean MP4 stream from WebView network traffic, finish immediately!
                        if (interceptedMp4.get() != null) {
                            completeIfVideoOrImage(interceptedMp4.get(), interceptedThumb.get(), isFinal)
                            return
                        }

                        view?.evaluateJavascript(extractJs) { rawResult ->
                            if (!continuation.isActive) return@evaluateJavascript
                            val cleaned = rawResult
                                ?.trim('"', '\'')
                                ?.replace("\\\\/", "/")
                                ?.replace("\\/", "/")
                                ?.replace("\\u0026", "&")
                                ?.replace("&amp;", "&")
                                ?: ""
                            val parts = cleaned.split("|||")
                            val videoUrl = parts.getOrNull(0)?.trim().orEmpty().takeIf { it.startsWith("http") }
                            val imageUrl = parts.getOrNull(1)?.trim().orEmpty().takeIf { it.startsWith("http") }
                            if (imageUrl != null) {
                                interceptedThumb.compareAndSet(null, imageUrl)
                            }

                            if (videoUrl != null || isFinal) {
                                completeIfVideoOrImage(videoUrl, imageUrl, isFinal)
                            } else {
                                mainHandler.postDelayed({ schedulePoll(view) }, 600L)
                            }
                        }
                    }

                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = userAgent
                        settings.saveFormData = false

                        CookieManager.getInstance().setAcceptCookie(true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: ""
                                // Capture direct MP4 video stream requested by Instagram's web player
                                if (url.contains(".mp4", ignoreCase = true) &&
                                    (url.contains("cdninstagram.com", ignoreCase = true) || url.contains("fbcdn.net", ignoreCase = true)) &&
                                    !url.contains("bytestart=", ignoreCase = true)
                                ) {
                                    interceptedMp4.compareAndSet(null, url)
                                    mainHandler.post {
                                        completeIfVideoOrImage(url, interceptedThumb.get(), finalAttempt = false)
                                    }
                                }

                                if (url.contains("google-analytics.com") ||
                                    url.contains("facebook.net") ||
                                    url.endsWith(".ttf") ||
                                    url.endsWith(".woff2")
                                ) {
                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (pollAttempts == 0) {
                                    mainHandler.postDelayed({ schedulePoll(view) }, 700L)
                                }
                            }
                        }
                        loadUrl(targetUrl)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            mainHandler.removeCallbacksAndMessages(null)
            webView?.apply {
                stopLoading()
                clearCache(true)
                destroy()
            }
            CookieManager.getInstance().removeAllCookies(null)
        }
    }
}
