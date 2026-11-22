package com.cleansaver.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cleansaver.app.data.remote.dto.ParserConfigDto
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RemoteConfigRepositoryImpl(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : RemoteConfigRepository {

    companion object {
        // High-availability candidate endpoints with automatic fallback
        val CONFIG_URLS = listOf(
            "https://cleansaver.github.io/config/config.json",
            "https://omkarspace.github.io/cleansaver/config/config.json",
            "https://raw.githubusercontent.com/omkarspace/cleansaver/main/config/config.json"
        )
        private const val SUPPORTED_SCHEMA_VERSION = 1

        val KEY_CONFIG_JSON = stringPreferencesKey("config_json")
        val KEY_CONFIG_ETAG = stringPreferencesKey("config_etag")
        val KEY_CONFIG_LAST_SYNC = longPreferencesKey("config_last_sync_ms")
    }

    override suspend fun getActiveConfig(forceRefresh: Boolean): ParserConfigDto {
        val prefs = dataStore.data.first()
        val cachedJson = prefs[KEY_CONFIG_JSON]
        val cachedEtag = prefs[KEY_CONFIG_ETAG] ?: ""
        val lastSyncMs = prefs[KEY_CONFIG_LAST_SYNC] ?: 0L
        val now = System.currentTimeMillis()

        val cachedConfig = parseConfigOrNull(cachedJson)
        val ttlMs = (cachedConfig?.configTtlHours ?: 24) * 3600_000L

        if (!forceRefresh && cachedConfig != null && (now - lastSyncMs < ttlMs)) {
            return cachedConfig
        }

        val client = okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        // Attempt each candidate endpoint in order
        for (url in CONFIG_URLS) {
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .get()

                if (cachedEtag.isNotEmpty()) {
                    requestBuilder.header("If-None-Match", cachedEtag)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 304 && cachedConfig != null) {
                        dataStore.edit { it[KEY_CONFIG_LAST_SYNC] = now }
                        return cachedConfig
                    }

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty body")
                        val newConfig = json.decodeFromString<ParserConfigDto>(responseBody)

                        if (newConfig.schemaVersion <= SUPPORTED_SCHEMA_VERSION) {
                            val newEtag = response.header("ETag") ?: ""
                            dataStore.edit { mutablePrefs ->
                                mutablePrefs[KEY_CONFIG_JSON] = responseBody
                                mutablePrefs[KEY_CONFIG_ETAG] = newEtag
                                mutablePrefs[KEY_CONFIG_LAST_SYNC] = now
                            }
                            return newConfig
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next endpoint fallback
            }
        }

        return cachedConfig ?: loadBundledDefaultConfig()
    }

    override suspend fun refreshConfig(force: Boolean): Result<Unit> {
        return try {
            getActiveConfig(forceRefresh = force)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseConfigOrNull(rawJson: String?): ParserConfigDto? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            json.decodeFromString<ParserConfigDto>(rawJson)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadBundledDefaultConfig(): ParserConfigDto {
        return context.assets.open("default_config.json").use { stream ->
            InputStreamReader(stream).use { reader ->
                json.decodeFromString<ParserConfigDto>(reader.readText())
            }
        }
    }
}
