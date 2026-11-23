package com.cleansaver.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cleansaver.app.data.engine.StowFormatOption
import com.cleansaver.app.data.engine.StowMediaInfo
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
import com.cleansaver.app.ui.theme.StowPineElevated
import com.cleansaver.app.ui.theme.StowPineMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatPickerBottomSheet(
    mediaInfo: StowMediaInfo,
    onDownload: (format: StowFormatOption, embedSubtitles: Boolean, embedThumbnail: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = VIDEO, 1 = AUDIO
    val options = if (activeTab == 0) mediaInfo.videoFormats else mediaInfo.audioFormats
    var selectedFormat by remember(activeTab, mediaInfo.id) {
        mutableStateOf(options.firstOrNull())
    }
    var embedSubtitles by remember { mutableStateOf(false) }
    var embedThumbnail by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = StowPineDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            // Media Preview Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StowPineElevated)
                ) {
                    mediaInfo.thumbnailUrl?.let { thumb ->
                        AsyncImage(
                            model = thumb,
                            contentDescription = mediaInfo.title,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = StowInkOnDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val dur = mediaInfo.formattedDuration()
                    val meta = listOfNotNull(
                        mediaInfo.creator.takeIf { it.isNotBlank() }?.let { "@$it" },
                        dur.takeIf { it.isNotBlank() },
                        mediaInfo.platform
                    ).joinToString(" · ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = StowInkMutedOnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // VIDEO / AUDIO Segmented Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "VIDEO", 1 to "AUDIO").forEach { (idx, label) ->
                    val selected = activeTab == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeTab = idx
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) StowLavender else StowPineElevated
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) StowPineDark else StowInkMutedOnDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selectable Format & File Size Pills
            options.forEach { option ->
                val isSelected = selectedFormat?.id == option.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFormat = option
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) StowLavender else StowPineElevated,
                    border = if (isSelected) null else BorderStroke(1.dp, StowPineMuted)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) StowPineDark else StowInkOnDark
                        )
                        Text(
                            text = option.sizeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) StowPineDark else StowInkMutedOnDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option Chips (Subtitles & Embed Cover)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeTab == 0) {
                    TogglePill(
                        label = "Subtitles",
                        checked = embedSubtitles,
                        onClick = { embedSubtitles = !embedSubtitles }
                    )
                }
                TogglePill(
                    label = "Embed cover",
                    checked = embedThumbnail,
                    onClick = { embedThumbnail = !embedThumbnail }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    val fmt = selectedFormat ?: return@Button
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDownload(fmt, embedSubtitles, embedThumbnail)
                },
                enabled = selectedFormat != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StowLavender,
                    contentColor = StowPineDark
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text("DOWNLOAD NOW", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun TogglePill(
    label: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (checked) StowPineMuted else StowPineElevated,
        border = BorderStroke(1.dp, if (checked) StowLavender else StowPineMuted)
    ) {
        Text(
            text = if (checked) "✓ $label" else label,
            style = MaterialTheme.typography.labelSmall,
            color = if (checked) StowLavender else StowInkMutedOnDark,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

