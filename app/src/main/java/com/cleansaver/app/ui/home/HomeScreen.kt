package com.cleansaver.app.ui.home

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaCandidate
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.domain.usecase.ValidateUrlUseCase
import com.cleansaver.app.ui.components.MediaPreviewDialog
import com.cleansaver.app.ui.components.StowActionButton
import com.cleansaver.app.ui.components.StowDarkBadge
import com.cleansaver.app.ui.components.StowDarkBlock
import com.cleansaver.app.ui.components.StowExtractionShimmerBlock
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
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState,
    clipboardSuggestion: String? = null,
    onUseClipboardSuggestion: (String) -> Unit = {},
    onDismissClipboardSuggestion: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val libraryState by libraryViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val validator = remember { ValidateUrlUseCase() }

    val detectedParsedUrl = remember(state.inputUrl) {
        if (state.inputUrl.isBlank()) null else validator(state.inputUrl).getOrNull()
    }

    var previewCandidate by remember { mutableStateOf<MediaCandidate?>(null) }
    var previewRecentItem by remember { mutableStateOf<DownloadedMediaEntity?>(null) }

    fun copyWithSnackbar(label: String, text: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch { snackbarHostState.showSnackbar("$label copied to clipboard") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top Wordmark Block
        StowTopBarBlock(
            badgeText = if (libraryState.totalCount > 0) "${libraryState.totalCount} STOWED" else "ON-DEVICE"
        )

        // 2. Smart Clipboard Detection Pill
        AnimatedVisibility(
            visible = !clipboardSuggestion.isNullOrBlank() && state.inputUrl.isBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            clipboardSuggestion?.let { clipUrl ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUseClipboardSuggestion(clipUrl)
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = StowLavender
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = StowPineDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LINK IN CLIPBOARD · TAP TO STOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = StowPineDark
                            )
                            Text(
                                text = clipUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = StowPineDark.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onDismissClipboardSuggestion, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Dismiss", tint = StowPineDark, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 3. Hero Input Modular Dark Block
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

            Spacer(modifier = Modifier.height(16.dp))

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
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = StowInkMutedOnDark, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            if (clipboard?.hasPrimaryClip() == true &&
                                clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                            ) {
                                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                viewModel.onPaste(text)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = StowLavender, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboard?.hide()
                        viewModel.onExtract()
                    }
                ),
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

            // Live Platform Detection + Auto-Link Sanitizer ("Clean & Copy")
            AnimatedVisibility(visible = detectedParsedUrl != null) {
                detectedParsedUrl?.let { parsed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StowLavenderBadge(text = "${parsed.platform.displayName} DETECTED")
                        TextButton(
                            onClick = { copyWithSnackbar("Clean link", parsed.canonicalUrl) }
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = StowLavender, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CLEAN LINK", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                        }
                    }
                }
            }
        }

        // 4. Light-Canvas Contrast Action Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                StowDarkBadge(text = if (state.isExtracting) "PARSING STREAM" else "ZERO ADS · LOCAL")
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

            StowActionButton(
                text = if (state.isValidating || state.isExtracting) "Working..." else "Extract",
                onClick = {
                    keyboard?.hide()
                    viewModel.onExtract()
                },
                enabled = state.inputUrl.isNotBlank() && !state.isValidating && !state.isExtracting
            )
        }

        // Shimmer Skeleton while Extracting
        AnimatedVisibility(
            visible = state.isValidating || state.isExtracting,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            StowExtractionShimmerBlock(
                stageLabel = when {
                    state.isValidating -> "RESOLVING LINK"
                    state.extractionStatus != null -> state.extractionStatus!!.uppercase()
                    else -> "FETCHING STREAM"
                }
            )
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
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRedDark, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Unable to extract", style = MaterialTheme.typography.titleMedium, color = StowInkOnDark)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(formatAppError(err), style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
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

        // 5. Swipeable Carousel (HorizontalPager) for Extracted Media
        AnimatedVisibility(
            visible = state.extractedCandidates.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val pagerState = rememberPagerState(pageCount = { state.extractedCandidates.size })

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
                            text = "Slide ${pagerState.currentPage + 1}/${state.extractedCandidates.size} · ${state.selectedIndices.size} selected",
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

                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { pageIndex ->
                    val candidate = state.extractedCandidates[pageIndex]
                    val isSelected = state.selectedIndices.contains(pageIndex)
                    val progress = state.downloadProgressMap[candidate.slideIndex]

                    StowDarkBlock {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.extractedCandidates.size > 1) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleCandidateSelection(pageIndex)
                                    },
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
                                TextButton(onClick = { copyWithSnackbar("Caption", caption) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = StowLavender, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("COPY CAPTION", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                                }
                                if (hashtags.isNotEmpty()) {
                                    TextButton(onClick = { copyWithSnackbar("Hashtags", hashtags.joinToString(" ")) }) {
                                        Icon(Icons.Default.Tag, contentDescription = null, tint = StowLavender, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("TAGS (${hashtags.size})", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                                    }
                                }
                            }
                        }

                        if (candidate.mediaType == MediaType.VIDEO) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.onDownloadAudio(candidate)
                                        scope.launch { snackbarHostState.showSnackbar("Extracting audio (.m4a)...") }
                                    },
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
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.onDownloadCover(candidate)
                                            scope.launch { snackbarHostState.showSnackbar("Saving cover image...") }
                                        },
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

                        if (progress != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            when (progress) {
                                is DownloadProgress.Queued -> {
                                    Text("Queued...", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
                                }
                                is DownloadProgress.Running -> {
                                    Text("Download progress", style = MaterialTheme.typography.titleMedium, color = StowInkOnDark)
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
                                    Text(formatAppError(progress.error), style = MaterialTheme.typography.bodySmall, color = ErrorRedDark)
                                }
                                else -> {}
                            }
                        }
                    }
                }

                // Lavender Pager Dot Indicators
                if (state.extractedCandidates.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(state.extractedCandidates.size) { idx ->
                            val active = pagerState.currentPage == idx
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .height(6.dp)
                                    .width(if (active) 20.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (active) StowPineDark else StowPineMuted.copy(alpha = 0.35f))
                            )
                        }
                    }
                }

                // Light-canvas Save CTA Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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

        // 6. Recent Stows Horizontal Strip (keeps Home alive & 1-tap accessible)
        if (state.extractedCandidates.isEmpty() && libraryState.items.isNotEmpty()) {
            val recents = libraryState.items.take(6)
            StowDarkBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent stows", style = MaterialTheme.typography.titleMedium, color = StowInkOnDark)
                    StowLavenderBadge(text = "${recents.size} LATEST")
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    items(recents, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(StowPineElevated)
                                .clickable { previewRecentItem = item }
                        ) {
                            if (item.mediaType == "AUDIO") {
                                Icon(
                                    imageVector = Icons.Default.AudioFile,
                                    contentDescription = item.fileName,
                                    tint = StowLavender,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .align(Alignment.Center)
                                )
                            } else {
                                AsyncImage(
                                    model = Uri.parse(item.mediaStoreUri),
                                    contentDescription = item.fileName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (item.mediaType == "VIDEO") {
                                    Surface(
                                        shape = CircleShape,
                                        color = StowPineDark.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.Center)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = StowLavender,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Bottom Paired Dark Bento Stat Tiles
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

    previewRecentItem?.let { item ->
        MediaPreviewDialog(
            mediaUriOrUrl = item.mediaStoreUri,
            isVideo = item.mediaType == "VIDEO",
            title = item.fileName,
            onDismiss = { previewRecentItem = null }
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
