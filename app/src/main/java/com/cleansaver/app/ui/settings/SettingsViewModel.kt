package com.cleansaver.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.domain.repository.LibraryRepository
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import com.cleansaver.app.domain.repository.UserPrefsRepository
import com.cleansaver.app.ui.state.SettingsUiState
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
    private val appVersionName: String
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshMessage = MutableStateFlow<String?>(null)
    private val _refreshSuccess = MutableStateFlow(true)

    val uiState: StateFlow<SettingsUiState> = combine(
        userPrefsRepository.themeMode,
        userPrefsRepository.clipboardAutoDetect,
        userPrefsRepository.fairUseAccepted,
        _isRefreshing,
        _refreshMessage
    ) { theme, clipboard, fairUse, refreshing, msg ->
        SettingsUiState(
            themeMode = theme,
            clipboardAutoDetect = clipboard,
            fairUseAccepted = fairUse,
            isLoaded = true,
            isRefreshingConfig = refreshing,
            configRefreshMessage = msg,
            configRefreshSuccess = _refreshSuccess.value,
            appVersion = appVersionName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoaded = false, appVersion = appVersionName)
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPrefsRepository.setThemeMode(mode)
        }
    }

    fun setClipboardAutoDetect(enabled: Boolean) {
        viewModelScope.launch {
            userPrefsRepository.setClipboardAutoDetect(enabled)
        }
    }

    fun setFairUseAccepted(accepted: Boolean) {
        viewModelScope.launch {
            userPrefsRepository.setFairUseAccepted(accepted)
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

    fun clearAllData() {
        viewModelScope.launch {
            libraryRepository.clearHistory()
        }
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
                        appVersionName = container.appVersionName
                    ) as T
                }
            }
    }
}
