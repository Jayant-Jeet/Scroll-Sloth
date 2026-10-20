package com.halfbloodcoder.scrollsloth.debug

import com.halfbloodcoder.scrollsloth.detect.Platform
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val serializer = SnapshotSerializer()
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = File(temporaryFolder.root, CaptureRepository.DIRECTORY_NAME)
    }

    private fun capture(platform: Platform, epochMs: Long): InspectorCapture {
        val result = serializer.snapshot(FakeNode(viewIdResourceName = "id/root"))
        return InspectorCapture(
            capturedAtEpochMs = epochMs,
            capturedAtUptimeMs = 1,
            platform = platform,
            app = TargetAppInfo(platform.packageName),
            device = DeviceInfo(36, 1080, 2400),
            nodeCount = result.nodeCount,
            truncated = result.truncated,
            viewIds = result.viewIds,
            recentEvents = emptyList(),
            root = result.root,
        )
    }

    @Test
    fun savesListsNewestFirstAndReadsBack() = runTest {
        val repository = CaptureRepository(directory, serializer, StandardTestDispatcher(testScheduler))

        val first = repository.save(capture(Platform.INSTAGRAM, 1_000))
        repository.save(capture(Platform.FACEBOOK, 2_000))

        assertEquals("capture-instagram-1000.json", first.fileName)
        assertEquals(listOf(Platform.FACEBOOK, Platform.INSTAGRAM), repository.captures.value.map { it.platform })
        assertTrue(repository.captures.value.all { it.sizeBytes > 0 })

        val file = repository.fileFor(first.fileName)
        assertNotNull(file)
        assertEquals(capture(Platform.INSTAGRAM, 1_000), serializer.decode(file!!.readText()))
        assertFalse(directory.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test
    fun sameTimestampGetsUniqueName() = runTest {
        val repository = CaptureRepository(directory, serializer, StandardTestDispatcher(testScheduler))

        val a = repository.save(capture(Platform.YOUTUBE, 5))
        val b = repository.save(capture(Platform.YOUTUBE, 5))

        assertEquals("capture-youtube-5.json", a.fileName)
        assertEquals("capture-youtube-5-1.json", b.fileName)
        assertEquals(2, repository.captures.value.size)
    }

    @Test
    fun deleteAndDeleteAll() = runTest {
        val repository = CaptureRepository(directory, serializer, StandardTestDispatcher(testScheduler))
        val a = repository.save(capture(Platform.YOUTUBE, 1))
        repository.save(capture(Platform.YOUTUBE, 2))
        repository.save(capture(Platform.FACEBOOK, 3))

        assertTrue(repository.delete(a.fileName))
        assertFalse(repository.delete(a.fileName))
        assertEquals(2, repository.captures.value.size)

        assertEquals(2, repository.deleteAll())
        assertTrue(repository.captures.value.isEmpty())
    }

    @Test
    fun refreshIgnoresUnrelatedFiles() = runTest {
        directory.mkdirs()
        File(directory, "notes.txt").writeText("hi")
        File(directory, "capture-tiktok-1.json").writeText("{}")
        val repository = CaptureRepository(directory, serializer, StandardTestDispatcher(testScheduler))

        repository.refresh()

        assertTrue(repository.captures.value.isEmpty())
    }

    @Test
    fun fileForRejectsTraversalAndUnknownNames() = runTest {
        val repository = CaptureRepository(directory, serializer, StandardTestDispatcher(testScheduler))
        repository.save(capture(Platform.INSTAGRAM, 7))
        File(temporaryFolder.root, "secret.json").writeText("x")

        assertNotNull(repository.fileFor("capture-instagram-7.json"))
        assertNull(repository.fileFor("../secret.json"))
        assertNull(repository.fileFor("capture-instagram-7.json/../../secret.json"))
        assertNull(repository.fileFor("capture-instagram-8.json"))
        assertNull(repository.fileFor(""))
        assertFalse(repository.delete("../secret.json"))
        assertTrue(File(temporaryFolder.root, "secret.json").exists())
    }
}
