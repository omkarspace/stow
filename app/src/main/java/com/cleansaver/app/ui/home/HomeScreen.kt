package com.cleansaver.app.ui.home

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.ui.components.MediaPreviewDialog
import com.cleansaver.app.ui.components.StowActionButton
import com.cleansaver.app.ui.components.StowDarkBadge
import com.cleansaver.app.ui.components.StowDarkBlock
import com.cleansaver.app.ui.components.StowLavenderBadge
import com.cleansaver.app.ui.components.StowSegmentedProgressBar
import com.cleansaver.app.ui.components.StowStatTile
import com.cleansaver.app.ui.components.StowTopBarBlock
import com.cleansaver.app.ui.library.LibraryViewModel
import com.cleansaver.app.ui.theme.ErrorRedDark
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
import com.cleansaver.app.ui.theme.StowPineElevated
import com.cleansaver.app.ui.theme.StowPineMuted
import com.cleansaver.app.ui.theme.SuccessGreenDark

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val libraryState by libraryViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var previewCandidate by remember { mutableStateOf<MediaCandidate?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. BRIK-style Top Brand Wordmark Block
        StowTopBarBlock(
            badgeText = if (libraryState.totalCount > 0) "${libraryState.totalCount} STOWED" else "ON-DEVICE"
        )

        // 2. Hero Input Modular Dark Block
        StowDarkBlock {
            Text(
                text = "Stow any\npublic media.",
                style = MaterialTheme.typography.headlineLarge,
                color = StowInkOnDark
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Instagram · Threads · X · Reddit · Pinterest · TikTok · Shorts",
                style = MaterialTheme.typography.bodySmall,
                color = StowInkMutedOnDark
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = state.inputUrl,
                onValueChange = { viewModel.onUrlChanged(it) },
                placeholder = {
                    Text(
                        text = "Paste link(s), one per line...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StowInkMutedOnDark
                    )
                },
                trailingIcon = {
                    if (state.inputUrl.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onUrlChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = StowInkMutedOnDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            if (clipboard?.hasPrimaryClip() == true &&
                                clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                            ) {
                                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                viewModel.onPaste(text)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = StowLavender,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = StowInkOnDark,
                    unfocusedTextColor = StowInkOnDark,
                    cursorColor = StowLavender,
                    focusedBorderColor = StowLavender,
                    unfocusedBorderColor = StowPineMuted,
                    focusedContainerColor = StowPineElevated,
                    unfocusedContainerColor = StowPineElevated
                )
            )
        }

        // 3. Light-Canvas Contrast Action Strip (matches BRIK "New challenge + [START]" band)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isExtracting || state.isValidating) "Processing" else "Ready to stow",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                StowDarkBadge(text = if (state.isExtracting) "PARSING" else "ZERO ADS · LOCAL")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.extractionStatus
                        ?: if (state.inputUrl.isBlank()) "Paste or share a link" else "Tap extract to preview",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (state.isValidating || state.isExtracting) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StowPineDark,
                    modifier = Modifier.height(48.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = StowLavender,
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else {
                StowActionButton(
                    text = "Extract",
                    onClick = { viewModel.onExtract() },
                    enabled = state.inputUrl.isNotBlank()
                )
            }
        }

        // Action Banner
        state.actionBannerMessage?.let { msg ->
            StowDarkBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = StowLavender,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearBanner() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Dismiss", tint = StowInkMutedOnDark, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Error Block
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            state.error?.let { err ->
                StowDarkBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRedDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Unable to extract",
                            style = MaterialTheme.typography.titleMedium,
                            color = StowInkOnDark
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatAppError(err),
                        style = MaterialTheme.typography.bodySmall,
                        color = StowInkMutedOnDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.onExtract() }) {
                            Text("RETRY", color = StowLavender, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("DISMISS", color = StowInkMutedOnDark, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 4. Extracted Media Modular Blocks
        AnimatedVisibility(
            visible = state.extractedCandidates.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.extractedCandidates.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.selectedIndices.size} of ${state.extractedCandidates.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(
                            onClick = {
                                if (state.selectedIndices.size == state.extractedCandidates.size) {
                                    viewModel.deselectAllCandidates()
                                } else {
                                    viewModel.selectAllCandidates()
                                }
                            }
                        ) {
                            Text(
                                text = if (state.selectedIndices.size == state.extractedCandidates.size) "DESELECT ALL" else "SELECT ALL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                state.extractedCandidates.forEachIndexed { index, candidate ->
                    val isSelected = state.selectedIndices.contains(index)
                    val progress = state.downloadProgressMap[candidate.slideIndex]

                    StowDarkBlock(
                        modifier = Modifier.clickable {
                            if (state.extractedCandidates.size > 1) {
                                viewModel.toggleCandidateSelection(index)
                            } else {
                                previewCandidate = candidate
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.extractedCandidates.size > 1) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleCandidateSelection(index) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = StowLavender,
                                        checkmarkColor = StowPineDark,
                                        uncheckedColor = StowInkMutedOnDark
                                    )
                                )
                            }

                            StowLavenderBadge(
                                text = "${candidate.platform.displayName} · ${if (candidate.mediaType == MediaType.VIDEO) "VIDEO" else "PHOTO"}"
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = candidate.creatorHandle?.let { "@$it" } ?: candidate.shortcode,
                                style = MaterialTheme.typography.bodySmall,
                                color = StowInkMutedOnDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(onClick = { previewCandidate = candidate }) {
                                Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                            }
                        }

                        val previewImageUrl = candidate.thumbnailUrl
                            ?: candidate.downloadUrl.takeIf { candidate.mediaType == MediaType.IMAGE }

                        if (previewImageUrl != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(StowPineElevated)
                                    .clickable { previewCandidate = candidate }
                            ) {
                                AsyncImage(
                                    model = previewImageUrl,
                                    contentDescription = "Media preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (candidate.mediaType == MediaType.VIDEO) {
                                    Surface(
                                        shape = CircleShape,
                                        color = StowPineDark.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .size(46.dp)
                                            .align(Alignment.Center)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Preview",
                                            tint = StowLavender,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Caption & Hashtag copy
                        candidate.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.bodySmall,
                                color = StowInkMutedOnDark,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val hashtags = remember(caption) { candidate.extractHashtags() }
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = { copyToClipboard("Caption", caption) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = StowLavender, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("COPY CAPTION", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                                }
                                if (hashtags.isNotEmpty()) {
                                    TextButton(onClick = { copyToClipboard("Hashtags", hashtags.joinToString(" ")) }) {
                                        Icon(Icons.Default.Tag, contentDescription = null, tint = StowLavender, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("TAGS (${hashtags.size})", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                                    }
                                }
                            }
                        }

                        // Audio & Cover extras for videos
                        if (candidate.mediaType == MediaType.VIDEO) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.onDownloadAudio(candidate) },
                                    enabled = !state.isDownloading,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, StowPineMuted)
                                ) {
                                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = StowLavender, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AUDIO .M4A", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                                }
                                if (!candidate.thumbnailUrl.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { viewModel.onDownloadCover(candidate) },
                                        enabled = !state.isDownloading,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, StowPineMuted)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = StowLavender, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("COVER", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                                    }
                                }
                            }
                        }

                        // Signature BRIK Segmented Download Progress Bar!
                        if (progress != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            when (progress) {
                                is DownloadProgress.Queued -> {
                                    Text("Queued...", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
                                }
                                is DownloadProgress.Running -> {
                                    Text(
                                        text = "Download progress",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = StowInkOnDark
                                    )
                                    Text(
                                        text = if (progress.totalBytes > 0) {
                                            "${formatBytes(progress.bytesRead)} of ${formatBytes(progress.totalBytes)}"
                                        } else {
                                            formatBytes(progress.bytesRead)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StowInkMutedOnDark
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    StowSegmentedProgressBar(percent = progress.percent)
                                }
                                is DownloadProgress.Completed -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreenDark, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Stowed · ${progress.fileName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SuccessGreenDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(
                                                                Uri.parse(progress.mediaStoreUri),
                                                                if (candidate.mediaType == MediaType.VIDEO) "video/*" else "image/*"
                                                            )
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(viewIntent)
                                                    } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = StowInkOnDark, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = if (candidate.mediaType == MediaType.VIDEO) "video/*" else "image/*"
                                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(progress.mediaStoreUri))
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                                                    } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = "Share", tint = StowInkOnDark, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                                is DownloadProgress.Failed -> {
                                    Text(
                                        text = formatAppError(progress.error),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErrorRedDark
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }

                // Light-canvas Save CTA Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Save to gallery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${state.selectedIndices.size} item${if (state.selectedIndices.size > 1) "s" else ""} ready",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    StowActionButton(
                        text = if (state.isDownloading) "Saving..." else "Save",
                        onClick = { viewModel.onDownload(force = false) },
                        enabled = state.selectedIndices.isNotEmpty() && !state.isDownloading
                    )
                }
            }
        }

        // 5. Bottom Paired Dark Bento Stat Tiles (matches BRIK "Best score" / "Reaction speed")
        if (state.extractedCandidates.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StowStatTile(
                    title = "Saved media",
                    subtitle = "On-device library",
                    value = "${libraryState.totalCount}",
                    modifier = Modifier.weight(1f)
                )
                StowStatTile(
                    title = "Storage used",
                    subtitle = "Local gallery",
                    value = formatBytes(libraryState.totalSizeBytes),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }

    previewCandidate?.let { candidate ->
        MediaPreviewDialog(
            mediaUriOrUrl = candidate.downloadUrl,
            isVideo = candidate.mediaType == MediaType.VIDEO,
            title = if (candidate.mediaType == MediaType.VIDEO) "Video Preview" else "Photo Preview",
            onDismiss = { previewCandidate = null }
        )
    }

    if (state.showDuplicatePrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicatePrompt() },
            containerColor = StowPineDark,
            title = { Text("Already stowed", style = MaterialTheme.typography.titleLarge, color = StowInkOnDark) },
            text = {
                Text(
                    text = "This post is already in your library. Save another copy?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StowInkMutedOnDark
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onDownload(force = true) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StowLavender, contentColor = StowPineDark),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("SAVE AGAIN", style = MaterialTheme.typography.labelSmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicatePrompt() }) {
                    Text("CANCEL", color = StowInkMutedOnDark, style = MaterialTheme.typography.labelSmall)
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}

internal fun formatAppError(err: AppError): String = when (err) {
    is AppError.InvalidUrl -> "Enter a valid public Instagram, Threads, X, Reddit, Pinterest, TikTok, or YouTube Shorts link."
    is AppError.StoriesNotSupported -> "Instagram Stories are private/ephemeral and not supported."
    is AppError.PrivateAccountNotSupported -> "Media from private accounts cannot be accessed."
    is AppError.PostNotFound -> "Post was not found or has been removed."
    is AppError.NetworkRateLimited -> "Rate-limited by platform. Try again in a moment."
    is AppError.ParserOutdated -> "Could not parse this post. Try syncing parser rules in Settings."
    is AppError.NoInternet -> "No internet connection. Check your network and try again."
    is AppError.WifiOnlyBlocked -> "Wi-Fi Only mode is enabled. Connect to Wi-Fi or turn it off in Settings."
    is AppError.StorageError -> "Storage error: ${err.reason}"
    is AppError.Unknown -> err.message
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
