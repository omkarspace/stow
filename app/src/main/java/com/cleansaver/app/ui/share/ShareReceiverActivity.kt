package com.cleansaver.app.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cleansaver.app.CleanSaverApplication
import com.cleansaver.app.domain.model.DownloadProgress
import com.cleansaver.app.domain.model.MediaType
import com.cleansaver.app.ui.components.StowLavenderBadge
import com.cleansaver.app.ui.components.StowSegmentedProgressBar
import com.cleansaver.app.ui.home.formatAppError
import com.cleansaver.app.ui.state.ShareSheetUiState
import com.cleansaver.app.ui.theme.CleanSaverTheme
import com.cleansaver.app.ui.theme.ErrorRedDark
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
import com.cleansaver.app.ui.theme.StowPineElevated
import com.cleansaver.app.ui.theme.SuccessGreenDark
import kotlinx.coroutines.delay

class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: ShareReceiverViewModel by viewModels {
        val app = application as CleanSaverApplication
        ShareReceiverViewModel.provideFactory(app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

        viewModel.handleSharedContent(sharedText)

        setContent {
            CleanSaverTheme(darkTheme = false) {
                ShareReceiverSheet(
                    viewModel = viewModel,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReceiverSheet(
    viewModel: ShareReceiverViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state) {
        if (state is ShareSheetUiState.Completed) {
            delay(1100)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = StowPineDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "STOW®", style = MaterialTheme.typography.headlineMedium, color = StowInkOnDark)
                StowLavenderBadge(text = "QUICK SAVE")
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (val s = state) {
                is ShareSheetUiState.Idle, is ShareSheetUiState.Loading -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = StowPineElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = StowLavender,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Resolving media...", style = MaterialTheme.typography.bodyMedium, color = StowInkOnDark)
                        }
                    }
                }

                is ShareSheetUiState.PreviewReady -> {
                    Text(
                        text = if (s.candidates.size == 1) "1 item ready" else "${s.candidates.size} items ready",
                        style = MaterialTheme.typography.titleMedium,
                        color = StowInkOnDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    s.candidates.forEachIndexed { idx, candidate ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = StowPineElevated
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (candidate.mediaType == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                                    contentDescription = null,
                                    tint = StowLavender,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${candidate.platform.displayName} · ${if (candidate.mediaType == MediaType.VIDEO) "Video" else "Photo"} ${if (s.candidates.size > 1) "#${idx + 1}" else ""}".trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StowInkOnDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.startDownload(s.candidates) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StowLavender, contentColor = StowPineDark),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = if (s.candidates.size == 1) "STOW TO GALLERY" else "STOW ${s.candidates.size} ITEMS",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                is ShareSheetUiState.Downloading -> {
                    val active = s.progressMap.values.filterIsInstance<DownloadProgress.Running>().firstOrNull()
                    Text("Saving to gallery", style = MaterialTheme.typography.titleMedium, color = StowInkOnDark)
                    Spacer(modifier = Modifier.height(12.dp))
                    StowSegmentedProgressBar(percent = active?.percent ?: 10)
                }

                is ShareSheetUiState.Completed -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = StowPineElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreenDark, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Stowed ${s.savedCount} item${if (s.savedCount > 1) "s" else ""} to gallery",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SuccessGreenDark
                            )
                        }
                    }
                }

                is ShareSheetUiState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = StowPineElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRedDark, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = formatAppError(s.appError),
                                style = MaterialTheme.typography.bodySmall,
                                color = StowInkMutedOnDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
