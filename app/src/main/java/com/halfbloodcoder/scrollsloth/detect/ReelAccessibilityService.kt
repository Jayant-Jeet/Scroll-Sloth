package com.halfbloodcoder.scrollsloth.detect

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.halfbloodcoder.scrollsloth.ScrollSlothApplication
import com.halfbloodcoder.scrollsloth.debug.AccessibilityNodeAdapter
import com.halfbloodcoder.scrollsloth.debug.CaptureOutcome
import com.halfbloodcoder.scrollsloth.debug.CalibrationState
import com.halfbloodcoder.scrollsloth.debug.FloatingButtonOverlay
import com.halfbloodcoder.scrollsloth.debug.DeviceInfo
import com.halfbloodcoder.scrollsloth.debug.EventRecord
import com.halfbloodcoder.scrollsloth.debug.InspectorClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReelAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as ScrollSlothApplication).container }
    private var overlay: FloatingButtonOverlay? = null
    private var calibrationOverlay: FloatingButtonOverlay? = null
    private var capturing = false

    // Detection state below is only touched on container.detectionDispatcher.
    private val detectionScope by lazy { CoroutineScope(SupervisorJob() + container.detectionDispatcher) }
    private var evaluationJob: Job? = null
    private var presenceJob: Job? = null
    private var missingRootStreak = 0

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) resetDetector(ResetReason.SCREEN_OFF)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val captureOverlay = FloatingButtonOverlay(
            context = this,
            label = CAPTURE_LABEL,
            description = "Capture inspector snapshot",
            color = 0xE6386A55.toInt(),
            topOffsetDp = 180,
            debounceMs = 1_000,
            onClick = ::captureNow,
        )
        overlay = captureOverlay
        val plusOneOverlay = FloatingButtonOverlay(
            context = this,
            label = "+1",
            description = "Count one reel by hand",
            color = 0xE65A3E8C.toInt(),
            topOffsetDp = 320,
            // Short, so honest fast taps all count, but a double-tap bounce does not.
            debounceMs = 250,
            alignStart = true,
            onClick = { container.calibrationSession.plusOne() },
        )
        calibrationOverlay = plusOneOverlay
        scope.launch {
            container.calibrationSession.state.collect { state ->
                if (state is CalibrationState.Running) {
                    plusOneOverlay.show()
                    plusOneOverlay.setText("+1  ·  ${state.manualCount}")
                } else {
                    plusOneOverlay.hide()
                }
            }
        }
        scope.launch {
            container.settingsStore.inspectorMode.collect { enabled ->
                container.ruleInspector.setEnabled(enabled)
                if (enabled) captureOverlay.show() else captureOverlay.hide()
            }
        }
        scope.launch {
            container.settingsStore.trackingPaused.collect { paused ->
                container.appScope.launch(container.detectionDispatcher) { container.reelDetector.setPaused(paused) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val inspector = container.ruleInspector
        if (inspector.enabled) inspector.onEvent(event.toRecord())
        if (Platform.fromPackageName(event.packageName?.toString()) != null) scheduleEvaluation()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        resetDetector(ResetReason.SERVICE_DISCONNECTED)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        calibrationOverlay?.hide()
        calibrationOverlay = null
        container.ruleInspector.setEnabled(false)
        runCatching { unregisterReceiver(screenOffReceiver) }
        detectionScope.cancel()
        resetDetector(ResetReason.SERVICE_DISCONNECTED)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Events arrive in bursts while a video plays; evaluate the tree at most once per
     * [EVALUATION_DELAY_MS], after the burst has had a moment to settle.
     */
    private fun scheduleEvaluation() {
        if (evaluationJob?.isActive == true) return
        evaluationJob = detectionScope.launch {
            delay(EVALUATION_DELAY_MS)
            evaluateNow()
        }
    }

    /**
     * The service only receives events from the three supported apps, so leaving them is silent.
     * While a reel is open, poll the active window so the reel ends when the user goes elsewhere.
     */
    private fun ensurePresenceCheck() {
        if (presenceJob?.isActive == true || !container.reelDetector.hasCurrentReel) return
        presenceJob = detectionScope.launch {
            while (isActive && container.reelDetector.hasCurrentReel) {
                delay(PRESENCE_CHECK_MS)
                evaluateNow()
            }
        }
    }

    private fun evaluateNow() {
        val detector = container.reelDetector
        val root = try {
            rootInActiveWindow
        } catch (_: RuntimeException) {
            null
        }
        val packageName = root?.packageName?.toString()
        when {
            root == null -> {
                // Briefly null during transitions; two in a row means the app is gone.
                if (++missingRootStreak >= 2) detector.onOtherAppInForeground()
            }
            packageName == null || Platform.fromPackageName(packageName) == null -> {
                missingRootStreak = 0
                detector.onOtherAppInForeground()
            }
            else -> {
                missingRootStreak = 0
                detector.onWindow(packageName, AccessibilityNodeAdapter(root))
            }
        }
        ensurePresenceCheck()
    }

    /** Runs on the app scope so the open reel is still recorded after this service is gone. */
    private fun resetDetector(reason: ResetReason) {
        container.appScope.launch(container.detectionDispatcher) { container.reelDetector.reset(reason) }
    }

    private fun captureNow() {
        if (capturing) return
        capturing = true
        overlay?.setBusy(true, "Capturing…")
        scope.launch {
            try {
                val root = findSupportedRoot()
                val outcome = container.ruleInspector.capture(
                    root = root?.let(::AccessibilityNodeAdapter),
                    packageName = root?.packageName?.toString(),
                    appInfo = packageManager::targetAppInfo,
                    device = deviceInfo(),
                    clock = InspectorClock(System::currentTimeMillis, SystemClock::uptimeMillis),
                )
                toast(outcome.message())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast("Capture failed. Try again once the screen settles.")
            } finally {
                capturing = false
                overlay?.setBusy(false, CAPTURE_LABEL)
            }
        }
    }

    /**
     * Prefers the active window; falls back to any interactive window owned by a supported app,
     * in case tapping the overlay moved focus. The fallback needs flagRetrieveInteractiveWindows.
     */
    private fun findSupportedRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (Platform.fromPackageName(active?.packageName?.toString()) != null) return active
        return windows.asSequence()
            .mapNotNull { it.root }
            .firstOrNull { Platform.fromPackageName(it.packageName?.toString()) != null }
            ?: active
    }

    private fun deviceInfo(): DeviceInfo {
        val windowManager = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            DeviceInfo(Build.VERSION.SDK_INT, bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            DeviceInfo(Build.VERSION.SDK_INT, metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun CaptureOutcome.message(): String = when (this) {
        CaptureOutcome.Disabled -> "Inspector mode is off."
        CaptureOutcome.NoWindow -> "No window to capture yet."
        is CaptureOutcome.UnsupportedApp -> "Open Instagram, YouTube, or Facebook first."
        is CaptureOutcome.Saved ->
            "Saved ${summary.platform.label} capture ($nodeCount nodes${if (truncated) ", truncated" else ""})."
    }
}

private const val CAPTURE_LABEL = "Capture"
private const val EVALUATION_DELAY_MS = 250L
private const val PRESENCE_CHECK_MS = 1_000L

private fun AccessibilityEvent.toRecord(): EventRecord {
    val scrolled = eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    return EventRecord.create(
        eventTimeMs = eventTime,
        type = AccessibilityEvent.eventTypeToString(eventType),
        packageName = packageName?.toString(),
        className = className?.toString(),
        scrollDeltaX = if (scrolled) scrollDeltaX else null,
        scrollDeltaY = if (scrolled) scrollDeltaY else null,
        fromIndex = fromIndex,
        toIndex = toIndex,
        itemCount = itemCount,
    )
}
