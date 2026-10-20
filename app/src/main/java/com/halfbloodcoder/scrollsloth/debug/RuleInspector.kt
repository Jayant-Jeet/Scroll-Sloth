package com.halfbloodcoder.scrollsloth.debug

import com.halfbloodcoder.scrollsloth.detect.Platform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CaptureOutcome {
    data object Disabled : CaptureOutcome
    data object NoWindow : CaptureOutcome
    data class UnsupportedApp(val packageName: String?) : CaptureOutcome
    data class Saved(val summary: CaptureSummary, val nodeCount: Int, val truncated: Boolean) : CaptureOutcome
}

class InspectorClock(
    val epochMs: () -> Long,
    val uptimeMs: () -> Long,
)

/** Records recent events and captures structure-only snapshots while Inspector mode is on. */
class RuleInspector(
    private val serializer: SnapshotSerializer,
    private val repository: CaptureRepository,
    private val eventLog: EventLog = EventLog(),
    private val traversalDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Volatile
    var enabled: Boolean = false
        private set

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) eventLog.clear()
    }

    fun onEvent(event: EventRecord) {
        if (enabled && Platform.fromPackageName(event.packageName) != null) eventLog.add(event)
    }

    fun recentEvents(): List<EventRecord> = eventLog.snapshot()

    suspend fun capture(
        root: InspectableNode?,
        packageName: String?,
        appInfo: (String) -> TargetAppInfo,
        device: DeviceInfo,
        clock: InspectorClock,
    ): CaptureOutcome {
        if (!enabled) return CaptureOutcome.Disabled
        if (root == null) return CaptureOutcome.NoWindow
        val platform = Platform.fromPackageName(packageName)
            ?: return CaptureOutcome.UnsupportedApp(packageName)

        val capturedAtUptimeMs = clock.uptimeMs()
        val capturedAtEpochMs = clock.epochMs()
        val events = eventLog.snapshot()
        val result = withContext(traversalDispatcher) { serializer.snapshot(root) }
        val capture = InspectorCapture(
            capturedAtEpochMs = capturedAtEpochMs,
            capturedAtUptimeMs = capturedAtUptimeMs,
            platform = platform,
            app = appInfo(platform.packageName),
            device = device,
            nodeCount = result.nodeCount,
            truncated = result.truncated,
            viewIds = result.viewIds,
            recentEvents = events,
            root = result.root,
        )
        return CaptureOutcome.Saved(repository.save(capture), result.nodeCount, result.truncated)
    }
}
