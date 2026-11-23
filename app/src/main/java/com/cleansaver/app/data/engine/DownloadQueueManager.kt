package com.cleansaver.app.data.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

enum class QueueTaskStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class QueueTask(
    val id: String = UUID.randomUUID().toString(),
    val mediaInfo: StowMediaInfo,
    val format: StowFormatOption,
    val embedSubtitles: Boolean = false,
    val embedThumbnail: Boolean = true,
    val status: QueueTaskStatus = QueueTaskStatus.QUEUED,
    val percent: Int = 0,
    val speed: String = "",
    val etaSec: Long = -1L,
    val savedUri: String? = null,
    val savedFileName: String? = null,
    val errorMessage: String? = null
)

class DownloadQueueManager(
    private val ytDlpEngine: YtDlpEngine,
    private val applicationScope: CoroutineScope
) {
    private val semaphore = Semaphore(permits = 2)
    private val _tasks = MutableStateFlow<List<QueueTask>>(emptyList())
    val tasks: StateFlow<List<QueueTask>> = _tasks.asStateFlow()

    fun enqueue(
        mediaInfo: StowMediaInfo,
        format: StowFormatOption,
        embedSubtitles: Boolean = false,
        embedThumbnail: Boolean = true
    ): QueueTask {
        val task = QueueTask(
            mediaInfo = mediaInfo,
            format = format,
            embedSubtitles = embedSubtitles,
            embedThumbnail = embedThumbnail
        )
        _tasks.update { listOf(task) + it }
        startTask(task)
        return task
    }

    private fun startTask(task: QueueTask) {
        applicationScope.launch(Dispatchers.IO) {
            semaphore.withPermit {
                val current = _tasks.value.find { it.id == task.id } ?: return@withPermit
                if (current.status == QueueTaskStatus.CANCELLED) return@withPermit

                updateTask(task.id) { it.copy(status = QueueTaskStatus.DOWNLOADING, errorMessage = null) }

                val result = ytDlpEngine.download(
                    taskId = task.id,
                    mediaInfo = task.mediaInfo,
                    format = task.format,
                    embedSubtitles = task.embedSubtitles,
                    embedThumbnail = task.embedThumbnail
                ) { pct, speed, eta ->
                    updateTask(task.id) {
                        if (it.status == QueueTaskStatus.CANCELLED) it
                        else it.copy(percent = pct, speed = speed, etaSec = eta)
                    }
                }

                val latest = _tasks.value.find { it.id == task.id }
                if (latest?.status == QueueTaskStatus.CANCELLED) return@withPermit

                if (result.isSuccess) {
                    val entity = result.getOrThrow()
                    updateTask(task.id) {
                        it.copy(
                            status = QueueTaskStatus.COMPLETED,
                            percent = 100,
                            speed = "",
                            etaSec = 0,
                            savedUri = entity.mediaStoreUri,
                            savedFileName = entity.fileName
                        )
                    }
                } else {
                    updateTask(task.id) {
                        it.copy(
                            status = QueueTaskStatus.FAILED,
                            speed = "",
                            errorMessage = result.exceptionOrNull()?.message ?: "Download failed"
                        )
                    }
                }
            }
        }
    }

    fun cancel(taskId: String) {
        updateTask(taskId) { it.copy(status = QueueTaskStatus.CANCELLED, speed = "") }
        ytDlpEngine.cancel(taskId)
    }

    fun retry(taskId: String) {
        val existing = _tasks.value.find { it.id == taskId } ?: return
        val reset = existing.copy(status = QueueTaskStatus.QUEUED, percent = 0, speed = "", errorMessage = null)
        updateTask(taskId) { reset }
        startTask(reset)
    }

    fun remove(taskId: String) {
        ytDlpEngine.cancel(taskId)
        _tasks.update { list -> list.filterNot { it.id == taskId } }
    }

    fun clearCompleted() {
        _tasks.update { list ->
            list.filter { it.status == QueueTaskStatus.QUEUED || it.status == QueueTaskStatus.DOWNLOADING }
        }
    }

    private fun updateTask(taskId: String, transform: (QueueTask) -> QueueTask) {
        _tasks.update { list -> list.map { if (it.id == taskId) transform(it) else it } }
    }
}
