package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.Platform
import com.cleansaver.app.domain.model.UrlKind
import java.util.regex.Pattern

class ValidateUrlUseCase {

    private val extractUrlPattern = Pattern.compile("https?://\\S+")
    private val storiesPattern = Pattern.compile(
        "^https?://(www\\.|m\\.)?(instagram\\.com|instagr\\.am)/stories/.*",
        Pattern.CASE_INSENSITIVE
    )
    private val igCanonicalPattern = Pattern.compile(
        "^https?://(?:www\\.|m\\.)?(?:instagram\\.com|instagr\\.am)/(?:(?:share|[A-Za-z0-9._]{1,30})/)?(reel|reels|p|tv)/([A-Za-z0-9_-]{5,32})(?:/|\\?|$).*",
        Pattern.CASE_INSENSITIVE
    )
    private val igOpaqueSharePattern = Pattern.compile(
        "^https?://(?:www\\.|m\\.)?(?:instagram\\.com|instagr\\.am)/share/([A-Za-z0-9_-]{5,40})(?:/|\\?|$).*",
        Pattern.CASE_INSENSITIVE
    )
    private val threadsPattern = Pattern.compile(
        "^https?://(?:www\\.)?threads\\.(?:net|com)/(?:@([A-Za-z0-9._]+)/)?post/([A-Za-z0-9_-]{5,32})(?:/|\\?|$).*",
        Pattern.CASE_INSENSITIVE
    )
    private val xTwitterPattern = Pattern.compile(
        "^https?://(?:www\\.|mobile\\.)?(?:x\\.com|twitter\\.com)/([A-Za-z0-9_]{1,30})/status/([0-9]{5,25})(?:/|\\?|$).*",
        Pattern.CASE_INSENSITIVE
    )
    private val redditPattern = Pattern.compile(
        "^https?://(?:www\\.|old\\.|m\\.)?reddit\\.com/r/([A-Za-z0-9_]+)/comments/([A-Za-z0-9]+)/?.*",
        Pattern.CASE_INSENSITIVE
    )
    private val pinterestPattern = Pattern.compile(
        "^https?://(?:www\\.|[a-z]{2}\\.)?(?:pinterest\\.[a-z.]+/pin/([0-9A-Za-z_-]+)|pin\\.it/([0-9A-Za-z_-]+))(?:/|\\?|$).*",
        Pattern.CASE_INSENSITIVE
    )

    fun extractAllUrls(input: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = extractUrlPattern.matcher(input)
        while (matcher.find()) {
            val cleaned = matcher.group(0)
                .trimEnd('.', ',', ')', ']', '!', '?', ';', '"', '\'')
            if (cleaned.isNotBlank()) urls.add(cleaned)
        }
        return urls.distinct()
    }

    operator fun invoke(input: String): Result<ParsedUrl> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.failure(CustomThrowable(AppError.InvalidUrl))

        val matcher = extractUrlPattern.matcher(trimmed)
        val rawUrl = (if (matcher.find()) matcher.group(0) else trimmed)
            .trimEnd('.', ',', ')', ']', '!', '?', ';', '"', '\'')

        if (storiesPattern.matcher(rawUrl).matches()) {
            return Result.failure(CustomThrowable(AppError.StoriesNotSupported))
        }

        // 1. Instagram standard / username / share/reel
        val igMatcher = igCanonicalPattern.matcher(rawUrl)
        if (igMatcher.matches()) {
            val typeSegment = igMatcher.group(1)?.lowercase() ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
            val shortcode = igMatcher.group(2) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
            val kind = if (typeSegment == "reel" || typeSegment == "reels") UrlKind.REEL else UrlKind.POST
            val normalizedSegment = if (typeSegment == "reels") "reel" else typeSegment
            return Result.success(
                ParsedUrl(
                    rawUrl = rawUrl,
                    canonicalUrl = "https://www.instagram.com/$normalizedSegment/$shortcode/",
                    shortcode = shortcode,
                    kind = kind,
                    platform = Platform.INSTAGRAM
                )
            )
        }

        // 2. Instagram opaque share link
        val igOpaqueMatcher = igOpaqueSharePattern.matcher(rawUrl)
        if (igOpaqueMatcher.matches()) {
            val shareToken = igOpaqueMatcher.group(1) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
            return Result.success(
                ParsedUrl(
                    rawUrl = rawUrl,
                    canonicalUrl = "https://www.instagram.com/share/$shareToken/",
                    shortcode = shareToken,
                    kind = UrlKind.REEL,
                    platform = Platform.INSTAGRAM
                )
            )
        }

        // 3. Threads
        val threadsMatcher = threadsPattern.matcher(rawUrl)
        if (threadsMatcher.matches()) {
            val user = threadsMatcher.group(1)
            val code = threadsMatcher.group(2) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
            val canonical = if (!user.isNullOrBlank()) {
                "https://www.threads.net/@$user/post/$code"
            } else {
                "https://www.threads.net/t/$code"
            }
            return Result.success(
                ParsedUrl(
                    rawUrl = rawUrl,
                    canonicalUrl = canonical,
                    shortcode = code,
                    kind = UrlKind.POST,
                    platform = Platform.THREADS
                )
            )
        }

        // 4. X / Twitter
        val xMatcher = xTwitterPattern.matcher(rawUrl)
        if (xMatcher.matches()) {
            val user = xMatcher.group(1) ?: "i"
            val statusId = xMatcher.group(2) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
            return Result.success(
                ParsedUrl(
                    rawUrl = rawUrl,
                    canonicalUrl = "https://x.com/$user/status/$statusId",
                    shortcode = statusId,
                    kind = UrlKind.POST,
                    platform = Platform.X_TWITTER
                )
            )
        }

        // 5. Reddit
        val redditMatcher = redditPattern.matcher(rawUrl)
        if (redditMatcher.matches()) {
            val sub = redditMatcher.group(1) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
            val postId = redditMatcher.group(2) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
            return Result.success(
                ParsedUrl(
                    rawUrl = rawUrl,
                    canonicalUrl = "https://www.reddit.com/r/$sub/comments/$postId/",
                    shortcode = postId,
                    kind = UrlKind.POST,
                    platform = Platform.REDDIT
                )
            )
        }

        // 6. Pinterest
        val pinMatcher = pinterestPattern.matcher(rawUrl)
        if (pinMatcher.matches()) {
            val pinId = pinMatcher.group(1) ?: pinMatcher.group(2) ?: return Result.failure(CustomThrowable(AppError.InvalidUrl))
            return Result.success(
                ParsedUrl(
                    rawUrl = rawUrl,
                    canonicalUrl = rawUrl.substringBefore("?"),
                    shortcode = pinId,
                    kind = UrlKind.POST,
                    platform = Platform.PINTEREST
                )
            )
        }

        return Result.failure(CustomThrowable(AppError.InvalidUrl))
    }
}
