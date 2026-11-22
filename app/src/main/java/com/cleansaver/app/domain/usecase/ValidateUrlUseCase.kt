package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.UrlKind
import java.util.regex.Pattern

class ValidateUrlUseCase {

    private val extractUrlPattern = Pattern.compile("https?://\\S+")
    private val canonicalPattern = Pattern.compile("^https?://(www\\.)?instagram\\.com/(reel|reels|p|tv)/([A-Za-z0-9_-]{5,32})/?.*$")
    private val storiesPattern = Pattern.compile("^https?://(www\\.)?instagram\\.com/stories/.*$")

    operator fun invoke(input: String): Result<ParsedUrl> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Input is empty"))

        // Extract first URL if text contains surrounding caption/hashtags
        val matcher = extractUrlPattern.matcher(trimmed)
        val rawUrl = if (matcher.find()) matcher.group(0) else trimmed

        if (storiesPattern.matcher(rawUrl).matches()) {
            return Result.failure(CustomThrowable(AppError.StoriesNotSupported))
        }

        val canonicalMatcher = canonicalPattern.matcher(rawUrl)
        if (!canonicalMatcher.matches()) {
            return Result.failure(CustomThrowable(AppError.InvalidUrl))
        }

        val typeSegment = canonicalMatcher.group(2)
        val shortcode = canonicalMatcher.group(3) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))

        val kind = when (typeSegment) {
            "reel", "reels" -> UrlKind.REEL
            "p", "tv" -> UrlKind.POST
            else -> UrlKind.POST
        }

        val canonicalUrl = "https://www.instagram.com/$typeSegment/$shortcode/"
        return Result.success(ParsedUrl(rawUrl = rawUrl, canonicalUrl = canonicalUrl, shortcode = shortcode, kind = kind))
    }
}

