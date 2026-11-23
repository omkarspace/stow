package com.cleansaver.app.util

import android.content.Context
import android.content.Intent
import com.cleansaver.app.data.local.DownloadedMediaEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LibraryBackupEntry(
    val shortcode: String,
    val platform: String,
    val mediaType: String,
    val fileName: String,
    val creatorHandle: String,
    val caption: String,
    val sourceUrl: String,
    val isFavorite: Boolean,
    val tag: String,
    val createdAtMs: Long
)

object LibraryBackupManager {
    private val prettyJson = Json { prettyPrint = true }

    fun exportToJson(items: List<DownloadedMediaEntity>): String {
        val payload = items.map {
            LibraryBackupEntry(
                shortcode = it.shortcode,
                platform = it.platform,
                mediaType = it.mediaType,
                fileName = it.fileName,
                creatorHandle = it.creatorHandle,
                caption = it.caption,
                sourceUrl = it.sourceUrl,
                isFavorite = it.isFavorite,
                tag = it.tag,
                createdAtMs = it.createdAtMs
            )
        }
        return prettyJson.encodeToString(payload)
    }

    fun shareBackupJson(context: Context, items: List<DownloadedMediaEntity>) {
        val jsonText = exportToJson(items)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Stow Library Backup (${items.size} items)")
            putExtra(Intent.EXTRA_TEXT, jsonText)
        }
        context.startActivity(Intent.createChooser(intent, "Export Stow Library JSON"))
    }
}

