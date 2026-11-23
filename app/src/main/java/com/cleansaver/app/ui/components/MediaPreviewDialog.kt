package com.cleansaver.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
import com.cleansaver.app.ui.theme.StowPineMuted
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun MediaPreviewDialog(
    mediaUriOrUrl: String,
    isVideo: Boolean,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isLocalContent = mediaUriOrUrl.startsWith("content://")
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY > 180f) onDismiss()
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0f || dragOffsetY > 0f) {
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                            }
                        }
                    )
                },
            color = Color.Black
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dragOffsetY * 0.45f
                        alpha = (1f - (dragOffsetY / 700f)).coerceIn(0.4f, 1f)
                    }
            ) {
                if (isVideo) {
                    var isPlaying by remember { mutableStateOf(true) }
                    var isMuted by remember { mutableStateOf(false) }
                    var isBuffering by remember { mutableStateOf(true) }
                    var playbackError by remember { mutableStateOf<String?>(null) }
                    var currentPosMs by remember { mutableLongStateOf(0L) }
                    var durationMs by remember { mutableLongStateOf(0L) }

                    val exoPlayer = remember(mediaUriOrUrl) {
                        val builder = ExoPlayer.Builder(context)
                        if (!isLocalContent) {
                            val httpFactory = DefaultHttpDataSource.Factory()
                                .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                .setDefaultRequestProperties(mapOf("Referer" to "https://www.instagram.com/"))
                            builder.setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                        }
                        builder.build().apply {
                            repeatMode = Player.REPEAT_MODE_ONE
                            setMediaItem(MediaItem.fromUri(Uri.parse(mediaUriOrUrl)))
                            addListener(object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    isBuffering = playbackState == Player.STATE_BUFFERING
                                    if (playbackState == Player.STATE_READY) {
                                        durationMs = duration.coerceAtLeast(0L)
                                    }
                                }

                                override fun onIsPlayingChanged(playing: Boolean) {
                                    isPlaying = playing
                                }

                                override fun onPlayerError(error: PlaybackException) {
                                    isBuffering = false
                                    playbackError = "Unable to stream preview. You can still save to gallery."
                                }
                            })
                            prepare()
                            playWhenReady = true
                        }
                    }

                    DisposableEffect(exoPlayer) {
                        onDispose { exoPlayer.release() }
                    }

                    LaunchedEffect(exoPlayer, isPlaying) {
                        while (true) {
                            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                            val d = exoPlayer.duration
                            if (d > 0) durationMs = d
                            delay(250L)
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(exoPlayer) {
                                detectTapGestures(
                                    onTap = {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                )
                            }
                    )

                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = StowLavender,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(34.dp)
                                .align(Alignment.Center)
                        )
                    }

                    playbackError?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = StowInkOnDark,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp)
                        )
                    }

                    // Sleek Custom Stow Bottom Playback HUD
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = StowPineDark.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play or Pause",
                                        tint = StowLavender
                                    )
                                }

                                Slider(
                                    value = if (durationMs > 0) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { frac ->
                                        if (durationMs > 0) {
                                            exoPlayer.seekTo((frac * durationMs).toLong())
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = StowLavender,
                                        activeTrackColor = StowLavender,
                                        inactiveTrackColor = StowPineMuted
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = StowInkOnDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatTimeMs(currentPosMs), style = MaterialTheme.typography.labelSmall, color = StowInkMutedOnDark)
                                Text(formatTimeMs(durationMs), style = MaterialTheme.typography.labelSmall, color = StowInkMutedOnDark)
                            }
                        }
                    }
                } else {
                    // Pinch-to-Zoom & Double-Tap Photo Viewer
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }

                    AsyncImage(
                        model = if (isLocalContent) Uri.parse(mediaUriOrUrl) else mediaUriOrUrl,
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    scale = newScale
                                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1.2f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                        }
                                    }
                                )
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                // Minimal Top Header Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(StowPineDark.copy(alpha = 0.75f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = StowInkOnDark, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = StowInkOnDark,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isLocalContent) {
                        IconButton(
                            onClick = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (isVideo) "video/*" else "image/*"
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaUriOrUrl))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(StowPineDark.copy(alpha = 0.75f), CircleShape)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = StowLavender, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format("%02d:%02d", mins, secs)
}
