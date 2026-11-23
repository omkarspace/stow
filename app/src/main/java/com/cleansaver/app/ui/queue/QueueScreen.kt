package com.cleansaver.app.ui.queue

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cleansaver.app.data.engine.QueueTask
import com.cleansaver.app.data.engine.QueueTaskStatus
import com.cleansaver.app.ui.components.StowDarkBlock
import com.cleansaver.app.ui.components.StowLavenderBadge
import com.cleansaver.app.ui.components.StowSegmentedProgressBar
import com.cleansaver.app.ui.home.HomeViewModel
import com.cleansaver.app.ui.theme.ErrorRedDark
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
import com.cleansaver.app.ui.theme.StowPineElevated
import com.cleansaver.app.ui.theme.SuccessGreenDark

@Composable
fun QueueScreen(
    homeViewModel: HomeViewModel
) {
    val tasks by homeViewModel.queueTasks.collectAsState()
    val context = LocalContext.current

    val activeCount = tasks.count {
        it.status == QueueTaskStatus.DOWNLOADING || it.status == QueueTaskStatus.QUEUED
    }
    val hasFinished = tasks.any {
        it.status == QueueTaskStatus.COMPLETED || it.status == QueueTaskStatus.FAILED || it.status == QueueTaskStatus.CANCELLED
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Lightweight Canvas-Direct Swiss Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUEUE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasFinished) {
                        TextButton(onClick = { homeViewModel.clearCompletedQueue() }) {
                            Text(
                                text = "CLEAR DONE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                    StowLavenderBadge(
                        text = if (activeCount > 0) "$activeCount ACTIVE" else "IDLE"
                    )
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
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
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = StowPineDark,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No active downloads.",
                            style = MaterialTheme.typography.titleLarge,
                            color = StowInkOnDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Paste any link on Stow or share from another app to start downloading.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StowInkMutedOnDark,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                QueueTaskCard(
                    task = task,
                    onCancel = { homeViewModel.cancelQueueTask(task.id) },
                    onRetry = { homeViewModel.retryQueueTask(task.id) },
                    onRemove = { homeViewModel.removeQueueTask(task.id) },
                    onOpen = {
                        task.savedUri?.let { uriStr ->
                            try {
                                val mime = if (task.format.isAudio) "audio/*" else "video/*"
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(uriStr), mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    },
                    onShare = {
                        task.savedUri?.let { uriStr ->
                            try {
                                val mime = if (task.format.isAudio) "audio/*" else "video/*"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Media"))
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueTaskCard(
    task: QueueTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    StowDarkBlock {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(StowPineElevated),
                contentAlignment = Alignment.Center
            ) {
                if (task.mediaInfo.thumbnailUrl != null) {
                    AsyncImage(
                        model = task.mediaInfo.thumbnailUrl,
                        contentDescription = task.mediaInfo.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (task.format.isAudio) Icons.Default.AudioFile else Icons.Default.Download,
                        contentDescription = null,
                        tint = StowLavender,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.mediaInfo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = StowInkOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val meta = listOfNotNull(
                    task.format.label,
                    task.mediaInfo.platform.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = StowInkMutedOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (task.status) {
                QueueTaskStatus.DOWNLOADING, QueueTaskStatus.QUEUED -> {
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = StowInkMutedOnDark, modifier = Modifier.size(18.dp))
                    }
                }
                QueueTaskStatus.COMPLETED -> {
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = StowInkOnDark, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = StowLavender, modifier = Modifier.size(17.dp))
                    }
                }
                QueueTaskStatus.FAILED, QueueTaskStatus.CANCELLED -> {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = StowInkMutedOnDark, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (task.status) {
            QueueTaskStatus.QUEUED -> {
                Text(
                    text = "Queued · waiting for slot...",
                    style = MaterialTheme.typography.bodySmall,
                    color = StowInkMutedOnDark
                )
            }

            QueueTaskStatus.DOWNLOADING -> {
                val stats = buildList {
                    if (task.speed.isNotBlank()) add(task.speed)
                    if (task.etaSec > 0) add("${formatEta(task.etaSec)} left")
                }.joinToString(" · ")
                if (stats.isNotBlank()) {
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.labelSmall,
                        color = StowInkMutedOnDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                StowSegmentedProgressBar(percent = task.percent)
            }

            QueueTaskStatus.COMPLETED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreenDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Saved · ${task.savedFileName ?: "Gallery"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreenDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            QueueTaskStatus.FAILED, QueueTaskStatus.CANCELLED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRedDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (task.status == QueueTaskStatus.CANCELLED) "Cancelled" else (task.errorMessage ?: "Download failed"),
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRedDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StowLavender,
                            contentColor = StowPineDark
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RETRY", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}
