package com.cleansaver.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            // Media Preview Header with overlaid duration pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StowPineElevated)
                ) {
                    mediaInfo.thumbnailUrl?.let { thumb ->
                        AsyncImage(
                            model = thumb,
                            contentDescription = mediaInfo.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    val dur = mediaInfo.formattedDuration()
                    if (dur.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StowPineDark.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = dur,
                                style = MaterialTheme.typography.labelSmall,
                                color = StowInkOnDark,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    StowLavenderBadge(text = mediaInfo.platform.ifBlank { "MEDIA" })
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = mediaInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = StowInkOnDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (mediaInfo.creator.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "@${mediaInfo.creator}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StowInkMutedOnDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // VIDEO / AUDIO Segmented Switcher
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StowPineElevated,
                border = BorderStroke(1.dp, StowPineMuted)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Triple(0, "VIDEO", Icons.Default.Videocam),
                        Triple(1, "AUDIO", Icons.Default.AudioFile)
                    ).forEach { (idx, label, icon) ->
                        val selected = activeTab == idx
                        val bg by animateColorAsState(
                            targetValue = if (selected) StowLavender else StowPineElevated,
                            animationSpec = tween(180),
                            label = "tab_bg_$idx"
                        )
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activeTab = idx
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = bg
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) StowPineDark else StowInkMutedOnDark,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) StowPineDark else StowInkMutedOnDark
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Format & File Size Pills
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 230.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val isSelected = selectedFormat?.id == option.id
                    val cardBg by animateColorAsState(
                        targetValue = if (isSelected) StowLavender else StowPineElevated,
                        animationSpec = tween(160),
                        label = "opt_bg_${option.id}"
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFormat = option
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = if (isSelected) null else BorderStroke(1.dp, StowPineMuted)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StowPineDark,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) StowPineDark else StowInkOnDark
                                )
                            }
                            Text(
                                text = option.sizeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) StowPineDark else StowInkMutedOnDark
                            )
                        }
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
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            embedSubtitles = !embedSubtitles
                        }
                    )
                }
                TogglePill(
                    label = "Embed cover",
                    checked = embedThumbnail,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        embedThumbnail = !embedThumbnail
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedFormat?.let { "DOWNLOAD · ${it.label.substringBefore(" · ")}" } ?: "DOWNLOAD NOW",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
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
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        )
    }
}
