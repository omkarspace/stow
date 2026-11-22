package com.cleansaver.app.ui.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cleansaver.app.CleanSaverApplication
import com.cleansaver.app.ui.components.DisclaimerBottomSheet
import com.cleansaver.app.ui.library.LibraryScreen
import com.cleansaver.app.ui.library.LibraryViewModel
import com.cleansaver.app.ui.settings.SettingsScreen
import com.cleansaver.app.ui.settings.SettingsViewModel
import com.cleansaver.app.ui.theme.CleanSaverTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels {
        val app = application as CleanSaverApplication
        HomeViewModel.provideFactory(app.container)
    }

    private val libraryViewModel: LibraryViewModel by viewModels {
        val app = application as CleanSaverApplication
        LibraryViewModel.provideFactory(app.container)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as CleanSaverApplication
        SettingsViewModel.provideFactory(app.container)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CleanSaverTheme {
                val settingsState by settingsViewModel.uiState.collectAsState()
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Download, contentDescription = "Home") },
                                label = { Text("Saver") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Library") },
                                label = { Text("Library") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> HomeScreen(viewModel = homeViewModel)
                            1 -> LibraryScreen(viewModel = libraryViewModel)
                            2 -> SettingsScreen(viewModel = settingsViewModel)
                        }
                    }

                    // First-run Disclaimer BottomSheet
                    if (!settingsState.fairUseAccepted) {
                        DisclaimerBottomSheet(
                            sheetState = sheetState,
                            onAccept = {
                                scope.launch {
                                    sheetState.hide()
                                    settingsViewModel.setFairUseAccepted(true)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboardAutoDetect()
    }

    private fun checkClipboardAutoDetect() {
        val settings = settingsViewModel.uiState.value
        if (!settings.clipboardAutoDetect) return

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard?.hasPrimaryClip() == true &&
            clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
        ) {
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.contains("instagram.com/reel/") || text.contains("instagram.com/p/")) {
                homeViewModel.onPaste(text)
            }
        }
    }
}

