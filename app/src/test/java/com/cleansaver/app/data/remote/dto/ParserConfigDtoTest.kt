package com.cleansaver.app.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ParserConfigDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes default config json schema correctly`() {
        val rawJson = """
        {
          "schema_version": 1,
          "min_app_version_code": 1,
          "maintenance_mode": false,
          "maintenance_message": "",
          "config_ttl_hours": 24,
          "user_agents": [
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0"
          ],
          "url_patterns": {
            "reel_regex": "^https?://(www\\.)?instagram\\.com/(reel|reels)/([A-Za-z0-9_-]{5,32})/?.*$",
            "post_regex": "^https?://(www\\.)?instagram\\.com/(p|tv)/([A-Za-z0-9_-]{5,32})/?.*$",
            "stories_regex": "^https?://(www\\.)?instagram\\.com/stories/.*$"
          },
          "strategies": [
            {
              "id": "embed_json_primary",
              "type": "HTTP_EMBED",
              "enabled": true,
              "endpoint_template": "https://www.instagram.com/p/{shortcode}/embed/captioned/",
              "video_regex": "\"video_url\":\"(https?:\\\\/\\\\/[^\"]+)\"",
              "image_regex": "\"display_url\":\"(https?:\\\\/\\\\/[^\"]+)\"",
              "json_script_selector": "script[type='text/javascript']",
              "timeout_ms": 8000
            }
          ]
        }
        """.trimIndent()

        val config = json.decodeFromString<ParserConfigDto>(rawJson)

        assertEquals(1, config.schemaVersion)
        assertEquals(1, config.minAppVersionCode)
        assertFalse(config.maintenanceMode)
        assertEquals(24, config.configTtlHours)
        assertEquals(1, config.userAgents.size)
        assertEquals(1, config.strategies.size)

        val strategy = config.strategies.first()
        assertEquals("embed_json_primary", strategy.id)
        assertEquals("HTTP_EMBED", strategy.type)
        assertEquals(8000L, strategy.timeoutMs)
        assertNotNull(strategy.videoRegex)
    }
}

