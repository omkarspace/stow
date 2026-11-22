package com.cleansaver.app.data.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.cleansaver.app.data.local.DownloadedMediaDao
import com.cleansaver.app.data.local.MediaStoreWriter
import kotlinx.serialization.json.Json

class CleanSaverWorkerFactory(
    private val mediaStoreWriter: MediaStoreWriter,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val json: Json
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            MediaDownloadWorker::class.java.name -> {
                MediaDownloadWorker(
                    context = appContext,
                    params = workerParameters,
                    mediaStoreWriter = mediaStoreWriter,
                    downloadedMediaDao = downloadedMediaDao,
                    json = json
                )
            }
            else -> null
        }
    }
}

