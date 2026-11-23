package com.cleansaver.app.util

import com.cleansaver.app.data.local.MediaStoreWriter
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureUtilsTest {

    @Test
    fun `semver comparison detects newer github release tags`() {
        assertTrue(AppUpdateChecker.isNewerVersion("v1.1.0", "1.0.0"))
        assertTrue(AppUpdateChecker.isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.0.1", "1.0.0"))
        assertFalse(AppUpdateChecker.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateChecker.isNewerVersion("v0.9.9", "1.0.0"))
    }

    @Test
    fun `candidate extracts unique hashtags from caption`() {
        val candidate = MediaCandidate(
            shortcode = "ABC123",
            slideIndex = 0,
            mediaType = MediaType.VIDEO,
            downloadUrl = "https://cdn.example.com/v.mp4",
            caption = "Minimal desk setup #minimal #design #minimal #android_dev"
        )
        assertEquals(listOf("#minimal", "#design", "#android_dev"), candidate.extractHashtags())
    }

    @Test
    fun `file naming supports default, creator_date, and audio m4a`() {
        val video = MediaCandidate(
            shortcode = "XYZ789",
            slideIndex = 0,
            mediaType = MediaType.VIDEO,
            downloadUrl = "https://cdn.example.com/v.mp4",
            creatorHandle = "natgeo",
            platform = Platform.INSTAGRAM
        )
        assertEquals("CleanSaver_XYZ789_0.mp4", MediaStoreWriter.buildFileName(video, "DEFAULT"))
        val creatorName = MediaStoreWriter.buildFileName(video, "CREATOR_DATE")
        assertTrue(creatorName.startsWith("natgeo_"))
        assertTrue(creatorName.endsWith("_XYZ789_0.mp4"))

        val audio = video.copy(mediaType = MediaType.AUDIO)
        assertEquals("CleanSaver_XYZ789_0.m4a", MediaStoreWriter.buildFileName(audio, "DEFAULT"))
    }
}
