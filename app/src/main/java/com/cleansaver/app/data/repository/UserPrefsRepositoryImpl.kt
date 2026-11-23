package com.cleansaver.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cleansaver.app.domain.repository.UserPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPrefsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : UserPrefsRepository {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_CLIPBOARD_AUTODETECT = booleanPreferencesKey("clipboard_autodetect")
        val KEY_FAIR_USE_ACCEPTED = booleanPreferencesKey("fair_use_accepted")
        val KEY_INSTANT_SHARE_SAVE = booleanPreferencesKey("instant_share_save")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val KEY_VAULT_LOCK = booleanPreferencesKey("vault_lock_enabled")
        val KEY_QUALITY_PREF = stringPreferencesKey("quality_preference")
        val KEY_FILE_NAMING = stringPreferencesKey("file_naming_pattern")
        val KEY_CUSTOM_ALBUM = stringPreferencesKey("custom_album_name")
    }

    override val themeMode: Flow<String> = dataStore.data.map { it[KEY_THEME_MODE] ?: "SYSTEM" }
    override val clipboardAutoDetect: Flow<Boolean> = dataStore.data.map { it[KEY_CLIPBOARD_AUTODETECT] ?: false }
    override val fairUseAccepted: Flow<Boolean> = dataStore.data.map { it[KEY_FAIR_USE_ACCEPTED] ?: false }
    override val instantShareSave: Flow<Boolean> = dataStore.data.map { it[KEY_INSTANT_SHARE_SAVE] ?: false }
    override val wifiOnlyDownloads: Flow<Boolean> = dataStore.data.map { it[KEY_WIFI_ONLY] ?: false }
    override val vaultLockEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_VAULT_LOCK] ?: false }
    override val qualityPreference: Flow<String> = dataStore.data.map { it[KEY_QUALITY_PREF] ?: "BEST" }
    override val fileNamingPattern: Flow<String> = dataStore.data.map { it[KEY_FILE_NAMING] ?: "DEFAULT" }
    override val customAlbumName: Flow<String> = dataStore.data.map { it[KEY_CUSTOM_ALBUM] ?: "Stow" }

    override suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    override suspend fun setClipboardAutoDetect(enabled: Boolean) {
        dataStore.edit { it[KEY_CLIPBOARD_AUTODETECT] = enabled }
    }

    override suspend fun setFairUseAccepted(accepted: Boolean) {
        dataStore.edit { it[KEY_FAIR_USE_ACCEPTED] = accepted }
    }

    override suspend fun setInstantShareSave(enabled: Boolean) {
        dataStore.edit { it[KEY_INSTANT_SHARE_SAVE] = enabled }
    }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        dataStore.edit { it[KEY_WIFI_ONLY] = enabled }
    }

    override suspend fun setVaultLockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_VAULT_LOCK] = enabled }
    }

    override suspend fun setQualityPreference(quality: String) {
        dataStore.edit { it[KEY_QUALITY_PREF] = quality }
    }

    override suspend fun setFileNamingPattern(pattern: String) {
        dataStore.edit { it[KEY_FILE_NAMING] = pattern }
    }

    override suspend fun setCustomAlbumName(albumName: String) {
        val sanitized = albumName.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "Stow" }
        dataStore.edit { it[KEY_CUSTOM_ALBUM] = sanitized }
    }
}
