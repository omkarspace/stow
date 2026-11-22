package com.cleansaver.app.data.extractor

import com.cleansaver.app.data.remote.dto.StrategyDto
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.model.ParsedUrl
import com.cleansaver.app.domain.model.UrlKind
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmbedHttpExtractorTest {

    private lateinit var server: MockWebServer
    private lateinit var extractor: EmbedHttpExtractor

    private val sampleParsedUrl = ParsedUrl(
        rawUrl = "https://www.instagram.com/reel/TEST12345/",
        canonicalUrl = "https://www.instagram.com/reel/TEST12345/",
        shortcode = "TEST12345",
        kind = UrlKind.REEL
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        extractor = EmbedHttpExtractor(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun strategy(): StrategyDto = StrategyDto(
        id = "embed_json_primary",
        type = "HTTP_EMBED",
        enabled = true,
        endpointTemplate = server.url("/p/{shortcode}/embed/captioned/").toString(),
        videoRegex = """"video_url":"(https?:\\/\\/[^"]+)"""",
        imageRegex = """"display_url":"(https?:\\/\\/[^"]+)"""",
        timeoutMs = 5000
    )

    @Test
    fun `extracts video with matching thumbnail and unescapes query params`() = runTest {
        val html = """
            <html><script>
            {"video_url":"https:\/\/cdn.instagram.com\/v\/reel.mp4?a=1\u0026b=2","display_url":"https:\/\/cdn.instagram.com\/img\/thumb.jpg?x=1&amp;y=2"}
            </script></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val result = extractor.extract(sampleParsedUrl, strategy(), "TestAgent")
        assertTrue(result.isSuccess)

        val candidates = result.getOrThrow()
        assertEquals(1, candidates.size)
        assertEquals(MediaType.VIDEO, candidates[0].mediaType)
        assertEquals("https://cdn.instagram.com/v/reel.mp4?a=1&b=2", candidates[0].downloadUrl)
        assertEquals("https://cdn.instagram.com/img/thumb.jpg?x=1&y=2", candidates[0].thumbnailUrl)
    }

    @Test
    fun `extracts mixed video and image carousel without duplicates`() = runTest {
        val html = """
            <html><script>
            "video_url":"https:\/\/cdn.instagram.com\/v\/slide1.mp4",
            "video_url":"https:\/\/cdn.instagram.com\/v\/slide1.mp4",
            "display_url":"https:\/\/cdn.instagram.com\/img\/thumb1.jpg",
            "display_url":"https:\/\/cdn.instagram.com\/img\/photo2.jpg"
            </script></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val result = extractor.extract(sampleParsedUrl, strategy(), "TestAgent")
        assertTrue(result.isSuccess)

        val candidates = result.getOrThrow()
        assertEquals(2, candidates.size)
        assertEquals(MediaType.VIDEO, candidates[0].mediaType)
        assertEquals(0, candidates[0].slideIndex)
        assertEquals(MediaType.IMAGE, candidates[1].mediaType)
        assertEquals(1, candidates[1].slideIndex)
        assertEquals("https://cdn.instagram.com/img/photo2.jpg", candidates[1].downloadUrl)
    }

    @Test
    fun `falls back to og meta tags when json keys are absent`() = runTest {
        val html = """
            <html><head>
            <meta property="og:video" content="https://cdn.instagram.com/meta_video.mp4?sig=1&amp;v=2" />
            <meta property="og:image" content="https://cdn.instagram.com/meta_thumb.jpg" />
            </head></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val result = extractor.extract(sampleParsedUrl, strategy(), "TestAgent")
        assertTrue(result.isSuccess)

        val candidates = result.getOrThrow()
        assertEquals(1, candidates.size)
        assertEquals("https://cdn.instagram.com/meta_video.mp4?sig=1&v=2", candidates[0].downloadUrl)
        assertEquals("https://cdn.instagram.com/meta_thumb.jpg", candidates[0].thumbnailUrl)
    }

    @Test
    fun `detects private account html response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>This Account is Private</html>"))

        val result = extractor.extract(sampleParsedUrl, strategy(), "TestAgent")
        assertTrue(result.isFailure)
        val err = (result.exceptionOrNull() as? CustomThrowable)?.appError
        assertEquals(AppError.PrivateAccountNotSupported, err)
    }

    @Test
    fun `maps 404 to PostNotFound and 429 to NetworkRateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val notFound = extractor.extract(sampleParsedUrl, strategy(), "TestAgent")
        assertEquals(AppError.PostNotFound, (notFound.exceptionOrNull() as? CustomThrowable)?.appError)

        server.enqueue(MockResponse().setResponseCode(429))
        val rateLimited = extractor.extract(sampleParsedUrl, strategy(), "TestAgent")
        assertEquals(AppError.NetworkRateLimited, (rateLimited.exceptionOrNull() as? CustomThrowable)?.appError)
    }
}
