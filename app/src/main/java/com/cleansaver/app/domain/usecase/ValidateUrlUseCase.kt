package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.UrlKind
import java.util.regex.Pattern

class ValidateUrlUseCase {

    private val extractUrlPattern = Pattern.compile("https?://\\S+")
    private val storiesPattern = Pattern.compile(
        "^https?://(www\\.|m\\.)?(instagram\\.com|instagr\\.am)/stories/.*",
        Pattern.CASE_INSENSITIVE
    )
    // Supports:
    // - instagram.com/reel/{code}
    // - instagram.com/reels/{code}
    // - instagram.com/p/{code}
    // - instagram.com/tv/{code}
    // - instagram.com/{username}/reel/{code}
    // - instagram.com/{username}/p/{code}
    // - instagram.com/share/reel/{code} or /share/p/{code}
    // - m.instagram.com/... and instagr.am/...
    private val canonicalPattern = Pattern.compile(
        "^https?://(?:www\\.|m\\.)?(?:instagram\\.com|instagr\\.am)/(?:(?:share|[A-Za-z0-9._]{1,30})/)?(reel|reels|p|tv)/([A-Za-z0-9_-]{5,32})(?:/|\\?|$).*",
        Pattern.CASE_INSENSITIVE
    )

    operator fun invoke(input: String): Result<ParsedUrl> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.failure(CustomThrowable(AppError.InvalidUrl))

        // Extract first URL if text contains surrounding caption/hashtags, and strip trailing punctuation
        val matcher = extractUrlPattern.matcher(trimmed)
        val rawUrl = (if (matcher.find()) matcher.group(0) else trimmed)
            .trimEnd('.', ',', ')', ']', '!', '?', ';', '"', '\'')

        if (storiesPattern.matcher(rawUrl).matches()) {
            return Result.failure(CustomThrowable(AppError.StoriesNotSupported))
        }

        val canonicalMatcher = canonicalPattern.matcher(rawUrl)
        if (!canonicalMatcher.matches()) {
            return Result.failure(CustomThrowable(AppError.InvalidUrl))
        }

        val typeSegment = canonicalMatcher.group(1)?.lowercase()
            ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
        val shortcode = canonicalMatcher.group(2)
            ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))

        val kind = when (typeSegment) {
            "reel", "reels" -> UrlKind.REEL
            "p", "tv" -> UrlKind.POST
            else -> UrlKind.POST
        }

        val normalizedSegment = if (typeSegment == "reels") "reel" else typeSegment
        val canonicalUrl = "https://www.instagram.com/$normalizedSegment/$shortcode/"
        return Result.success(
            ParsedUrl(
                rawUrl = rawUrl,
                canonicalUrl = canonicalUrl,
                shortcode = shortcode,
                kind = kind
            )
        )
    }
}
