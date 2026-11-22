package com.cleansaver.app.data.extractor

import android.content.Context
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
            withTimeout(strategyConfig.timeoutMs) {
                suspendCancellableCoroutine { continuation ->
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
                                    url.endsWith(".ttf")
                                ) {
                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                evaluateJavascript(
                                    "(function() { return document.querySelector('meta[property=\"og:video\"]')?.content || document.querySelector('meta[property=\"og:image\"]')?.content || ''; })();"
                                ) { result ->
                                    val extractedUrl = result?.trim('"', '\'')?.replace("\\/", "/") ?: ""
                                    if (extractedUrl.isNotBlank() && extractedUrl != "null") {
                                        val type = if (extractedUrl.contains(".mp4") || extractedUrl.contains("video")) {
                                            MediaType.VIDEO
                                        } else {
                                            MediaType.IMAGE
                                        }
                                        val candidate = MediaCandidate(
                                            shortcode = parsedUrl.shortcode,
                                            slideIndex = 0,
                                            mediaType = type,
                                            downloadUrl = extractedUrl,
                                            thumbnailUrl = null,
                                            width = null,
                                            height = null
                                        )
                                        if (continuation.isActive) {
                                            continuation.resume(Result.success(listOf(candidate)))
                                        }
                                    } else {
                                        if (continuation.isActive) {
                                            continuation.resume(Result.failure(CustomThrowable(AppError.ParserOutdated)))
                                        }
                                    }
                                }
                            }
                        }
                        loadUrl(parsedUrl.canonicalUrl)
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

