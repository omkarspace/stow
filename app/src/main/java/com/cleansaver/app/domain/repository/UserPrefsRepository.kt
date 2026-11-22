package com.cleansaver.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPrefsRepository {
    val themeMode: Flow<String>
    val clipboardAutoDetect: Flow<Boolean>
    val fairUseAccepted: Flow<Boolean>
    suspend fun setThemeMode(mode: String)
    suspend fun setClipboardAutoDetect(enabled: Boolean)
    suspend fun setFairUseAccepted(accepted: Boolean)
}

