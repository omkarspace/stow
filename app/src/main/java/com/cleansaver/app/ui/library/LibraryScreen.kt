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
import androidx.compose.ui.draw.clip
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
import com.cleansaver.app.ui.components.StowStatTile
import com.cleansaver.app.ui.components.StowTopBarBlock
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.pruneStaleEntries()
    }

    fun deleteWithUndo(item: DownloadedMediaEntity) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        pendingDeleteIds = pendingDeleteIds + item.id
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Removed ${item.fileName}",
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
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 18.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                if (inSelectionMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = StowPineDark
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                    StowTopBarBlock(badgeText = "${state.totalCount} ITEMS")
                }
            }

            if (state.totalCount > 0) {
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StowStatTile(
                            title = "Total stowed",
                            subtitle = "${state.videoCount} vid · ${state.imageCount} img · ${state.audioCount} aud",
                            value = "${state.totalCount}",
                            modifier = Modifier.weight(1f)
                        )
                        StowStatTile(
                            title = "Storage",
                            subtitle = "Vid ${formatBytes(state.videoSizeBytes)} · Img ${formatBytes(state.imageSizeBytes)}",
                            value = formatBytes(state.totalSizeBytes),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
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
                                        text = "Search caption, @creator, tag...",
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
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                    focusedBorderColor = StowPineDark,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
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
                                "ALL" to "ALL (${state.totalCount})",
                                "VIDEO" to "VIDEOS (${state.videoCount})",
                                "IMAGE" to "PHOTOS (${state.imageCount})",
                                "AUDIO" to "AUDIO (${state.audioCount})",
                                "FAVORITES" to "FAVS (${state.favoriteCount})"
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
                                        borderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
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
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = StowLavender,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = StowPineDark, modifier = Modifier.padding(15.dp))
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
                                    "Paste a link on the Stow tab or share directly from any supported app."
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
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelect(item.id)
                                }
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = if (isSelected) BorderStroke(2.dp, StowLavender) else null
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(StowPineElevated)
                            ) {
                                if (isAudio) {
                                    Icon(
                                        imageVector = Icons.Default.AudioFile,
                                        contentDescription = "Audio",
                                        tint = StowLavender,
                                        modifier = Modifier
                                            .size(42.dp)
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
                                            color = StowPineDark.copy(alpha = 0.75f),
                                            modifier = Modifier
                                                .size(34.dp)
                                                .align(Alignment.Center)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = StowLavender, modifier = Modifier.padding(7.dp))
                                        }
                                    }
                                }

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
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleFavorite(item)
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .background(StowPineDark.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Favorite",
                                            tint = if (item.isFavorite) StowLavender else StowInkOnDark,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                if (item.tag.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = StowLavender,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "#${item.tag.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StowPineDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = item.creatorHandle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: item.shortcode,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StowInkOnDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${dateFormat.format(Date(item.createdAtMs))} · ${formatBytes(item.fileSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StowInkMutedOnDark,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { itemToTag = item }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Label, contentDescription = "Tag", tint = StowInkMutedOnDark, modifier = Modifier.size(14.dp))
                                    }
                                    if (item.sourceUrl.startsWith("http")) {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl)))
                                                } catch (_: Exception) {}
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = StowInkMutedOnDark, modifier = Modifier.size(14.dp))
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
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = StowInkMutedOnDark, modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(onClick = { deleteWithUndo(item) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StowInkMutedOnDark, modifier = Modifier.size(14.dp))
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
