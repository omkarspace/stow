package com.cleansaver.app.data.local

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.MediaStore
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedFileMeta(val mediaStoreUri: String, val fileName: String, val fileSize: Long)

data class SaveOptions(
    val wifiOnly: Boolean = false,
    val fileNamingPattern: String = "DEFAULT", // "DEFAULT" | "CREATOR_DATE"
    val albumName: String = "Stow"
)

class MediaStoreWriter(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val CDN_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        fun buildFileName(candidate: MediaCandidate, pattern: String): String {
            val extension = when (candidate.mediaType) {
                MediaType.VIDEO -> "mp4"
                MediaType.IMAGE -> "jpg"
                MediaType.AUDIO -> "m4a"
            }
            if (pattern == "CREATOR_DATE") {
                val creator = candidate.creatorHandle
                    ?.replace(Regex("[^A-Za-z0-9._-]"), "")
                    ?.take(24)
                    ?.takeIf { it.isNotBlank() }
                    ?: candidate.platform.displayName
                val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                return "${creator}_${date}_${candidate.shortcode}_${candidate.slideIndex}.$extension"
            }
            return "Stow_${candidate.shortcode}_${candidate.slideIndex}.$extension"
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    suspend fun downloadToGallery(
        candidate: MediaCandidate,
        options: SaveOptions = SaveOptions(),
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<SavedFileMeta> = withContext(Dispatchers.IO) {
        if (options.wifiOnly && !isOnWifi()) {
            return@withContext Result.failure(CustomThrowable(AppError.WifiOnlyBlocked))
        }

        if (candidate.mediaType == MediaType.AUDIO) {
            return@withContext downloadAndExtractAudio(candidate, options, onProgress)
        }

        if (candidate.mediaType == MediaType.VIDEO && !candidate.audioUrl.isNullOrBlank()) {
            return@withContext downloadAndMuxDashVideo(candidate, options, onProgress)
        }

        val isVideo = candidate.mediaType == MediaType.VIDEO
        val collection: Uri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val album = options.albumName.ifBlank { "CleanSaver" }
        val fileName = buildFileName(candidate, options.fileNamingPattern)
        val relativePath = if (isVideo) "Movies/$album" else "Pictures/$album"
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

                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(pendingUri, publishValues, null, null)

                Result.success(SavedFileMeta(pendingUri.toString(), fileName, bytesReadTotal))
            }
        } catch (e: Throwable) {
            try { contentResolver.delete(pendingUri, null, null) } catch (_: Exception) {}
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun downloadAndExtractAudio(
        candidate: MediaCandidate,
        options: SaveOptions,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<SavedFileMeta> {
        val tempMp4 = File(context.cacheDir, "cs_tmp_${System.currentTimeMillis()}.mp4")
        val tempM4a = File(context.cacheDir, "cs_tmp_${System.currentTimeMillis()}.m4a")
        val contentResolver = context.contentResolver
        var pendingUri: Uri? = null

        return try {
            val request = Request.Builder()
                .url(candidate.downloadUrl)
                .header("User-Agent", CDN_USER_AGENT)
                .header("Referer", "https://www.instagram.com/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP server returned error code: ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Response body is null")
                val totalBytes = body.contentLength()
                val buffer = ByteArray(16384)
                var bytesReadTotal = 0L
                var lastPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempMp4).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesReadTotal += read
                            if (totalBytes > 0) {
                                // Reserve 0..85% for download, 85..100% for audio remux
                                val percent = ((bytesReadTotal * 85) / totalBytes).toInt().coerceIn(0, 85)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(bytesReadTotal, totalBytes, percent)
                                }
                            }
                        }
                    }
                }
            }

            val extracted = AudioTrackExtractor.extractAudioToM4a(tempMp4, tempM4a)
            if (!extracted) {
                throw IllegalStateException("No compatible audio track found in video")
            }
            onProgress(tempM4a.length(), tempM4a.length(), 95)

            val album = options.albumName.ifBlank { "CleanSaver" }
            val fileName = buildFileName(candidate, options.fileNamingPattern)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/$album")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            pendingUri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create audio MediaStore entry")

            FileInputStream(tempM4a).use { input ->
                contentResolver.openOutputStream(pendingUri!!)?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("Failed to open audio output stream")
            }

            contentResolver.update(
                pendingUri!!,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            onProgress(tempM4a.length(), tempM4a.length(), 100)

            Result.success(SavedFileMeta(pendingUri.toString(), fileName, tempM4a.length()))
        } catch (e: Throwable) {
            pendingUri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            try { tempMp4.delete() } catch (_: Exception) {}
            try { tempM4a.delete() } catch (_: Exception) {}
        }
    }

    private fun downloadAndMuxDashVideo(
        candidate: MediaCandidate,
        options: SaveOptions,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit
    ): Result<SavedFileMeta> {
        val ts = System.currentTimeMillis()
        val tempVideo = File(context.cacheDir, "cs_vid_$ts.mp4")
        val tempAudio = File(context.cacheDir, "cs_aud_$ts.mp4")
        val tempMuxed = File(context.cacheDir, "cs_mux_$ts.mp4")
        val contentResolver = context.contentResolver
        var pendingUri: Uri? = null

        return try {
            // 1. Download video stream (0..75%)
            val vidReq = Request.Builder().url(candidate.downloadUrl).header("User-Agent", CDN_USER_AGENT).build()
            okHttpClient.newCall(vidReq).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Video HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Empty video body")
                val total = body.contentLength()
                val buf = ByteArray(16384)
                var readTotal = 0L
                var lastPct = -1
                body.byteStream().use { input ->
                    FileOutputStream(tempVideo).use { out ->
                        var r: Int
                        while (input.read(buf).also { r = it } != -1) {
                            out.write(buf, 0, r)
                            readTotal += r
                            if (total > 0) {
                                val pct = ((readTotal * 75) / total).toInt().coerceIn(0, 75)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(readTotal, total, pct)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Download companion audio stream (best-effort; some Reddit videos are silent)
            var hasAudio = false
            val audioUrls = listOfNotNull(
                candidate.audioUrl,
                candidate.audioUrl?.replace("DASH_AUDIO_128.mp4", "DASH_audio.mp4")
            ).distinct()
            for (aUrl in audioUrls) {
                try {
                    val audReq = Request.Builder().url(aUrl).header("User-Agent", CDN_USER_AGENT).build()
                    okHttpClient.newCall(audReq).execute().use { resp ->
                        if (resp.isSuccessful && resp.body != null) {
                            resp.body!!.byteStream().use { input ->
                                FileOutputStream(tempAudio).use { out -> input.copyTo(out) }
                            }
                            if (tempAudio.length() > 0) hasAudio = true
                        }
                    }
                    if (hasAudio) break
                } catch (_: Exception) {}
            }
            onProgress(tempVideo.length(), tempVideo.length(), 88)

            // 3. Mux video + audio via native MediaMuxer, or fall back to video-only
            val finalFile = if (hasAudio && AudioTrackExtractor.muxVideoAndAudio(tempVideo, tempAudio, tempMuxed)) {
                tempMuxed
            } else {
                tempVideo
            }

            val album = options.albumName.ifBlank { "CleanSaver" }
            val fileName = buildFileName(candidate, options.fileNamingPattern)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/$album")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            pendingUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create MediaStore video entry")

            FileInputStream(finalFile).use { input ->
                contentResolver.openOutputStream(pendingUri!!)?.use { out ->
                    input.copyTo(out)
                } ?: throw IllegalStateException("Failed to open video output stream")
            }

            contentResolver.update(
                pendingUri!!,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            onProgress(finalFile.length(), finalFile.length(), 100)
            Result.success(SavedFileMeta(pendingUri.toString(), fileName, finalFile.length()))
        } catch (e: Throwable) {
            pendingUri?.let { try { contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            if (e is CancellationException) throw e
            Result.failure(e)
        } finally {
            try { tempVideo.delete() } catch (_: Exception) {}
            try { tempAudio.delete() } catch (_: Exception) {}
            try { tempMuxed.delete() } catch (_: Exception) {}
        }
    }
}
