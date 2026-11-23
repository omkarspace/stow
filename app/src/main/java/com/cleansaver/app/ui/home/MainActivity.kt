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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.cleansaver.app.CleanSaverApplication
import com.cleansaver.app.ui.components.DisclaimerBottomSheet
import com.cleansaver.app.ui.components.StowActionButton
import com.cleansaver.app.ui.components.StowDarkBlock
import com.cleansaver.app.ui.library.LibraryScreen
import com.cleansaver.app.ui.library.LibraryViewModel
import com.cleansaver.app.ui.settings.SettingsScreen
import com.cleansaver.app.ui.settings.SettingsViewModel
import com.cleansaver.app.ui.theme.CleanSaverTheme
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
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
                else -> false // Default to signature BRIK bone-white shell + deep pine blocks
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        StowFloatingBottomBar(
                            selectedTab = selectedTab,
                            onSelectTab = { selectedTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> HomeScreen(viewModel = homeViewModel, libraryViewModel = libraryViewModel)
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
            .setTitle("Unlock Stow Library")
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

@Composable
private fun StowFloatingBottomBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StowNavItem(
                    label = "STOW",
                    icon = if (selectedTab == 0) Icons.Filled.Download else Icons.Outlined.Download,
                    selected = selectedTab == 0,
                    onClick = { onSelectTab(0) },
                    modifier = Modifier.weight(1f)
                )
                StowNavItem(
                    label = "LIBRARY",
                    icon = if (selectedTab == 1) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                    selected = selectedTab == 1,
                    onClick = { onSelectTab(1) },
                    modifier = Modifier.weight(1f)
                )
                StowNavItem(
                    label = "SETTINGS",
                    icon = if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                    selected = selectedTab == 2,
                    onClick = { onSelectTab(2) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StowNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) StowLavender else androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) StowPineDark else StowInkMutedOnDark,
                modifier = Modifier.size(18.dp)
            )
            if (selected) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = StowPineDark
                )
            }
        }
    }
}

@Composable
private fun VaultLockedView(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StowDarkBlock {
            Surface(
                shape = CircleShape,
                color = StowLavender,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = StowPineDark,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Vault locked.",
                style = MaterialTheme.typography.headlineLarge,
                color = StowInkOnDark
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Verify your identity to access your saved media.",
                style = MaterialTheme.typography.bodyMedium,
                color = StowInkMutedOnDark
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        StowActionButton(
            text = "Unlock Library",
            onClick = onUnlockClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
