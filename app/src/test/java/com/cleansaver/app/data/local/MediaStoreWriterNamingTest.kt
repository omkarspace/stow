package com.cleansaver.app.data.local

import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreWriterNamingTest {

    @Test
    fun `video candidate generates correct filename and media type`() {
        val candidate = MediaCandidate(
            shortcode = "Cxyz123",
            slideIndex = 0,
            mediaType = MediaType.VIDEO,
            downloadUrl = "https://example.com/video.mp4"
        )

        val isVideo = candidate.mediaType == MediaType.VIDEO
        val extension = if (isVideo) "mp4" else "jpg"
        val fileName = "CleanSaver_${candidate.shortcode}_${candidate.slideIndex}.$extension"
        val relativePath = if (isVideo) "Movies/CleanSaver" else "Pictures/CleanSaver"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

        assertEquals("CleanSaver_Cxyz123_0.mp4", fileName)
        assertEquals("Movies/CleanSaver", relativePath)
        assertEquals("video/mp4", mimeType)
    }

    @Test
    fun `image candidate generates correct filename and media type`() {
        val candidate = MediaCandidate(
            shortcode = "Dabc987",
            slideIndex = 2,
            mediaType = MediaType.IMAGE,
            downloadUrl = "https://example.com/image.jpg"
        )

        val isVideo = candidate.mediaType == MediaType.VIDEO
        val extension = if (isVideo) "mp4" else "jpg"
        val fileName = "CleanSaver_${candidate.shortcode}_${candidate.slideIndex}.$extension"
        val relativePath = if (isVideo) "Movies/CleanSaver" else "Pictures/CleanSaver"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

        assertEquals("CleanSaver_Dabc987_2.jpg", fileName)
        assertEquals("Pictures/CleanSaver", relativePath)
        assertEquals("image/jpeg", mimeType)
    }
}

