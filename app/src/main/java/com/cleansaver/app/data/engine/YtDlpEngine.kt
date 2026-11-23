package com.cleansaver.app.data.engine

import android.content.Context
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.data.local.MediaStoreWriter
import com.cleansaver.app.data.local.SaveOptions
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.CustomThrowable
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.repository.UserPrefsRepository
import com.cleansaver.app.util.ExtractionLogger
import com.cleansaver.app.util.NotificationHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

data class StowFormatOption(
    val id: String,
    val label: String,          // e.g. "1080p · MP4"
    val sizeLabel: String,      // e.g. "28.4 MB" or "Auto"
    val formatSelector: String, // yt-dlp -f selector
    val isAudio: Boolean,
    val audioExt: String = "m4a"
)

data class StowMediaInfo(
    val id: String,
    val title: String,
    val creator: String,
    val platform: String,
    val durationSec: Int,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val description: String?,
    val videoFormats: List<StowFormatOption>,
    val audioFormats: List<StowFormatOption>
) {
    fun formattedDuration(): String {
        if (durationSec <= 0) return ""
        val mins = durationSec / 60
        val secs = durationSec % 60
        return String.format("%d:%02d", mins, secs)
    }
}

class YtDlpEngine(
    private val context: Context,
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val userPrefsRepository: UserPrefsRepository
) {
    private val speedRegex = Regex("""(\d+(?:\.\d+)?\s*[KMG]i?B/s)""", RegexOption.IGNORE_CASE)

    suspend fun fetchMediaInfo(url: String): Result<StowMediaInfo> = withContext(Dispatchers.IO) {
        try {
            ExtractionLogger.log("YT-DLP", "Fetching info: $url")
            val info = YoutubeDL.getInstance().getInfo(url)

            val rawFormats = info.formats.orEmpty()
            val heights = rawFormats
                .filter { (it.height ?: 0) >= 240 && it.vcodec != "none" }
                .groupBy { it.height ?: 0 }
                .entries
                .sortedByDescending { it.key }
                .take(4)

            val videoOptions = mutableListOf<StowFormatOption>()
            if (heights.isEmpty()) {
                videoOptions.add(
                    StowFormatOption(
                        id = "best_mp4",
                        label = "Best Quality · MP4",
                        sizeLabel = "Auto",
                        formatSelector = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                        isAudio = false
                    )
                )
            } else {
                heights.forEach { (height, group) ->
                    val bestInHeight = group.maxByOrNull { it.fileSize.coerceAtLeast(it.fileSizeApproximate) }
                    val sizeBytes = bestInHeight?.let {
                        if (it.fileSize > 0) it.fileSize else it.fileSizeApproximate
                    } ?: 0L
                    videoOptions.add(
                        StowFormatOption(
                            id = "v_$height",
                            label = "${height}p · MP4",
                            sizeLabel = if (sizeBytes > 0) formatFileSize(sizeBytes) else "Auto",
                            formatSelector = "bestvideo[height<=$height][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=$height]+bestaudio/best[height<=$height]/best",
                            isAudio = false
                        )
                    )
                }
            }

            val audioOptions = listOf(
                StowFormatOption(
                    id = "a_m4a",
                    label = "Audio · M4A (Original)",
                    sizeLabel = "Fast",
                    formatSelector = "bestaudio[ext=m4a]/bestaudio",
                    isAudio = true,
                    audioExt = "m4a"
                ),
                StowFormatOption(
                    id = "a_mp3",
                    label = "Audio · MP3 (320 kbps)",
                    sizeLabel = "Universal",
                    formatSelector = "bestaudio/best",
                    isAudio = true,
                    audioExt = "mp3"
                )
            )

            val cleanTitle = info.title?.trim()?.takeIf { it.isNotBlank() } ?: "Untitled Media"
            val creator = info.uploader?.trim()?.takeIf { it.isNotBlank() }
                ?: info.extractor?.trim()
                ?: ""
            val platform = info.extractorKey?.trim()?.takeIf { it.isNotBlank() }
                ?: info.extractor?.trim()
                ?: "Web"

            val mediaInfo = StowMediaInfo(
                id = info.id ?: url.hashCode().toString(),
                title = cleanTitle,
                creator = creator,
                platform = platform,
                durationSec = info.duration,
                thumbnailUrl = info.thumbnail,
                sourceUrl = info.webpageUrl ?: url,
                description = info.description,
                videoFormats = videoOptions,
                audioFormats = audioOptions
            )
            ExtractionLogger.log("YT-DLP", "Resolved: ${mediaInfo.title} ($platform, ${videoOptions.size} video opts)")
            Result.success(mediaInfo)
        } catch (e: Exception) {
            ExtractionLogger.log("YT-DLP", "Fetch error: ${e.message}")
            val msg = e.message.orEmpty()
            val mapped = when {
                msg.contains("Private", ignoreCase = true) || msg.contains("login", ignoreCase = true) ->
                    CustomThrowable(AppError.PrivateAccountNotSupported)
                msg.contains("404", ignoreCase = true) || msg.contains("not found", ignoreCase = true) || msg.contains("unavailable", ignoreCase = true) ->
                    CustomThrowable(AppError.PostNotFound)
                msg.contains("429", ignoreCase = true) || msg.contains("rate", ignoreCase = true) ->
                    CustomThrowable(AppError.NetworkRateLimited)
                msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("Failed to establish", ignoreCase = true) ->
                    CustomThrowable(AppError.NoInternet)
                else -> CustomThrowable(AppError.Unknown(msg.ifBlank { "Unable to fetch media info" }))
            }
            Result.failure(mapped)
        }
    }

    suspend fun download(
        taskId: String,
        mediaInfo: StowMediaInfo,
        format: StowFormatOption,
        embedSubtitles: Boolean,
        embedThumbnail: Boolean,
        onProgress: (percent: Int, speed: String, etaSec: Long) -> Unit
    ): Result<DownloadedMediaEntity> = withContext(Dispatchers.IO) {
        val wifiOnly = userPrefsRepository.wifiOnlyDownloads.first()
        if (wifiOnly && !mediaStoreWriter.isOnWifi()) {
            return@withContext Result.failure(CustomThrowable(AppError.WifiOnlyBlocked))
        }

        val albumName = userPrefsRepository.customAlbumName.first().ifBlank { "Stow" }
        val namingPattern = userPrefsRepository.fileNamingPattern.first()
        val tempDir = File(context.cacheDir, "stow_dl_$taskId").apply { mkdirs() }

        try {
            val request = YoutubeDLRequest(mediaInfo.sourceUrl).apply {
                addOption("-o", "${tempDir.absolutePath}/media.%(ext)s")
                addOption("--no-mtime")
                addOption("--no-playlist")
                if (format.isAudio) {
                    addOption("-f", format.formatSelector)
                    addOption("-x")
                    addOption("--audio-format", format.audioExt)
                    addOption("--audio-quality", "0")
                } else {
                    addOption("-f", format.formatSelector)
                    addOption("--merge-output-format", "mp4")
                }
                if (embedThumbnail) {
                    addOption("--embed-thumbnail")
                    addOption("--add-metadata")
                }
                if (embedSubtitles && !format.isAudio) {
                    addOption("--write-subs")
                    addOption("--embed-subs")
                    addOption("--sub-langs", "en.*")
                }
            }

            ExtractionLogger.log("YT-DLP", "Downloading ${mediaInfo.title} [${format.label}]")
            YoutubeDL.getInstance().execute(request, taskId) { progress, etaInSeconds, line ->
                val pct = progress.toInt().coerceIn(0, 100)
                val speed = speedRegex.find(line)?.groupValues?.getOrNull(1) ?: ""
                onProgress(pct, speed, etaInSeconds)
                NotificationHelper.notifyProgress(context, mediaInfo.title.take(36), pct)
            }

            val outputFile = tempDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("Download completed but no media file was produced")

            val ext = outputFile.extension.lowercase().ifBlank { if (format.isAudio) format.audioExt else "mp4" }
            val mediaType = when {
                format.isAudio || ext in setOf("mp3", "m4a", "opus", "ogg", "wav") -> MediaType.AUDIO
                ext in setOf("jpg", "jpeg", "png", "webp") -> MediaType.IMAGE
                else -> MediaType.VIDEO
            }

            val safeTitle = mediaInfo.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifBlank { mediaInfo.id }
            val displayFileName = if (namingPattern == "CREATOR_DATE" && mediaInfo.creator.isNotBlank()) {
                val creatorClean = mediaInfo.creator.replace(Regex("[^A-Za-z0-9._-]"), "").take(20)
                "${creatorClean}_${safeTitle}.$ext"
            } else {
                "Stow_${safeTitle}.$ext"
            }

            val published = mediaStoreWriter.publishLocalFile(
                sourceFile = outputFile,
                fileName = displayFileName,
                mediaType = mediaType,
                options = SaveOptions(albumName = albumName)
            ).getOrThrow()

            val entity = DownloadedMediaEntity(
                shortcode = mediaInfo.id,
                slideIndex = 0,
                mediaType = mediaType.name,
                mediaStoreUri = published.mediaStoreUri,
                fileName = published.fileName,
                fileSizeBytes = published.fileSize,
                sourceUrl = mediaInfo.sourceUrl,
                createdAtMs = System.currentTimeMillis(),
                title = mediaInfo.title,
                durationSec = mediaInfo.durationSec,
                caption = mediaInfo.description.orEmpty(),
                creatorHandle = mediaInfo.creator,
                platform = mediaInfo.platform.uppercase()
            )
            downloadedMediaDao.upsert(entity)

            val mime = when (mediaType) {
                MediaType.VIDEO -> "video/*"
                MediaType.AUDIO -> "audio/*"
                MediaType.IMAGE -> "image/*"
            }
            NotificationHelper.notifyCompleted(context, mediaInfo.title.take(40), published.mediaStoreUri, mime)
            ExtractionLogger.log("YT-DLP", "Saved ${published.fileName} (${formatFileSize(published.fileSize)})")
            Result.success(entity)
        } catch (e: Exception) {
            NotificationHelper.cancelProgress(context)
            ExtractionLogger.log("YT-DLP", "Download failed: ${e.message}")
            Result.failure(e)
        } finally {
            try { tempDir.deleteRecursively() } catch (_: Exception) {}
        }
    }

    fun cancel(taskId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (_: Exception) {}
    }

    suspend fun updateEngine(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            val msg = when (status) {
                YoutubeDL.UpdateStatus.DONE -> "yt-dlp engine updated to latest!"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp engine is already up to date."
                else -> "yt-dlp engine checked."
            }
            ExtractionLogger.log("YT-DLP", msg)
            Result.success(msg)
        } catch (e: Exception) {
            ExtractionLogger.log("YT-DLP", "Update failed: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format("%.0f KB", kb)
            val mb = kb / 1024.0
            return String.format("%.1f MB", mb)
        }
    }
}
