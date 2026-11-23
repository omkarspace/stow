package com.cleansaver.app.ui.home

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.cleansaver.app.CleanSaverApplication
import com.cleansaver.app.ui.components.DisclaimerBottomSheet
import com.cleansaver.app.ui.library.LibraryScreen
import com.cleansaver.app.ui.library.LibraryViewModel
import com.cleansaver.app.ui.settings.SettingsScreen
import com.cleansaver.app.ui.settings.SettingsViewModel
import com.cleansaver.app.ui.theme.CleanSaverTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_QUICK_PASTE = "extra_quick_paste"
    }

    private var lastAutoPastedClip: String? = null
    private var pendingQuickPaste: Boolean = false

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
        pendingQuickPaste = intent?.getBooleanExtra(EXTRA_QUICK_PASTE, false) == true

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val darkTheme = when (settingsState.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Permission result handled gracefully */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            CleanSaverTheme(darkTheme = darkTheme) {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                var vaultUnlocked by remember { mutableStateOf(false) }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()

                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        Column {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 0) Icons.Filled.Download else Icons.Outlined.Download,
                                            contentDescription = "Save"
                                        )
                                    },
                                    label = { Text("Save", style = MaterialTheme.typography.labelSmall) },
                                    colors = navItemColors
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 1) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                            contentDescription = "Library"
                                        )
                                    },
                                    label = { Text("Library", style = MaterialTheme.typography.labelSmall) },
                                    colors = navItemColors
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                                            contentDescription = "Settings"
                                        )
                                    },
                                    label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
                                    colors = navItemColors
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> HomeScreen(viewModel = homeViewModel)
                            1 -> {
                                if (settingsState.vaultLockEnabled && !vaultUnlocked) {
                                    VaultLockedView(
                                        onUnlockClick = {
                                            authenticateVault { vaultUnlocked = true }
                                        }
                                    )
                                } else {
                                    LibraryScreen(viewModel = libraryViewModel)
                                }
                            }
                            2 -> SettingsScreen(viewModel = settingsViewModel)
                        }
                    }

                    if (settingsState.isLoaded && !settingsState.fairUseAccepted) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QUICK_PASTE, false)) {
            pendingQuickPaste = true
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            checkClipboardAutoDetect(forceFromTile = pendingQuickPaste)
            pendingQuickPaste = false
        }
    }

    private fun authenticateVault(onSuccess: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock CleanSaver Library")
            .setSubtitle("Verify your identity to access saved media")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun checkClipboardAutoDetect(forceFromTile: Boolean) {
        val settings = settingsViewModel.uiState.value
        if (!forceFromTile && !settings.clipboardAutoDetect) return

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard?.hasPrimaryClip() == true &&
            clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
        ) {
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
            val isSupportedLink = text.contains("instagram.com/", ignoreCase = true) ||
                text.contains("instagr.am/", ignoreCase = true) ||
                text.contains("threads.net/", ignoreCase = true) ||
                text.contains("threads.com/", ignoreCase = true) ||
                text.contains("x.com/", ignoreCase = true) ||
                text.contains("twitter.com/", ignoreCase = true) ||
                text.contains("reddit.com/", ignoreCase = true) ||
                text.contains("pinterest.", ignoreCase = true) ||
                text.contains("pin.it/", ignoreCase = true) ||
                text.contains("tiktok.com/", ignoreCase = true) ||
                text.contains("youtube.com/shorts/", ignoreCase = true) ||
                text.contains("youtu.be/", ignoreCase = true)

            if (isSupportedLink && (forceFromTile || text != lastAutoPastedClip)) {
                lastAutoPastedClip = text
                homeViewModel.onPaste(text)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun VaultLockedView(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Library Locked",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Authenticate to view your saved media.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onUnlockClick,
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text("Unlock", style = MaterialTheme.typography.labelLarge)
        }
    }
}
