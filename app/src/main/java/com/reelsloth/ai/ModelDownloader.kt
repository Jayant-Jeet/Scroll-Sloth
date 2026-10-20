package com.reelsloth.ai

import com.reelsloth.data.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** The system download service, behind an interface so [ModelDownloader] can be tested. */
interface Downloads {
    fun enqueue(request: DownloadRequest): Long
    fun query(id: Long): DownloadInfo?
    /** Cancels the download and deletes its partial file. */
    fun remove(id: Long)
}

data class DownloadRequest(
    val url: String,
    val destination: File,
    val title: String,
    val description: String,
    val allowMetered: Boolean,
)

data class DownloadInfo(
    val status: DownloadStatus,
    val bytesSoFar: Long,
    val totalBytes: Long,
    val waitingFor: Waiting? = null,
)

enum class DownloadStatus { PENDING, RUNNING, PAUSED, SUCCESSFUL, FAILED }

enum class Waiting { WIFI, NETWORK, RETRY, OTHER }

sealed interface ModelState {
    data object Checking : ModelState
    data object NotDownloaded : ModelState
    data class Downloading(val bytesSoFar: Long, val totalBytes: Long, val waitingFor: Waiting? = null) : ModelState
    data object Verifying : ModelState
    data object Ready : ModelState
    data class Failed(val reason: FailReason) : ModelState
}

enum class FailReason { NOT_ENOUGH_SPACE, DOWNLOAD_FAILED, CHECKSUM_MISMATCH }

/** What to do on app start, decided from the file, the user's wish and any system download. */
enum class StartupAction { READY, CLEAN_UP, WATCH, VERIFY, RESTART }

fun startupAction(fileReady: Boolean, wanted: Boolean, download: DownloadStatus?): StartupAction = when {
    fileReady -> StartupAction.READY
    !wanted -> StartupAction.CLEAN_UP
    download == DownloadStatus.SUCCESSFUL -> StartupAction.VERIFY
    download == DownloadStatus.PENDING || download == DownloadStatus.RUNNING || download == DownloadStatus.PAUSED ->
        StartupAction.WATCH
    else -> StartupAction.RESTART
}

/**
 * Downloads the model with the system download service, which resumes after network drops and
 * keeps going when the app is closed. Wi-Fi only unless the user allows mobile data.
 *
 * On every app start [checkOnStartup] confirms the model file still matches its checksum marker.
 * A missing or broken file is deleted and, if the user wanted the model, downloaded again.
 */
class ModelDownloader(
    private val files: ModelFiles,
    private val downloads: Downloads,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val freeSpace: () -> Long = { files.directory.usableSpace },
    private val pollIntervalMs: Long = 1_000,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    val state: StateFlow<ModelState> = _state.asStateFlow()
    val spec: ModelSpec get() = files.spec
    private var watchJob: Job? = null

    /** The verified model file, or null. Cheap: compares the marker with the file, no hashing. */
    fun readyModelFile(): File? = files.modelFile.takeIf { files.isReady() }

    suspend fun checkOnStartup() = mutex.withLock {
        val id = settings.modelDownloadId.first()
        val info = id?.let { downloads.query(it) }
        val ready = withContext(ioDispatcher) { files.isReady() }
        when (startupAction(ready, settings.modelWanted.first(), info?.status)) {
            StartupAction.READY -> {
                id?.let { forget(it) }
                _state.value = ModelState.Ready
            }
            StartupAction.CLEAN_UP -> {
                id?.let { forget(it) }
                withContext(ioDispatcher) { files.deleteAll() }
                _state.value = ModelState.NotDownloaded
            }
            StartupAction.WATCH -> {
                withContext(ioDispatcher) { files.deleteInstalled() }
                watch(id!!)
            }
            StartupAction.VERIFY -> verifyLocked(id!!)
            StartupAction.RESTART -> {
                id?.let { forget(it) }
                withContext(ioDispatcher) { files.deleteInstalled() }
                enqueueLocked()
            }
        }
    }

    suspend fun download() = mutex.withLock {
        if (withContext(ioDispatcher) { files.isReady() }) {
            _state.value = ModelState.Ready
            return@withLock
        }
        settings.setModelWanted(true)
        val existing = settings.modelDownloadId.first()
        val status = existing?.let { downloads.query(it)?.status }
        if (existing != null && status != null && status != DownloadStatus.FAILED) {
            if (status == DownloadStatus.SUCCESSFUL) verifyLocked(existing) else watch(existing)
            return@withLock
        }
        existing?.let { forget(it) }
        enqueueLocked()
    }

    /** Cancels any download and deletes the model. Also used for "Cancel". */
    suspend fun delete() = mutex.withLock {
        watchJob?.cancel()
        watchJob = null
        settings.setModelWanted(false)
        settings.modelDownloadId.first()?.let { forget(it) }
        withContext(ioDispatcher) { files.deleteAll() }
        _state.value = ModelState.NotDownloaded
    }

    private suspend fun enqueueLocked() {
        withContext(ioDispatcher) {
            files.directory.mkdirs()
            files.partFile.delete()
        }
        if (freeSpace() < spec.sizeBytes + SPACE_MARGIN_BYTES) {
            _state.value = ModelState.Failed(FailReason.NOT_ENOUGH_SPACE)
            return
        }
        val id = downloads.enqueue(
            DownloadRequest(
                url = spec.url,
                destination = files.partFile,
                title = "Scroll Sloth language model",
                description = spec.displayName,
                allowMetered = settings.modelAllowMetered.first(),
            ),
        )
        settings.setModelDownloadId(id)
        _state.value = ModelState.Downloading(0, spec.sizeBytes)
        watch(id)
    }

    private fun watch(id: Long) {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                val info = downloads.query(id)
                when (info?.status) {
                    DownloadStatus.SUCCESSFUL -> {
                        mutex.withLock { if (settings.modelDownloadId.first() == id) verifyLocked(id) }
                        return@launch
                    }
                    null, DownloadStatus.FAILED -> {
                        mutex.withLock {
                            if (settings.modelDownloadId.first() == id) {
                                forget(id)
                                _state.value = ModelState.Failed(FailReason.DOWNLOAD_FAILED)
                            }
                        }
                        return@launch
                    }
                    else -> _state.value = ModelState.Downloading(
                        bytesSoFar = info.bytesSoFar,
                        totalBytes = info.totalBytes.takeIf { it > 0 } ?: spec.sizeBytes,
                        waitingFor = info.waitingFor.takeIf { info.status == DownloadStatus.PAUSED },
                    )
                }
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun verifyLocked(id: Long) {
        _state.value = ModelState.Verifying
        val ok = withContext(ioDispatcher) { files.install() }
        forget(id)
        _state.value = if (ok) ModelState.Ready else ModelState.Failed(FailReason.CHECKSUM_MISMATCH)
    }

    /** Drops the system download entry. Its file is either installed already or no longer wanted. */
    private suspend fun forget(id: Long) {
        downloads.remove(id)
        settings.setModelDownloadId(null)
    }

    companion object {
        /** Keep some room free after the download so the phone is not left completely full. */
        const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024
    }
}
