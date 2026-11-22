package com.cleansaver.app.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

enum class MediaType {
    VIDEO,
    IMAGE
}

enum class UrlKind {
    REEL,
    POST
}

@Keep
@Serializable
data class ParsedUrl(
    val rawUrl: String,
    val canonicalUrl: String,
    val shortcode: String,
    val kind: UrlKind
)

@Keep
@Serializable
data class MediaCandidate(
    val shortcode: String,
    val slideIndex: Int,
    val mediaType: MediaType,
    val downloadUrl: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

