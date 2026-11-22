package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
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
        val input = "https://www.instagram.com/reel/C1234567890/"
        val result = useCase(input)

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("C1234567890", parsed.shortcode)
        assertEquals(UrlKind.REEL, parsed.kind)
        assertEquals("https://www.instagram.com/reel/C1234567890/", parsed.canonicalUrl)
    }

    @Test
    fun `reel url with tracking query params and caption text is canonicalized`() {
        val input = "Check out this Reel! https://instagram.com/reel/C1234567890/?igsh=NzBmMjdhZWRiYQ== #trending"
        val result = useCase(input)

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("C1234567890", parsed.shortcode)
        assertEquals(UrlKind.REEL, parsed.kind)
        assertEquals("https://www.instagram.com/reel/C1234567890/", parsed.canonicalUrl)
    }

    @Test
    fun `standard post url is parsed correctly`() {
        val input = "https://www.instagram.com/p/B9876543210/"
        val result = useCase(input)

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("B9876543210", parsed.shortcode)
        assertEquals(UrlKind.POST, parsed.kind)
        assertEquals("https://www.instagram.com/p/B9876543210/", parsed.canonicalUrl)
    }

    @Test
    fun `stories url is explicitly rejected`() {
        val input = "https://www.instagram.com/stories/some_user/1234567890/"
        val result = useCase(input)

        assertTrue(result.isFailure)
        val throwable = result.exceptionOrNull() as? CustomThrowable
        assertEquals(AppError.StoriesNotSupported, throwable?.appError)
    }

    @Test
    fun `non instagram url is rejected with InvalidUrl`() {
        val input = "https://example.com/video/12345"
        val result = useCase(input)

        assertTrue(result.isFailure)
        val throwable = result.exceptionOrNull() as? CustomThrowable
        assertEquals(AppError.InvalidUrl, throwable?.appError)
    }

    @Test
    fun `empty input is rejected`() {
        val result = useCase("   ")
        assertTrue(result.isFailure)
    }
}

