package com.cleansaver.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cleansaver.app.ui.components.StowDarkBlock
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
import com.cleansaver.app.util.LibraryBackupManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    snackbarHostState: SnackbarHostState
) {
    val state by viewModel.uiState.collectAsState()
    val libraryState by libraryViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var albumDraft by remember(state.customAlbumName) { mutableStateOf(state.customAlbumName) }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = StowLavender,
        selectedLabelColor = StowPineDark,
        containerColor = StowPineElevated,
        labelColor = StowInkMutedOnDark
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StowTopBarBlock(badgeText = "V${state.appVersion}")

        // Block 1: Appearance & Automation
        StowDarkBlock {
            Text("Appearance", style = MaterialTheme.typography.titleLarge, color = StowInkOnDark)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SYSTEM" to "BRIK", "LIGHT" to "BONE", "DARK" to "NIGHT").forEach { (mode, label) ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp),
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = state.themeMode == mode,
                            borderColor = StowPineMuted
                        )
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            SettingsToggleRow(
                title = "Clipboard auto-detect",
                subtitle = "Extract supported links automatically on focus",
                checked = state.clipboardAutoDetect,
                onCheckedChange = { viewModel.setClipboardAutoDetect(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            SettingsToggleRow(
                title = "Instant 1-tap Share save",
                subtitle = "Skip preview and download immediately on share",
                checked = state.instantShareSave,
                onCheckedChange = { viewModel.setInstantShareSave(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            SettingsToggleRow(
                title = "Wi-Fi only downloads",
                subtitle = "Block downloads on cellular data",
                checked = state.wifiOnlyDownloads,
                onCheckedChange = { viewModel.setWifiOnlyDownloads(it) }
            )
        }

        // Block 2: Downloads & Storage
        StowDarkBlock {
            Text("Downloads & storage", style = MaterialTheme.typography.titleLarge, color = StowInkOnDark)
            Spacer(modifier = Modifier.height(12.dp))

            Text("Stream quality", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BEST" to "BEST QUALITY", "DATA_SAVER" to "DATA SAVER").forEach { (q, label) ->
                    FilterChip(
                        selected = state.qualityPreference == q,
                        onClick = { viewModel.setQualityPreference(q) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp),
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = state.qualityPreference == q, borderColor = StowPineMuted)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            Text("File naming", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DEFAULT" to "STOW_CODE", "CREATOR_DATE" to "CREATOR_DATE").forEach { (p, label) ->
                    FilterChip(
                        selected = state.fileNamingPattern == p,
                        onClick = { viewModel.setFileNamingPattern(p) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp),
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = state.fileNamingPattern == p, borderColor = StowPineMuted)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            Text("Gallery subfolder", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = albumDraft,
                    onValueChange = { albumDraft = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StowInkOnDark,
                        unfocusedTextColor = StowInkOnDark,
                        focusedBorderColor = StowLavender,
                        unfocusedBorderColor = StowPineMuted,
                        focusedContainerColor = StowPineElevated,
                        unfocusedContainerColor = StowPineElevated
                    )
                )
                Button(
                    onClick = { viewModel.setCustomAlbumName(albumDraft) },
                    enabled = albumDraft.trim() != state.customAlbumName && albumDraft.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StowLavender, contentColor = StowPineDark),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("SAVE", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Block 3: Privacy & Biometric Vault
        StowDarkBlock {
            SettingsToggleRow(
                title = "Biometric vault lock",
                subtitle = "Require fingerprint, face, or PIN to open Library",
                checked = state.vaultLockEnabled,
                onCheckedChange = { viewModel.setVaultLockEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            Text(
                text = "Stow runs 100% on-device with zero ads, zero logins, and zero analytics. Unaffiliated with Meta, X, Reddit, Pinterest, TikTok, or YouTube.",
                style = MaterialTheme.typography.bodySmall,
                color = StowInkMutedOnDark
            )
        }

        // Block 4: Engine, Updates & Data
        StowDarkBlock {
            Text("Engine & maintenance", style = MaterialTheme.typography.titleLarge, color = StowInkOnDark)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.refreshParserConfig() },
                    enabled = !state.isRefreshingConfig,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StowPineMuted),
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isRefreshingConfig) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = StowLavender)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = StowLavender, modifier = Modifier.size(15.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SYNC RULES", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                }

                OutlinedButton(
                    onClick = { viewModel.checkForUpdate() },
                    enabled = !state.isCheckingUpdate,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StowPineMuted),
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = StowLavender)
                    } else {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = StowLavender, modifier = Modifier.size(15.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("UPDATES", style = MaterialTheme.typography.labelSmall, color = StowInkOnDark)
                }
            }

            state.configRefreshMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.configRefreshSuccess) SuccessGreenDark else StowInkMutedOnDark
                )
            }

            state.updateCheckMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.updateInfo?.isUpdateAvailable == true) SuccessGreenDark else StowInkMutedOnDark,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.updateInfo?.isUpdateAvailable == true) {
                        TextButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.updateInfo!!.releaseUrl)))
                            } catch (_: Exception) {}
                        }) {
                            Text("GET", color = StowLavender, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Extraction log", style = MaterialTheme.typography.titleMedium, color = StowInkOnDark)
                    Text("Recent strategy attempts & HTTP codes", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
                }
                TextButton(onClick = {
                    viewModel.refreshDiagnosticLogs()
                    showDiagnostics = true
                }) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = StowLavender, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("VIEW", color = StowLavender, style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Export library backup", style = MaterialTheme.typography.titleMedium, color = StowInkOnDark)
                    Text("Share favorites, tags & metadata as JSON", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
                }
                TextButton(onClick = {
                    if (libraryState.items.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar("Library is empty") }
                    } else {
                        LibraryBackupManager.shareBackupJson(context, libraryState.items)
                    }
                }) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = StowLavender, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EXPORT", color = StowLavender, style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = StowPineMuted)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clear library history", style = MaterialTheme.typography.titleMedium, color = StowInkOnDark)
                    Text("Gallery files stay intact", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
                }
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("CLEAR", color = ErrorRedDark, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }

    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            containerColor = StowPineDark,
            title = { Text("Extraction log", style = MaterialTheme.typography.titleLarge, color = StowInkOnDark) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (state.diagnosticLogs.isEmpty()) {
                        Text("No extraction activity recorded yet.", style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
                    } else {
                        state.diagnosticLogs.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, color = StowInkOnDark, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text("CLOSE", color = StowLavender, style = MaterialTheme.typography.labelSmall)
                }
            },
            dismissButton = {
                if (state.diagnosticLogs.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearDiagnosticLogs() }) {
                        Text("CLEAR", color = StowInkMutedOnDark, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = StowPineDark,
            title = { Text("Clear history?", style = MaterialTheme.typography.titleLarge, color = StowInkOnDark) },
            text = {
                Text(
                    text = "This removes all download records from Stow. Files already saved in your device gallery will not be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StowInkMutedOnDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRedDark, contentColor = StowPineDark),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("CLEAR ALL", style = MaterialTheme.typography.labelSmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("CANCEL", color = StowInkMutedOnDark, style = MaterialTheme.typography.labelSmall)
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = StowInkOnDark)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = StowInkMutedOnDark)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StowPineDark,
                checkedTrackColor = StowLavender,
                uncheckedThumbColor = StowInkMutedOnDark,
                uncheckedTrackColor = StowPineElevated,
                uncheckedBorderColor = StowPineMuted
            )
        )
    }
}
