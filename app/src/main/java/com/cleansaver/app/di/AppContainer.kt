package com.cleansaver.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.cleansaver.app.data.engine.DownloadQueueManager
import com.cleansaver.app.data.engine.YtDlpEngine
import com.cleansaver.app.data.extractor.EmbedHttpExtractor
import com.cleansaver.app.data.extractor.EphemeralWebViewExtractor
import com.cleansaver.app.data.extractor.MediaExtractorChain
import com.cleansaver.app.data.extractor.MultiPlatformExtractor
import com.cleansaver.app.data.local.CleanSaverDatabase
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.MediaStoreWriter
import com.cleansaver.app.data.repository.LibraryRepositoryImpl
import com.cleansaver.app.data.repository.MediaRepositoryImpl
import com.cleansaver.app.data.repository.RemoteConfigRepositoryImpl
import com.cleansaver.app.data.repository.UserPrefsRepositoryImpl
import com.cleansaver.app.data.worker.CleanSaverWorkerFactory
import com.cleansaver.app.domain.repository.LibraryRepository
import com.cleansaver.app.domain.repository.MediaRepository
import com.cleansaver.app.domain.repository.RemoteConfigRepository
import com.cleansaver.app.domain.repository.UserPrefsRepository
import com.cleansaver.app.domain.usecase.DownloadMediaUseCase
import com.cleansaver.app.domain.usecase.ExtractMediaUseCase
import com.cleansaver.app.domain.usecase.RefreshConfigUseCase
import com.cleansaver.app.domain.usecase.ValidateUrlUseCase
import com.cleansaver.app.util.AppUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stow_prefs")

class AppContainer(private val context: Context) {

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    val appVersionName: String by lazy {
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val appUpdateChecker: AppUpdateChecker by lazy {
        AppUpdateChecker(okHttpClient, json)
    }

    val database: CleanSaverDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            CleanSaverDatabase::class.java,
            "stow.db"
        ).fallbackToDestructiveMigration().build()
    }

    val downloadedMediaDao: DownloadedMediaDao by lazy {
        database.downloadedMediaDao()
    }

    val dataStore: DataStore<Preferences> by lazy {
        context.dataStore
    }

    val userPrefsRepository: UserPrefsRepository by lazy {
        UserPrefsRepositoryImpl(dataStore)
    }

    val remoteConfigRepository: RemoteConfigRepository by lazy {
        RemoteConfigRepositoryImpl(context, dataStore, okHttpClient, json)
    }

    val embedHttpExtractor: EmbedHttpExtractor by lazy {
        EmbedHttpExtractor(okHttpClient)
    }

    val ephemeralWebViewExtractor: EphemeralWebViewExtractor by lazy {
        EphemeralWebViewExtractor(context)
    }

    val multiPlatformExtractor: MultiPlatformExtractor by lazy {
        MultiPlatformExtractor(okHttpClient, json)
    }

    val mediaExtractorChain: MediaExtractorChain by lazy {
        MediaExtractorChain(
            remoteConfigRepository = remoteConfigRepository,
            strategies = mapOf(
                embedHttpExtractor.id to embedHttpExtractor,
                ephemeralWebViewExtractor.id to ephemeralWebViewExtractor
            ),
            multiPlatformExtractor = multiPlatformExtractor
        )
    }

    val mediaStoreWriter: MediaStoreWriter by lazy {
        MediaStoreWriter(context, okHttpClient)
    }

    val ytDlpEngine: YtDlpEngine by lazy {
        YtDlpEngine(
            context = context.applicationContext,
            mediaStoreWriter = mediaStoreWriter,
            downloadedMediaDao = downloadedMediaDao,
            userPrefsRepository = userPrefsRepository
        )
    }

    val downloadQueueManager: DownloadQueueManager by lazy {
        DownloadQueueManager(
            ytDlpEngine = ytDlpEngine,
            applicationScope = applicationScope
        )
    }

    val validateUrlUseCase: ValidateUrlUseCase by lazy {
        ValidateUrlUseCase()
    }

    val mediaRepository: MediaRepository by lazy {
        MediaRepositoryImpl(
            context = context,
            extractorChain = mediaExtractorChain,
            mediaStoreWriter = mediaStoreWriter,
            downloadedMediaDao = downloadedMediaDao,
            userPrefsRepository = userPrefsRepository,
            okHttpClient = okHttpClient,
            validateUrlUseCase = validateUrlUseCase,
            applicationScope = applicationScope
        )
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepositoryImpl(context, downloadedMediaDao)
    }

    val extractMediaUseCase: ExtractMediaUseCase by lazy {
        ExtractMediaUseCase(mediaRepository)
    }

    val downloadMediaUseCase: DownloadMediaUseCase by lazy {
        DownloadMediaUseCase(mediaRepository)
    }

    val refreshConfigUseCase: RefreshConfigUseCase by lazy {
        RefreshConfigUseCase(remoteConfigRepository)
    }

    val workerFactory: CleanSaverWorkerFactory by lazy {
        CleanSaverWorkerFactory(mediaStoreWriter, downloadedMediaDao, json)
    }
}
