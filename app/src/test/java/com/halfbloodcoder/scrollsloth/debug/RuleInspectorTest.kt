package com.halfbloodcoder.scrollsloth.debug

import com.halfbloodcoder.scrollsloth.detect.Platform
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuleInspectorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val device = DeviceInfo(36, 1080, 2400)
    private val clock = InspectorClock(epochMs = { 42_000 }, uptimeMs = { 7 })
    private val appInfo: (String) -> TargetAppInfo = { TargetAppInfo(it, "1.0", 1) }

    private fun TestScope.newInspector(): Pair<RuleInspector, CaptureRepository> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val serializer = SnapshotSerializer()
        val repository = CaptureRepository(temporaryFolder.root, serializer, dispatcher)
        return RuleInspector(serializer, repository, EventLog(), dispatcher) to repository
    }

    private fun event(pkg: String, time: Long = 1) =
        EventRecord(eventTimeMs = time, type = "TYPE_VIEW_SCROLLED", packageName = pkg)

    @Test
    fun captureIsRefusedWhileDisabled() = runTest {
        val (inspector, _) = newInspector()
        assertEquals(
            CaptureOutcome.Disabled,
            inspector.capture(FakeNode(), Platform.INSTAGRAM.packageName, appInfo, device, clock),
        )
    }

    @Test
    fun reportsMissingWindowAndUnsupportedApp() = runTest {
        val (inspector, _) = newInspector()
        inspector.setEnabled(true)

        assertEquals(CaptureOutcome.NoWindow, inspector.capture(null, null, appInfo, device, clock))
        assertEquals(
            CaptureOutcome.UnsupportedApp("com.android.systemui"),
            inspector.capture(FakeNode(), "com.android.systemui", appInfo, device, clock),
        )
    }

    @Test
    fun savesCaptureWithRecentEventsAndMetadata() = runTest {
        val (inspector, repository) = newInspector()
        inspector.setEnabled(true)
        inspector.onEvent(event(Platform.YOUTUBE.packageName, 1))
        inspector.onEvent(event("com.android.systemui", 2))
        inspector.onEvent(event(Platform.YOUTUBE.packageName, 3))

        val outcome = inspector.capture(
            FakeNode(viewIdResourceName = "id/a", children = listOf(FakeNode())),
            Platform.YOUTUBE.packageName,
            appInfo,
            device,
            clock,
        )

        assertTrue(outcome is CaptureOutcome.Saved)
        outcome as CaptureOutcome.Saved
        assertEquals(2, outcome.nodeCount)
        assertEquals("capture-youtube-42000.json", outcome.summary.fileName)

        val saved = SnapshotSerializer().decode(repository.fileFor(outcome.summary.fileName)!!.readText())
        assertEquals(Platform.YOUTUBE, saved.platform)
        assertEquals(42_000L, saved.capturedAtEpochMs)
        assertEquals(7L, saved.capturedAtUptimeMs)
        assertEquals(TargetAppInfo(Platform.YOUTUBE.packageName, "1.0", 1), saved.app)
        assertEquals(device, saved.device)
        assertEquals(listOf("id/a"), saved.viewIds)
        assertEquals(listOf(1L, 3L), saved.recentEvents.map { it.eventTimeMs })
    }

    @Test
    fun eventsAreIgnoredWhileDisabledAndClearedOnDisable() = runTest {
        val (inspector, _) = newInspector()
        inspector.onEvent(event(Platform.FACEBOOK.packageName))
        assertTrue(inspector.recentEvents().isEmpty())

        inspector.setEnabled(true)
        inspector.onEvent(event(Platform.FACEBOOK.packageName))
        assertEquals(1, inspector.recentEvents().size)

        inspector.setEnabled(false)
        assertTrue(inspector.recentEvents().isEmpty())
    }
}
