package com.halfbloodcoder.scrollsloth.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.halfbloodcoder.scrollsloth.data.SettingsStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val content = "a very small sloth model".toByteArray()
    private val spec = ModelSpec(
        displayName = "Test model",
        url = "https://example.invalid/model.litertlm",
        fileName = "model.litertlm",
        sizeBytes = content.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) },
        license = "Test",
        licenseUrl = "https://example.invalid",
    )

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private class FakeDownloads : Downloads {
        val requests = mutableListOf<DownloadRequest>()
        val infos = mutableMapOf<Long, DownloadInfo>()
        val removed = mutableListOf<Long>()
        private var nextId = 100L

        override fun enqueue(request: DownloadRequest): Long {
            requests += request
            return nextId++.also { infos[it] = DownloadInfo(DownloadStatus.PENDING, 0, -1) }
        }

        override fun query(id: Long) = infos[id]

        override fun remove(id: Long) {
            removed += id
            infos.remove(id)?.let { requests.lastOrNull()?.destination?.delete() }
        }
    }

    private lateinit var files: ModelFiles
    private val downloads = FakeDownloads()
    private val settings = SettingsStore(MemoryDataStore())
    private var space = Long.MAX_VALUE

    private fun TestScope.downloader(scope: CoroutineScope = backgroundScope): ModelDownloader {
        files = ModelFiles(File(temp.root, "models"), spec)
        return ModelDownloader(
            files = files,
            downloads = downloads,
            settings = settings,
            scope = scope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            freeSpace = { space },
            pollIntervalMs = 1_000,
        )
    }

    /** Pretends the system finished the download, with [bytes] written to the destination. */
    private fun finish(id: Long, bytes: ByteArray = content) {
        val destination = files.partFile
        destination.parentFile!!.mkdirs()
        destination.writeBytes(bytes)
        downloads.infos[id] = DownloadInfo(DownloadStatus.SUCCESSFUL, bytes.size.toLong(), bytes.size.toLong())
    }

    /** Starts a download, then drops that downloader as if the app process had been killed. */
    private suspend fun TestScope.killedAfterDownload() {
        val processScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        downloader(processScope).download()
        processScope.cancel()
    }

    private fun installValidModel() {
        files.directory.mkdirs()
        files.partFile.writeBytes(content)
        assertTrue(files.install())
    }

    @Test
    fun `startup decision table`() {
        assertEquals(StartupAction.READY, startupAction(fileReady = true, wanted = false, download = null))
        assertEquals(StartupAction.READY, startupAction(true, true, DownloadStatus.RUNNING))
        assertEquals(StartupAction.CLEAN_UP, startupAction(false, false, DownloadStatus.RUNNING))
        assertEquals(StartupAction.VERIFY, startupAction(false, true, DownloadStatus.SUCCESSFUL))
        for (active in listOf(DownloadStatus.PENDING, DownloadStatus.RUNNING, DownloadStatus.PAUSED)) {
            assertEquals(StartupAction.WATCH, startupAction(false, true, active))
        }
        assertEquals(StartupAction.RESTART, startupAction(false, true, DownloadStatus.FAILED))
        assertEquals(StartupAction.RESTART, startupAction(false, true, null))
    }

    @Test
    fun `download is wifi only by default, shows progress and installs a verified file`() = runTest {
        val downloader = downloader()
        downloader.download()
        val request = downloads.requests.single()
        assertFalse(request.allowMetered)
        assertEquals(files.partFile, request.destination)
        assertEquals(spec.url, request.url)
        assertEquals(100L, settings.modelDownloadId.first())
        assertTrue(settings.modelWanted.first())

        downloads.infos[100L] = DownloadInfo(DownloadStatus.PAUSED, 5, 24, Waiting.WIFI)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(ModelState.Downloading(5, 24, Waiting.WIFI), downloader.state.value)

        finish(100L)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(ModelState.Ready, downloader.state.value)
        assertTrue(files.isReady())
        assertEquals(files.modelFile, downloader.readyModelFile())
        assertNull(settings.modelDownloadId.first())
    }

    @Test
    fun `mobile data setting is passed to the download`() = runTest {
        settings.setModelAllowMetered(true)
        downloader().download()
        assertTrue(downloads.requests.single().allowMetered)
    }

    @Test
    fun `wrong checksum is rejected and deleted`() = runTest {
        val downloader = downloader()
        downloader.download()
        finish(100L, "a very small sloth modeL".toByteArray())
        advanceTimeBy(1_001); runCurrent()
        assertEquals(ModelState.Failed(FailReason.CHECKSUM_MISMATCH), downloader.state.value)
        assertFalse(files.partFile.exists())
        assertFalse(files.modelFile.exists())
        assertNull(downloader.readyModelFile())
    }

    @Test
    fun `failed download is reported and forgotten`() = runTest {
        val downloader = downloader()
        downloader.download()
        downloads.infos[100L] = DownloadInfo(DownloadStatus.FAILED, 3, 24)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(ModelState.Failed(FailReason.DOWNLOAD_FAILED), downloader.state.value)
        assertNull(settings.modelDownloadId.first())
        downloader.download()
        assertEquals(2, downloads.requests.size)
    }

    @Test
    fun `not enough space does not start a download`() = runTest {
        space = spec.sizeBytes
        val downloader = downloader()
        downloader.download()
        assertEquals(ModelState.Failed(FailReason.NOT_ENOUGH_SPACE), downloader.state.value)
        assertTrue(downloads.requests.isEmpty())
    }

    @Test
    fun `startup with a verified model is ready without downloading`() = runTest {
        val downloader = downloader()
        installValidModel()
        settings.setModelWanted(true)
        downloader.checkOnStartup()
        assertEquals(ModelState.Ready, downloader.state.value)
        assertTrue(downloads.requests.isEmpty())
    }

    @Test
    fun `startup deletes a tampered model and downloads it again`() = runTest {
        val downloader = downloader()
        installValidModel()
        settings.setModelWanted(true)
        files.modelFile.writeBytes("a very small sloth MODEL".toByteArray())
        files.modelFile.setLastModified(files.modelFile.lastModified() + 10_000)
        assertFalse(files.isReady())

        downloader.checkOnStartup()
        assertFalse(files.modelFile.exists())
        assertFalse(files.markerFile.exists())
        assertEquals(1, downloads.requests.size)
        assertEquals(ModelState.Downloading(0, spec.sizeBytes), downloader.state.value)
    }

    @Test
    fun `startup removes leftovers when the model is not wanted`() = runTest {
        val downloader = downloader()
        files.directory.mkdirs()
        files.partFile.writeBytes(content.copyOf(5))
        files.modelFile.writeBytes(content)
        downloader.checkOnStartup()
        assertEquals(ModelState.NotDownloaded, downloader.state.value)
        assertFalse(files.partFile.exists())
        assertFalse(files.modelFile.exists())
        assertTrue(downloads.requests.isEmpty())
    }

    @Test
    fun `startup resumes watching a download that is still running`() = runTest {
        killedAfterDownload()
        val restarted = downloader()
        downloads.infos[100L] = DownloadInfo(DownloadStatus.RUNNING, 12, 24)
        restarted.checkOnStartup()
        runCurrent()
        assertEquals(ModelState.Downloading(12, 24), restarted.state.value)
        assertEquals(1, downloads.requests.size)
    }

    @Test
    fun `startup verifies a download that finished while the app was closed`() = runTest {
        killedAfterDownload()
        val restarted = downloader()
        finish(100L)
        restarted.checkOnStartup()
        assertEquals(ModelState.Ready, restarted.state.value)
        assertTrue(files.isReady())
    }

    @Test
    fun `delete cancels the download and removes everything`() = runTest {
        val downloader = downloader()
        downloader.download()
        downloader.delete()
        assertEquals(listOf(100L), downloads.removed)
        assertEquals(ModelState.NotDownloaded, downloader.state.value)
        assertFalse(settings.modelWanted.first())
        assertNull(settings.modelDownloadId.first())

        installValidModel()
        downloader.delete()
        assertFalse(files.modelFile.exists())
        assertFalse(files.markerFile.exists())
    }

    @Test
    fun `marker must match the file`() = runTest {
        downloader()
        installValidModel()
        assertTrue(files.isReady())
        files.markerFile.writeText("""{"sha256":"00","sizeBytes":${spec.sizeBytes},"lastModified":${files.modelFile.lastModified()}}""")
        assertFalse(files.isReady())
        files.markerFile.writeText("not json")
        assertFalse(files.isReady())
        files.markerFile.delete()
        assertFalse(files.isReady())
    }
}
