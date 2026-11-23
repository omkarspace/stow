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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cleansaver.app.data.local.DownloadedMediaEntity
import com.cleansaver.app.ui.components.MediaPreviewDialog
import com.cleansaver.app.ui.components.StowDarkBlock
import com.cleansaver.app.ui.components.StowLavenderBadge
import com.cleansaver.app.ui.home.formatBytes
import com.cleansaver.app.ui.theme.ErrorRedDark
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
import com.cleansaver.app.ui.theme.StowPineElevated
import com.cleansaver.app.ui.theme.StowPineMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var previewItem by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var itemToTag by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val visibleItems = remember(state.items, pendingDeleteIds) {
        state.items.filterNot { pendingDeleteIds.contains(it.id) }
    }
    val inSelectionMode = state.selectedIds.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.pruneStaleEntries()
    }

    fun deleteWithUndo(item: DownloadedMediaEntity) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pendingDeleteIds = pendingDeleteIds + item.id
        scope.launch {
            val label = item.title.ifBlank { item.fileName }.take(28)
            val result = snackbarHostState.showSnackbar(
                message = "Removed $label",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeleteIds = pendingDeleteIds - item.id
            } else {
                viewModel.deleteItem(item)
                pendingDeleteIds = pendingDeleteIds - item.id
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            isRefreshing = true
            viewModel.pruneStaleEntries()
            scope.launch {
                delay(550L)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
        ) {
            // Lightweight Canvas-Direct Header (or Selection Bar)
            item(span = { GridItemSpan(2) }) {
                if (inSelectionMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = StowPineDark
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = StowInkOnDark)
                            }
                            Text(
                                text = "${state.selectedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                color = StowInkOnDark,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val uris = ArrayList(
                                    visibleItems
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
                                Icon(Icons.Default.Share, contentDescription = "Share selected", tint = StowLavender)
                            }
                            IconButton(onClick = { showBulkDeleteConfirm = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = ErrorRedDark)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIBRARY",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        StowLavenderBadge(
                            text = if (state.totalCount > 0) {
                                "${state.totalCount} SAVED · ${formatBytes(state.totalSizeBytes)}"
                            } else {
                                "0 SAVED"
                            }
                        )
                    }
                }
            }

            if (state.totalCount > 0) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = {
                                    Text(
                                        text = "Search title, @creator, tag...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    focusedBorderColor = StowPineDark,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )

                            Box {
                                Surface(shape = RoundedCornerShape(14.dp), color = StowPineDark) {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.Default.Sort, contentDescription = "Sort", tint = StowLavender)
                                    }
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    listOf(
                                        "NEWEST" to "Newest first",
                                        "OLDEST" to "Oldest first",
                                        "LARGEST" to "Largest file",
                                        "CREATOR" to "Creator A–Z"
                                    ).forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(if (state.sortBy == key) "• $label" else label) },
                                            onClick = {
                                                viewModel.setSortBy(key)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val chipColors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StowPineDark,
                            selectedLabelColor = StowLavender,
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onBackground
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "ALL" to "ALL",
                                "VIDEO" to "VIDEO",
                                "AUDIO" to "AUDIO",
                                "IMAGE" to "PHOTO",
                                "FAVORITES" to "FAVS"
                            ).forEach { (type, label) ->
                                FilterChip(
                                    selected = state.filterType == type,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setFilter(type)
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = chipColors,
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = state.filterType == type,
                                        borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
                                    )
                                )
                            }
                        }

                        if (state.availableTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
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
                                        colors = chipColors
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (visibleItems.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    StowDarkBlock(modifier = Modifier.padding(top = 6.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = StowLavender,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = StowPineDark, modifier = Modifier.padding(14.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (state.totalCount == 0) "Nothing stowed yet." else "No matching items.",
                                style = MaterialTheme.typography.titleLarge,
                                color = StowInkOnDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (state.totalCount == 0) {
                                    "Paste a link on the Stow tab or share from any app."
                                } else {
                                    "Try clearing your search or switching filters."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = StowInkMutedOnDark,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(visibleItems, key = { it.id }) { item ->
                    val isVideo = item.mediaType == "VIDEO"
                    val isAudio = item.mediaType == "AUDIO"
                    val isSelected = state.selectedIds.contains(item.id)
                    var menuExpanded by remember(item.id) { mutableStateOf(false) }

                    // Full-Bleed 4:5 Media Tile (Concept A — Zero Clutter)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 5f)
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
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelect(item.id)
                                }
                            ),
                        shape = RoundedCornerShape(18.dp),
                        color = StowPineElevated,
                        border = if (isSelected) BorderStroke(2.5.dp, StowLavender) else null
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isAudio) {
                                Icon(
                                    imageVector = Icons.Default.AudioFile,
                                    contentDescription = item.title,
                                    tint = StowLavender,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .align(Alignment.Center)
                                )
                            } else {
                                AsyncImage(
                                    model = Uri.parse(item.mediaStoreUri),
                                    contentDescription = item.title.ifBlank { item.fileName },
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            // Bottom dark gradient scrim for crisp legibility
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, StowPineDark.copy(alpha = 0.94f))
                                        )
                                    )
                            )

                            // Top-left: overflow menu (or favorite dot)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(StowPineDark.copy(alpha = 0.58f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = if (item.isFavorite) StowLavender else StowInkOnDark,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (item.isFavorite) "Remove favorite" else "Favorite") },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.toggleFavorite(item)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Collection tag") },
                                        onClick = {
                                            menuExpanded = false
                                            itemToTag = item
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        onClick = {
                                            menuExpanded = false
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
                                        }
                                    )
                                    if (item.sourceUrl.startsWith("http")) {
                                        DropdownMenuItem(
                                            text = { Text("Open source link") },
                                            onClick = {
                                                menuExpanded = false
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl)))
                                                } catch (_: Exception) {}
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = ErrorRedDark) },
                                        onClick = {
                                            menuExpanded = false
                                            deleteWithUndo(item)
                                        }
                                    )
                                }
                            }

                            // Top-right: Duration / Type badge or Selected check
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = StowLavender,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(22.dp)
                                )
                            } else {
                                val badgeText = when {
                                    item.durationSec > 0 -> formatDuration(item.durationSec)
                                    isAudio -> "AUDIO"
                                    isVideo -> "VIDEO"
                                    else -> "PHOTO"
                                }
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = StowPineDark.copy(alpha = 0.78f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StowInkOnDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Bottom Scrim Text: Real Title + Creator · Size
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(horizontal = 11.dp, vertical = 10.dp)
                            ) {
                                val displayTitle = item.title.takeIf { it.isNotBlank() }
                                    ?: item.fileName.substringBeforeLast('.')
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StowInkOnDark,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val sub = listOfNotNull(
                                    item.creatorHandle.takeIf { it.isNotBlank() }?.let { "@$it" },
                                    formatBytes(item.fileSizeBytes)
                                ).joinToString(" · ")
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StowInkMutedOnDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
            title = item.title.ifBlank { item.fileName },
            onDismiss = { previewItem = null }
        )
    }

    itemToTag?.let { item ->
        var tagInput by remember(item.id) { mutableStateOf(item.tag) }
        AlertDialog(
            onDismissRequest = { itemToTag = null },
            containerColor = StowPineDark,
            title = { Text("Collection tag", style = MaterialTheme.typography.titleLarge, color = StowInkOnDark) },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("Design, Recipes, Workouts", color = StowInkMutedOnDark) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StowInkOnDark,
                        unfocusedTextColor = StowInkOnDark,
                        focusedBorderColor = StowLavender,
                        unfocusedBorderColor = StowPineMuted
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setTag(item, tagInput)
                        itemToTag = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StowLavender, contentColor = StowPineDark),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("SAVE", style = MaterialTheme.typography.labelSmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToTag = null }) {
                    Text("CANCEL", color = StowInkMutedOnDark, style = MaterialTheme.typography.labelSmall)
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            containerColor = StowPineDark,
            title = { Text("Delete ${state.selectedIds.size} items?", style = MaterialTheme.typography.titleLarge, color = StowInkOnDark) },
            text = {
                Text(
                    text = "Selected files will be removed from your gallery and library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StowInkMutedOnDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        showBulkDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRedDark, contentColor = StowPineDark),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("DELETE ALL", style = MaterialTheme.typography.labelSmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("CANCEL", color = StowInkMutedOnDark, style = MaterialTheme.typography.labelSmall)
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}
