package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.Platform
import com.cleansaver.app.domain.model.UrlKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateUrlUseCaseTest {

    private lateinit var useCase: ValidateUrlUseCase

    @Before
    fun setUp() {
        useCase = ValidateUrlUseCase()
    }

    @Test
    fun `standard reel url is parsed correctly`() {
        val result = useCase("https://www.instagram.com/reel/C1234567890/")
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("C1234567890", parsed.shortcode)
        assertEquals(UrlKind.REEL, parsed.kind)
        assertEquals(Platform.INSTAGRAM, parsed.platform)
        assertEquals("https://www.instagram.com/reel/C1234567890/", parsed.canonicalUrl)
    }

    @Test
    fun `reel url with tracking query params and caption text is canonicalized`() {
        val result = useCase("Check out this Reel! https://instagram.com/reel/C1234567890/?igsh=NzBmMjdhZWRiYQ== #trending")
        assertTrue(result.isSuccess)
        assertEquals("C1234567890", result.getOrThrow().shortcode)
    }

    @Test
    fun `username prefixed reel and post urls are parsed correctly`() {
        val reelResult = useCase("https://www.instagram.com/natgeo/reel/C9988776655/?utm_source=ig_web_copy_link")
        assertTrue(reelResult.isSuccess)
        assertEquals("C9988776655", reelResult.getOrThrow().shortcode)
        assertEquals(UrlKind.REEL, reelResult.getOrThrow().kind)

        val postResult = useCase("https://www.instagram.com/design.daily/p/B1122334455/")
        assertTrue(postResult.isSuccess)
        assertEquals("B1122334455", postResult.getOrThrow().shortcode)
        assertEquals(UrlKind.POST, postResult.getOrThrow().kind)
    }

    @Test
    fun `mobile, short domain, reels, tv, and share urls are supported`() {
        assertEquals("AbCdEf12345", useCase("https://m.instagram.com/reels/AbCdEf12345/").getOrThrow().shortcode)
        assertEquals("XyZ98765432", useCase("https://instagr.am/p/XyZ98765432/").getOrThrow().shortcode)
        assertEquals("TvCode12345", useCase("https://www.instagram.com/tv/TvCode12345/").getOrThrow().shortcode)
        assertEquals("ShArE123456", useCase("https://www.instagram.com/share/reel/ShArE123456/").getOrThrow().shortcode)
        assertEquals("BA1234567890", useCase("https://www.instagram.com/share/BA1234567890/").getOrThrow().shortcode)
    }

    @Test
    fun `multi-platform urls for threads, x, reddit, and pinterest are parsed`() {
        val threads = useCase("https://www.threads.net/@zuck/post/C12345ABCDE").getOrThrow()
        assertEquals(Platform.THREADS, threads.platform)
        assertEquals("C12345ABCDE", threads.shortcode)

        val xPost = useCase("https://x.com/AndroidDev/status/1840000000000000000").getOrThrow()
        assertEquals(Platform.X_TWITTER, xPost.platform)
        assertEquals("1840000000000000000", xPost.shortcode)

        val reddit = useCase("https://www.reddit.com/r/androiddev/comments/1abc2de/cool_post/").getOrThrow()
        assertEquals(Platform.REDDIT, reddit.platform)
        assertEquals("1abc2de", reddit.shortcode)

        val pin = useCase("https://www.pinterest.com/pin/123456789012345/").getOrThrow()
        assertEquals(Platform.PINTEREST, pin.platform)
        assertEquals("123456789012345", pin.shortcode)
    }

    @Test
    fun `extractAllUrls finds multiple links for batch queue`() {
        val input = """
            https://www.instagram.com/reel/AAA11111/
            https://x.com/user/status/1234567890
            https://www.instagram.com/reel/AAA11111/
        """.trimIndent()
        val urls = useCase.extractAllUrls(input)
        assertEquals(2, urls.size)
    }

    @Test
    fun `trailing punctuation around url is stripped`() {
        val result = useCase("Look at this (https://www.instagram.com/p/B9876543210/).")
        assertTrue(result.isSuccess)
        assertEquals("B9876543210", result.getOrThrow().shortcode)
    }

    @Test
    fun `stories url is explicitly rejected`() {
        val result = useCase("https://www.instagram.com/stories/some_user/1234567890/")
        assertTrue(result.isFailure)
        val throwable = result.exceptionOrNull() as? CustomThrowable
        assertEquals(AppError.StoriesNotSupported, throwable?.appError)
    }

    @Test
    fun `non supported url is rejected with InvalidUrl`() {
        val result = useCase("https://example.com/video/12345")
        assertTrue(result.isFailure)
        val throwable = result.exceptionOrNull() as? CustomThrowable
        assertEquals(AppError.InvalidUrl, throwable?.appError)
    }

    @Test
    fun `empty input is rejected`() {
        assertTrue(useCase("   ").isFailure)
    }
}
