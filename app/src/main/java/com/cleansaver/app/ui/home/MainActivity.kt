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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.cleansaver.app.CleanSaverApplication
import com.cleansaver.app.data.engine.QueueTaskStatus
import com.cleansaver.app.ui.components.DisclaimerBottomSheet
import com.cleansaver.app.ui.components.StowActionButton
import com.cleansaver.app.ui.components.StowDarkBlock
import com.cleansaver.app.ui.components.StowFloatingSnackbarHost
import com.cleansaver.app.ui.library.LibraryScreen
import com.cleansaver.app.ui.library.LibraryViewModel
import com.cleansaver.app.ui.queue.QueueScreen
import com.cleansaver.app.ui.settings.SettingsScreen
import com.cleansaver.app.ui.settings.SettingsViewModel
import com.cleansaver.app.ui.theme.CleanSaverTheme
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark
import com.cleansaver.app.ui.theme.StowPineMuted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_QUICK_PASTE = "extra_quick_paste"
    }

    private var lastAutoPastedClip: String? = null
    private var pendingQuickPaste: Boolean = false
    private val clipboardSuggestionFlow = MutableStateFlow<String?>(null)
    private val httpUrlRegex = Regex("""https?://[^\s/$.?#].[^\s]*""", RegexOption.IGNORE_CASE)

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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingQuickPaste = intent?.getBooleanExtra(EXTRA_QUICK_PASTE, false) == true

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val clipboardSuggestion by clipboardSuggestionFlow.collectAsState()
            val queueTasks by homeViewModel.queueTasks.collectAsState()
            val activeQueueCount = queueTasks.count {
                it.status == QueueTaskStatus.DOWNLOADING || it.status == QueueTaskStatus.QUEUED
            }
            val darkTheme = when (settingsState.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> false
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
            ) { }

            LaunchedEffect(settingsState.isLoaded, settingsState.fairUseAccepted) {
                if (settingsState.isLoaded && settingsState.fairUseAccepted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                val snackbarHostState = remember { SnackbarHostState() }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { StowFloatingSnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        StowFloatingBottomBar(
                            selectedTab = selectedTab,
                            activeQueueCount = activeQueueCount,
                            onSelectTab = { selectedTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(140))
                            },
                            label = "tab_transition"
                        ) { tab ->
                            when (tab) {
                                0 -> HomeScreen(
                                    viewModel = homeViewModel,
                                    libraryViewModel = libraryViewModel,
                                    snackbarHostState = snackbarHostState,
                                    clipboardSuggestion = clipboardSuggestion,
                                    onUseClipboardSuggestion = { url ->
                                        clipboardSuggestionFlow.value = null
                                        lastAutoPastedClip = url
                                        homeViewModel.onPaste(url)
                                    },
                                    onDismissClipboardSuggestion = {
                                        lastAutoPastedClip = clipboardSuggestion
                                        clipboardSuggestionFlow.value = null
                                    },
                                    onOpenQueue = { selectedTab = 1 },
                                    onOpenLibrary = { selectedTab = 2 }
                                )
                                1 -> QueueScreen(homeViewModel = homeViewModel)
                                2 -> {
                                    if (settingsState.vaultLockEnabled && !vaultUnlocked) {
                                        VaultLockedView(
                                            onUnlockClick = {
                                                authenticateVault { vaultUnlocked = true }
                                            }
                                        )
                                    } else {
                                        LibraryScreen(
                                            viewModel = libraryViewModel,
                                            snackbarHostState = snackbarHostState
                                        )
                                    }
                                }
                                3 -> SettingsScreen(
                                    viewModel = settingsViewModel,
                                    libraryViewModel = libraryViewModel,
                                    snackbarHostState = snackbarHostState
                                )
                            }
                        }
                    }

                    if (settingsState.isLoaded && !settingsState.fairUseAccepted) {
                        DisclaimerBottomSheet(
                            onAccept = {
                                settingsViewModel.setFairUseAccepted(true)
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
            inspectClipboard(forceFromTile = pendingQuickPaste)
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

    private fun inspectClipboard(forceFromTile: Boolean) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard?.hasPrimaryClip() != true ||
            clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true
        ) {
            return
        }

        val raw = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
        val url = httpUrlRegex.find(raw)?.value ?: return

        val settings = settingsViewModel.uiState.value
        if (forceFromTile || (settings.clipboardAutoDetect && url != lastAutoPastedClip)) {
            lastAutoPastedClip = url
            clipboardSuggestionFlow.value = null
            homeViewModel.onPaste(url)
        } else if (url != lastAutoPastedClip) {
            clipboardSuggestionFlow.value = url
        }
    }
}

@Composable
private fun StowFloatingBottomBar(
    selectedTab: Int,
    activeQueueCount: Int,
    onSelectTab: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, StowPineMuted.copy(alpha = 0.65f)),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StowNavItem(
                    label = "STOW",
                    icon = if (selectedTab == 0) Icons.Filled.Download else Icons.Outlined.Download,
                    selected = selectedTab == 0,
                    badgeCount = 0,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(0)
                    },
                    modifier = Modifier.weight(if (selectedTab == 0) 1.55f else 1f)
                )
                StowNavItem(
                    label = "QUEUE",
                    icon = if (selectedTab == 1) Icons.Filled.Queue else Icons.Outlined.Queue,
                    selected = selectedTab == 1,
                    badgeCount = activeQueueCount,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(1)
                    },
                    modifier = Modifier.weight(if (selectedTab == 1) 1.65f else 1f)
                )
                StowNavItem(
                    label = "LIBRARY",
                    icon = if (selectedTab == 2) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                    selected = selectedTab == 2,
                    badgeCount = 0,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(2)
                    },
                    modifier = Modifier.weight(if (selectedTab == 2) 1.75f else 1f)
                )
                StowNavItem(
                    label = "SETTINGS",
                    icon = if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                    selected = selectedTab == 3,
                    badgeCount = 0,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTab(3)
                    },
                    modifier = Modifier.weight(if (selectedTab == 3) 1.85f else 1f)
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
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) StowLavender else Color.Transparent,
        animationSpec = tween(200),
        label = "nav_bg"
    )
    Surface(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
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
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (badgeCount > 0) "$label $badgeCount" else label,
                    style = MaterialTheme.typography.labelSmall,
                    color = StowPineDark,
                    maxLines = 1
                )
            } else if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(shape = CircleShape, color = StowLavender) {
                    Text(
                        text = "$badgeCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = StowPineDark,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultLockedView(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
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
            Text("Vault locked.", style = MaterialTheme.typography.headlineLarge, color = StowInkOnDark)
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
