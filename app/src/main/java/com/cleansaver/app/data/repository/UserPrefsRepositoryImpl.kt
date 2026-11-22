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
    }

    override val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "SYSTEM"
    }

    override val clipboardAutoDetect: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CLIPBOARD_AUTODETECT] ?: false
    }

    override val fairUseAccepted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FAIR_USE_ACCEPTED] ?: false
    }

    override suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    override suspend fun setClipboardAutoDetect(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CLIPBOARD_AUTODETECT] = enabled
        }
    }

    override suspend fun setFairUseAccepted(accepted: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_FAIR_USE_ACCEPTED] = accepted
        }
    }
}

