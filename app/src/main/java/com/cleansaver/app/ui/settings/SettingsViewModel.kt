package com.cleansaver.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cleansaver.app.data.engine.YtDlpEngine
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.domain.repository.LibraryRepository
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import com.cleansaver.app.domain.repository.UserPrefsRepository
import com.cleansaver.app.ui.state.SettingsUiState
import com.cleansaver.app.util.AppUpdateChecker
import com.cleansaver.app.util.ExtractionLogger
import com.cleansaver.app.util.ReleaseUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPrefsRepository: UserPrefsRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val libraryRepository: LibraryRepository,
    private val appUpdateChecker: AppUpdateChecker,
    private val ytDlpEngine: YtDlpEngine,
    private val appVersionName: String
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshMessage = MutableStateFlow<String?>(null)
    private val _refreshSuccess = MutableStateFlow(true)
    private val _isCheckingUpdate = MutableStateFlow(false)
    private val _updateInfo = MutableStateFlow<ReleaseUpdateInfo?>(null)
    private val _updateMessage = MutableStateFlow<String?>(null)
    private val _isUpdatingYtDlp = MutableStateFlow(false)
    private val _ytDlpMessage = MutableStateFlow<String?>(null)
    private val _ytDlpSuccess = MutableStateFlow(true)
    private val _logs = MutableStateFlow<List<String>>(emptyList())

    private data class CorePrefs(
        val theme: String,
        val clipboard: Boolean,
        val fairUse: Boolean,
        val instantShare: Boolean,
        val wifiOnly: Boolean
    )

    private data class AdvancedPrefs(
        val vaultLock: Boolean,
        val quality: String,
        val naming: String,
        val album: String
    )

    private data class ActionState(
        val isRefreshing: Boolean,
        val refreshMsg: String?,
        val isCheckingUpdate: Boolean,
        val updateInfo: ReleaseUpdateInfo?,
        val updateMsg: String?
    )

    private data class EngineState(
        val isUpdatingYtDlp: Boolean,
        val ytDlpMsg: String?,
        val ytDlpSuccess: Boolean,
        val logs: List<String>
    )

    private val corePrefsFlow = combine(
        userPrefsRepository.themeMode,
        userPrefsRepository.clipboardAutoDetect,
        userPrefsRepository.fairUseAccepted,
        userPrefsRepository.instantShareSave,
        userPrefsRepository.wifiOnlyDownloads
    ) { theme, clipboard, fairUse, instantShare, wifiOnly ->
        CorePrefs(theme, clipboard, fairUse, instantShare, wifiOnly)
    }

    private val advancedPrefsFlow = combine(
        userPrefsRepository.vaultLockEnabled,
        userPrefsRepository.qualityPreference,
        userPrefsRepository.fileNamingPattern,
        userPrefsRepository.customAlbumName
    ) { vault, quality, naming, album ->
        AdvancedPrefs(vault, quality, naming, album)
    }

    private val actionStateFlow = combine(
        _isRefreshing,
        _refreshMessage,
        _isCheckingUpdate,
        _updateInfo,
        _updateMessage
    ) { refreshing, msg, checkingUpd, updInfo, updMsg ->
        ActionState(refreshing, msg, checkingUpd, updInfo, updMsg)
    }

    private val engineStateFlow = combine(
        _isUpdatingYtDlp,
        _ytDlpMessage,
        _ytDlpSuccess,
        _logs
    ) { updating, msg, ok, logs ->
        EngineState(updating, msg, ok, logs)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        corePrefsFlow,
        advancedPrefsFlow,
        actionStateFlow,
        engineStateFlow
    ) { core, adv, act, eng ->
        SettingsUiState(
            themeMode = core.theme,
            clipboardAutoDetect = core.clipboard,
            fairUseAccepted = core.fairUse,
            instantShareSave = core.instantShare,
            wifiOnlyDownloads = core.wifiOnly,
            vaultLockEnabled = adv.vaultLock,
            qualityPreference = adv.quality,
            fileNamingPattern = adv.naming,
            customAlbumName = adv.album,
            isLoaded = true,
            isRefreshingConfig = act.isRefreshing,
            configRefreshMessage = act.refreshMsg,
            configRefreshSuccess = _refreshSuccess.value,
            isCheckingUpdate = act.isCheckingUpdate,
            updateInfo = act.updateInfo,
            updateCheckMessage = act.updateMsg,
            isUpdatingYtDlp = eng.isUpdatingYtDlp,
            ytDlpUpdateMessage = eng.ytDlpMsg,
            ytDlpUpdateSuccess = eng.ytDlpSuccess,
            diagnosticLogs = eng.logs,
            appVersion = appVersionName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoaded = false, appVersion = appVersionName)
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch { userPrefsRepository.setThemeMode(mode) }
    }

    fun setClipboardAutoDetect(enabled: Boolean) {
        viewModelScope.launch { userPrefsRepository.setClipboardAutoDetect(enabled) }
    }

    fun setFairUseAccepted(accepted: Boolean) {
        viewModelScope.launch { userPrefsRepository.setFairUseAccepted(accepted) }
    }

    fun setInstantShareSave(enabled: Boolean) {
        viewModelScope.launch { userPrefsRepository.setInstantShareSave(enabled) }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch { userPrefsRepository.setWifiOnlyDownloads(enabled) }
    }

    fun setVaultLockEnabled(enabled: Boolean) {
        viewModelScope.launch { userPrefsRepository.setVaultLockEnabled(enabled) }
    }

    fun setQualityPreference(quality: String) {
        viewModelScope.launch { userPrefsRepository.setQualityPreference(quality) }
    }

    fun setFileNamingPattern(pattern: String) {
        viewModelScope.launch { userPrefsRepository.setFileNamingPattern(pattern) }
    }

    fun setCustomAlbumName(album: String) {
        viewModelScope.launch { userPrefsRepository.setCustomAlbumName(album) }
    }

    fun updateYtDlpEngine() {
        viewModelScope.launch {
            _isUpdatingYtDlp.value = true
            _ytDlpMessage.value = null
            val result = ytDlpEngine.updateEngine()
            _isUpdatingYtDlp.value = false
            _ytDlpSuccess.value = result.isSuccess
            _ytDlpMessage.value = result.getOrElse {
                "Update failed: ${it.message ?: "check connection"}"
            }
        }
    }

    fun refreshParserConfig() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshMessage.value = null
            val result = remoteConfigRepository.refreshConfig(force = true)
            _isRefreshing.value = false
            _refreshSuccess.value = result.isSuccess
            _refreshMessage.value = if (result.isSuccess) {
                "Parser rules are up to date."
            } else {
                "Offline — using bundled fallback rules."
            }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateMessage.value = null
            val result = appUpdateChecker.checkForUpdate(appVersionName)
            _isCheckingUpdate.value = false
            if (result.isSuccess) {
                val info = result.getOrThrow()
                _updateInfo.value = info
                _updateMessage.value = if (info.isUpdateAvailable) {
                    "Update available: v${info.latestVersion}"
                } else {
                    "You're on the latest version (v$appVersionName)."
                }
            } else {
                _updateMessage.value = "Could not check GitHub Releases right now."
            }
        }
    }

    fun refreshDiagnosticLogs() {
        _logs.value = ExtractionLogger.getLogs()
    }

    fun clearDiagnosticLogs() {
        ExtractionLogger.clear()
        _logs.value = emptyList()
    }

    fun clearAllData() {
        viewModelScope.launch { libraryRepository.clearHistory() }
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        userPrefsRepository = container.userPrefsRepository,
                        remoteConfigRepository = container.remoteConfigRepository,
                        libraryRepository = container.libraryRepository,
                        appUpdateChecker = container.appUpdateChecker,
                        ytDlpEngine = container.ytDlpEngine,
                        appVersionName = container.appVersionName
                    ) as T
                }
            }
    }
}
