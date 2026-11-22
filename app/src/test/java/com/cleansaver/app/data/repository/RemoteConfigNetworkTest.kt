package com.cleansaver.app.data.repository

import com.cleansaver.app.data.remote.dto.ParserConfigDto
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class RemoteConfigNetworkTest {

    private lateinit var mockWebServer: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `parses config response from web server correctly`() {
        val sampleJson = """
        {
          "schema_version": 1,
          "min_app_version_code": 1,
          "maintenance_mode": false,
          "maintenance_message": "",
          "config_ttl_hours": 24,
          "user_agents": ["Mozilla/5.0 Test"],
          "url_patterns": {
            "reel_regex": "",
            "post_regex": "",
            "stories_regex": ""
          },
          "strategies": [
            {
              "id": "embed_json_primary",
              "type": "HTTP_EMBED",
              "enabled": true,
              "endpoint_template": "https://instagram.com/p/{shortcode}/",
              "timeout_ms": 5000
            }
          ]
        }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"33a64df551425fcc55e4d42a148795d9f25f89d4\"")
                .setBody(sampleJson)
        )

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/config.json"))
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("\"33a64df551425fcc55e4d42a148795d9f25f89d4\"", response.header("ETag"))

            val body = response.body?.string()
            assertNotNull(body)

            val parsed = json.decodeFromString<ParserConfigDto>(body!!)
            assertEquals(1, parsed.schemaVersion)
            assertEquals("embed_json_primary", parsed.strategies.first().id)
        }
    }

    @Test
    fun `conditional request with If-None-Match yields 304 response`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(304)
        )

        val request = okhttp3.Request.Builder()
            .url(mockWebServer.url("/config.json"))
            .header("If-None-Match", "\"cached_etag_123\"")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(304, response.code)
        }

        val recorded = mockWebServer.takeRequest()
        assertEquals("\"cached_etag_123\"", recorded.getHeader("If-None-Match"))
    }
}

