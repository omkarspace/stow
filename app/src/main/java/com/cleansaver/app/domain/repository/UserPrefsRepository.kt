package com.cleansaver.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPrefsRepository {
    val themeMode: Flow<String>
    val clipboardAutoDetect: Flow<Boolean>
    val fairUseAccepted: Flow<Boolean>
    val instantShareSave: Flow<Boolean>
    val wifiOnlyDownloads: Flow<Boolean>
    val vaultLockEnabled: Flow<Boolean>
    val qualityPreference: Flow<String> // "BEST" | "DATA_SAVER"
    val fileNamingPattern: Flow<String> // "DEFAULT" | "CREATOR_DATE"
    val customAlbumName: Flow<String>

    suspend fun setThemeMode(mode: String)
    suspend fun setClipboardAutoDetect(enabled: Boolean)
    suspend fun setFairUseAccepted(accepted: Boolean)
    suspend fun setInstantShareSave(enabled: Boolean)
    suspend fun setWifiOnlyDownloads(enabled: Boolean)
    suspend fun setVaultLockEnabled(enabled: Boolean)
    suspend fun setQualityPreference(quality: String)
    suspend fun setFileNamingPattern(pattern: String)
    suspend fun setCustomAlbumName(albumName: String)
}
