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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        try {
            val targetUrl = strategyConfig.endpointTemplate
                .takeIf { it.isNotBlank() }
                ?.replace("{shortcode}", parsedUrl.shortcode)
                ?: parsedUrl.canonicalUrl

            withTimeout(strategyConfig.timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val mainHandler = Handler(Looper.getMainLooper())
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = userAgent
                        settings.saveFormData = false

                        CookieManager.getInstance().setAcceptCookie(false)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: ""
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
                                // Brief delay lets SPA scripts populate <video> / meta tags
                                mainHandler.postDelayed({
                                    if (!continuation.isActive) return@postDelayed
                                    val js = """
                                        (function() {
                                            var video = document.querySelector('meta[property="og:video"]')?.content
                                                || document.querySelector('video')?.src
                                                || '';
                                            var image = document.querySelector('meta[property="og:image"]')?.content
                                                || document.querySelector('img.EmbeddedMediaImage')?.src
                                                || '';
                                            return video + '|||' + image;
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(js) { rawResult ->
                                        if (!continuation.isActive) return@evaluateJavascript
                                        val cleaned = rawResult
                                            ?.trim('"', '\'')
                                            ?.replace("\\/", "/")
                                            ?.replace("\\u0026", "&")
                                            ?: ""
                                        val parts = cleaned.split("|||")
                                        val videoUrl = parts.getOrNull(0)?.trim().orEmpty()
                                        val imageUrl = parts.getOrNull(1)?.trim().orEmpty()

                                        val candidate = when {
                                            videoUrl.startsWith("http") -> MediaCandidate(
                                                shortcode = parsedUrl.shortcode,
                                                slideIndex = 0,
                                                mediaType = MediaType.VIDEO,
                                                downloadUrl = videoUrl,
                                                thumbnailUrl = imageUrl.takeIf { it.startsWith("http") },
                                                width = null,
                                                height = null
                                            )
                                            imageUrl.startsWith("http") -> MediaCandidate(
                                                shortcode = parsedUrl.shortcode,
                                                slideIndex = 0,
                                                mediaType = MediaType.IMAGE,
                                                downloadUrl = imageUrl,
                                                thumbnailUrl = imageUrl,
                                                width = null,
                                                height = null
                                            )
                                            else -> null
                                        }

                                        if (candidate != null) {
                                            continuation.resume(Result.success(listOf(candidate)))
                                        } else {
                                            continuation.resume(Result.failure(CustomThrowable(AppError.ParserOutdated)))
                                        }
                                    }
                                }, 800L)
                            }
                        }
                        loadUrl(targetUrl)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            webView?.apply {
                stopLoading()
                clearCache(true)
                destroy()
            }
        }
    }
}
