package com.cleansaver.app.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

enum class MediaType {
    VIDEO,
    IMAGE,
    AUDIO
}

enum class UrlKind {
    REEL,
    POST
}

enum class Platform(val displayName: String) {
    INSTAGRAM("Instagram"),
    THREADS("Threads"),
    X_TWITTER("X"),
    REDDIT("Reddit"),
    PINTEREST("Pinterest"),
    TIKTOK("TikTok"),
    YOUTUBE_SHORTS("YouTube")
}

@Keep
@Serializable
data class ParsedUrl(
    val rawUrl: String,
    val canonicalUrl: String,
    val shortcode: String,
    val kind: UrlKind,
    val platform: Platform = Platform.INSTAGRAM
)

@Keep
@Serializable
data class MediaCandidate(
    val shortcode: String,
    val slideIndex: Int,
    val mediaType: MediaType,
    val downloadUrl: String,
    val thumbnailUrl: String? = null,
    val audioUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val caption: String? = null,
    val creatorHandle: String? = null,
    val platform: Platform = Platform.INSTAGRAM,
    val sourceUrl: String? = null
) {
    fun extractHashtags(): List<String> {
        if (caption.isNullOrBlank()) return emptyList()
        return Regex("#[A-Za-z0-9_]+").findAll(caption).map { it.value }.distinct().toList()
    }
}
