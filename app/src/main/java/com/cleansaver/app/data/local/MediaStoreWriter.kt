package com.cleansaver.app.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream

data class SavedFileMeta(val mediaStoreUri: String, val fileName: String, val fileSize: Long)

class MediaStoreWriter(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val CDN_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    suspend fun downloadToGallery(
        candidate: MediaCandidate,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<SavedFileMeta> = withContext(Dispatchers.IO) {
        val isVideo = candidate.mediaType == MediaType.VIDEO
        val collection: Uri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val extension = if (isVideo) "mp4" else "jpg"
        val fileName = "CleanSaver_${candidate.shortcode}_${candidate.slideIndex}.$extension"
        val relativePath = if (isVideo) "Movies/CleanSaver" else "Pictures/CleanSaver"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val contentResolver = context.contentResolver
        val pendingUri = contentResolver.insert(collection, contentValues)
            ?: return@withContext Result.failure(IllegalStateException("Failed to create MediaStore entry"))

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            val request = Request.Builder()
                .url(candidate.downloadUrl)
                .header("User-Agent", CDN_USER_AGENT)
                .header("Referer", "https://www.instagram.com/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP server returned error code: ${response.code}")
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (isVideo && (contentType.startsWith("image/") || contentType.startsWith("text/html"))) {
                    throw IllegalStateException("Expected video stream but received $contentType")
                }

                val body = response.body ?: throw IllegalStateException("Response body is null")
                val totalBytes = body.contentLength()
                inputStream = body.byteStream()
                outputStream = contentResolver.openOutputStream(pendingUri)
                    ?: throw IllegalStateException("Failed to open output stream for URI: $pendingUri")

                val buffer = ByteArray(16384)
                var bytesReadTotal = 0L
                var read: Int
                var lastReportedPercent = -1

                while (inputStream!!.read(buffer).also { read = it } != -1) {
                    outputStream!!.write(buffer, 0, read)
                    bytesReadTotal += read

                    if (totalBytes > 0) {
                        val percent = ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            onProgress(bytesReadTotal, totalBytes, percent)
                        }
                    } else if (bytesReadTotal % (64 * 1024) < 16384) {
                        onProgress(bytesReadTotal, -1L, 0)
                    }
                }
                outputStream!!.flush()

                // Finalize entry creation: Toggle IS_PENDING to 0
                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(pendingUri, publishValues, null, null)

                Result.success(SavedFileMeta(pendingUri.toString(), fileName, bytesReadTotal))
            }
        } catch (e: Throwable) {
            // Guarantee atomic cleanup: Purge partial file from MediaStore on failure
            try {
                contentResolver.delete(pendingUri, null, null)
            } catch (_: Exception) {}
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }
}
