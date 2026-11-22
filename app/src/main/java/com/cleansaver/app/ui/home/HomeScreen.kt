package com.cleansaver.app.ui.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cleansaver.app.domain.model.AppError
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaType

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "CleanSaver",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Ad-free public Reel & Post downloader",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // URL Input Field
        OutlinedTextField(
            value = state.inputUrl,
            onValueChange = { viewModel.onUrlChanged(it) },
            label = { Text("Paste Instagram URL") },
            placeholder = { Text("https://www.instagram.com/reel/...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Link, contentDescription = null)
            },
            trailingIcon = {
                if (state.inputUrl.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onUrlChanged("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
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
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Button(
            onClick = { viewModel.onExtract() },
            enabled = state.inputUrl.isNotBlank() && !state.isValidating && !state.isExtracting,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.isValidating || state.isExtracting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.isValidating) "Validating..." else "Extracting...")
            } else {
                Text("Search & Extract")
            }
        }

        // Error message card
        state.error?.let { err ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (err) {
                            is AppError.InvalidUrl -> "Please provide a valid public Reel or Post URL."
                            is AppError.StoriesNotSupported -> "Stories are private/ephemeral and not supported."
                            is AppError.PrivateAccountNotSupported -> "Media from private accounts cannot be saved."
                            is AppError.PostNotFound -> "Post not found or was deleted."
                            is AppError.NetworkRateLimited -> "Rate limit reached. Please wait a few moments."
                            is AppError.ParserOutdated -> "Parser rules need updating. Go to Settings to refresh."
                            is AppError.NoInternet -> "No internet connection."
                            is AppError.StorageError -> "Storage error: ${err.reason}"
                            is AppError.Unknown -> err.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Extracted Media Preview
        if (state.extractedCandidates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items (${state.selectedIndices.size}/${state.extractedCandidates.size} selected)",
                    style = MaterialTheme.typography.titleLarge
                )

                if (state.extractedCandidates.size > 1) {
                    TextButton(
                        onClick = {
                            if (state.selectedIndices.size == state.extractedCandidates.size) {
                                viewModel.deselectAllCandidates()
                            } else {
                                viewModel.selectAllCandidates()
                            }
                        }
                    ) {
                        Text(if (state.selectedIndices.size == state.extractedCandidates.size) "Deselect All" else "Select All")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            state.extractedCandidates.forEachIndexed { index, candidate ->
                val isSelected = state.selectedIndices.contains(index)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { viewModel.toggleCandidateSelection(index) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.extractedCandidates.size > 1) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleCandidateSelection(index) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Icon(
                                imageVector = if (candidate.mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${candidate.mediaType.name} #${index + 1} (${candidate.shortcode})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        candidate.thumbnailUrl?.let { thumb ->
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = thumb,
                                contentDescription = "Thumbnail",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Progress indicator for this candidate
                        val progress = state.downloadProgressMap[index]
                        if (progress != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            when (progress) {
                                is DownloadProgress.Running -> {
                                    LinearProgressIndicator(
                                        progress = { progress.percent / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "${progress.percent}% (${progress.bytesRead / 1024} KB)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                is DownloadProgress.Completed -> {
                                    Text(
                                        text = "✓ Saved to Gallery (${progress.fileName})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is DownloadProgress.Failed -> {
                                    Text(
                                        text = "Failed: ${progress.error}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.onDownload(force = false) },
                enabled = state.selectedIndices.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Selected (${state.selectedIndices.size}) to Gallery")
            }
        }
    }

    // FR-4 Deduplication Confirmation Dialog
    if (state.showDuplicatePrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicatePrompt() },
            title = { Text("Item Already Saved") },
            text = { Text("Item already saved. Download again?") },
            confirmButton = {
                Button(onClick = { viewModel.onDownload(force = true) }) {
                    Text("Download Again")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicatePrompt() }) {
                    Text("Cancel")
                }
            }
        )
    }
}
