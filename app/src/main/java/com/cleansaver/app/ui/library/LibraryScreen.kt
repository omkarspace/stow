package com.cleansaver.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.ui.components.MediaPreviewDialog
import com.cleansaver.app.ui.home.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var previewItem by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var itemToTag by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val inSelectionMode = state.selectedIds.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.pruneStaleEntries()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        if (inSelectionMode) {
            // Minimal Bulk Selection Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                    }
                    Text(
                        text = "${state.selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val uris = ArrayList(
                            state.items
                                .filter { state.selectedIds.contains(it.id) }
                                .map { Uri.parse(it.mediaStoreUri) }
                        )
                        if (uris.isNotEmpty()) {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${uris.size} items"))
                            } catch (_: Exception) {}
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share selected")
                    }
                    IconButton(onClick = { showBulkDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            // Minimal Header with Storage Breakdown & Sort
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (state.totalCount == 0) {
                            "Your saved Reels, photos, and audio appear here."
                        } else {
                            "${state.totalCount} saved · ${formatBytes(state.totalSizeBytes)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (state.totalCount > 0) {
                        Text(
                            text = "Videos ${formatBytes(state.videoSizeBytes)} · Photos ${formatBytes(state.imageSizeBytes)} · Audio ${formatBytes(state.audioSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.totalCount > 0) {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf(
                                "NEWEST" to "Newest first",
                                "OLDEST" to "Oldest first",
                                "LARGEST" to "Largest file",
                                "CREATOR" to "Creator A–Z"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (state.sortBy == key) "• $label" else label,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortBy(key)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (state.totalCount > 0) {
            // Search Bar (searches filename, shortcode, caption, creator, tag, platform)
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = {
                    Text(
                        text = "Search caption, @creator, tag...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Type & Favorites Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All (${state.totalCount})",
                    "VIDEO" to "Videos (${state.videoCount})",
                    "IMAGE" to "Photos (${state.imageCount})",
                    "AUDIO" to "Audio (${state.audioCount})",
                    "FAVORITES" to "Favorites (${state.favoriteCount})"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = state.filterType == type,
                        onClick = { viewModel.setFilter(type) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(10.dp),
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = state.filterType == type,
                            borderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            // Custom Collection Tags Row (shown only when tags exist)
            if (state.availableTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.availableTags.forEach { tag ->
                        FilterChip(
                            selected = state.selectedTag == tag,
                            onClick = { viewModel.setSelectedTag(tag) },
                            label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp),
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = state.selectedTag == tag,
                                borderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.totalCount == 0) "No saved media" else "No matching items",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (state.totalCount == 0) {
                            "Paste a link on the Save tab or share directly from Instagram, X, Threads, Reddit, or Pinterest."
                        } else {
                            "Try clearing your search or switching filters."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    val isVideo = item.mediaType == "VIDEO"
                    val isAudio = item.mediaType == "AUDIO"
                    val isSelected = state.selectedIds.contains(item.id)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (inSelectionMode) {
                                        viewModel.toggleSelect(item.id)
                                    } else if (isAudio) {
                                        try {
                                            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(item.mediaStoreUri), "audio/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(playIntent)
                                        } catch (_: Exception) {}
                                    } else {
                                        previewItem = item
                                    }
                                },
                                onLongClick = { viewModel.toggleSelect(item.id) }
                            ),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (isAudio) {
                                    Icon(
                                        imageVector = Icons.Default.AudioFile,
                                        contentDescription = "Audio",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .align(Alignment.Center)
                                    )
                                } else {
                                    AsyncImage(
                                        model = Uri.parse(item.mediaStoreUri),
                                        contentDescription = item.fileName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isVideo) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .size(34.dp)
                                                .align(Alignment.Center)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Video",
                                                tint = Color.White,
                                                modifier = Modifier.padding(7.dp)
                                            )
                                        }
                                    }
                                }

                                // Top-right Favorite or Selection Indicator
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(22.dp)
                                    )
                                } else {
                                    IconButton(
                                        onClick = { viewModel.toggleFavorite(item) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(30.dp)
                                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Favorite",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (item.tag.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "#${item.tag}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = item.creatorHandle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: item.shortcode,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${dateFormat.format(Date(item.createdAtMs))} · ${formatBytes(item.fileSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { itemToTag = item },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Label,
                                            contentDescription = "Tag",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    if (item.sourceUrl.startsWith("http")) {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl)))
                                                } catch (_: Exception) {}
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = "Open original post",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            try {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = when {
                                                        isVideo -> "video/*"
                                                        isAudio -> "audio/*"
                                                        else -> "image/*"
                                                    }
                                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(item.mediaStoreUri))
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { itemToDelete = item },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewItem?.let { item ->
        MediaPreviewDialog(
            mediaUriOrUrl = item.mediaStoreUri,
            isVideo = item.mediaType == "VIDEO",
            title = item.fileName,
            onDismiss = { previewItem = null }
        )
    }

    itemToTag?.let { item ->
        var tagInput by remember(item.id) { mutableStateOf(item.tag) }
        AlertDialog(
            onDismissRequest = { itemToTag = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Collection tag", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("e.g. Design, Recipes, Workouts") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setTag(item, tagInput)
                        itemToTag = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToTag = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete item?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "Remove ${item.fileName} from your gallery and library?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete ${state.selectedIds.size} items?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "Selected files will be removed from your gallery and library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        showBulkDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
