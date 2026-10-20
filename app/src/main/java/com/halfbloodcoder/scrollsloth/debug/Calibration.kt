package com.halfbloodcoder.scrollsloth.debug

import com.halfbloodcoder.scrollsloth.data.CsvExporter
import com.halfbloodcoder.scrollsloth.detect.AppStatus
import com.halfbloodcoder.scrollsloth.detect.Clock
import com.halfbloodcoder.scrollsloth.detect.Platform
import com.halfbloodcoder.scrollsloth.detect.ReelViewed
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One calibration run: what the detector counted next to what the user counted by hand. */
data class CalibrationResult(
    val platform: Platform,
    val startedAtMs: Long,
    val durationSec: Long,
    val autoCount: Int,
    val manualCount: Int,
    val rulesVersion: Int,
    val appVersionName: String?,
) {
    /** Positive when the detector over-counts, negative when it misses reels. */
    val difference: Int get() = autoCount - manualCount

    val absoluteError: Int get() = abs(difference)

    /**
     * Absolute error as a percentage of the hand count. Zero when both counts are zero; null when
     * nothing was counted by hand but the detector counted something (no meaningful percentage).
     */
    val percentError: Double?
        get() = when {
            manualCount > 0 -> absoluteError * 100.0 / manualCount
            autoCount == 0 -> 0.0
            else -> null
        }
}

/** "12.5%", or "n/a" when there is no meaningful percentage. One decimal, dot separator. */
fun CalibrationResult.formatPercentError(): String =
    percentError?.let { String.format(java.util.Locale.ROOT, "%.1f%%", it) } ?: "n/a"

/** One plain sentence about which way the detector was off. */
fun CalibrationResult.verdict(): String = when {
    difference == 0 -> "Spot on."
    difference > 0 -> "The detector counted $absoluteError more than you did."
    else -> "The detector missed $absoluteError that you counted."
}

/** Why a calibration run cannot start for an app, or null when it can. */
object CalibrationGate {
    fun reasonNotReady(status: AppStatus?, trackingPaused: Boolean): String? = when {
        trackingPaused -> "Counting is paused. Resume it in Settings first."
        status == AppStatus.NEEDS_CALIBRATION -> "This app has no working rules yet, so the detector would count nothing."
        status == AppStatus.PERMISSION_MISSING -> "Turn on the accessibility service first."
        status == null -> "Unknown app."
        else -> null
    }
}

sealed interface CalibrationState {
    data class Idle(val lastResult: CalibrationResult? = null) : CalibrationState

    data class Running(
        val platform: Platform,
        val startedAtMs: Long,
        val autoCount: Int = 0,
        val manualCount: Int = 0,
    ) : CalibrationState
}

/**
 * Counts reels for one platform two ways at once: [onReelViewed] from the detector, [plusOne] from
 * the user. [flushDetector] closes any open reel (as if the user left the app) so a reel that
 * started before Start, or is still open at Stop, lands on the right side of the boundary.
 * Thread-safe: the detector reports from its own dispatcher while the user taps on the main thread.
 */
class CalibrationSession(
    private val clock: Clock,
    private val flushDetector: suspend () -> Unit,
    private val rulesVersion: () -> Int,
    private val appVersionName: (Platform) -> String?,
) {
    private val mutableState = MutableStateFlow<CalibrationState>(CalibrationState.Idle())
    val state: StateFlow<CalibrationState> = mutableState.asStateFlow()
    private val transitions = Mutex()

    /** Returns false if a run is already going. */
    suspend fun start(platform: Platform): Boolean = transitions.withLock {
        if (mutableState.value is CalibrationState.Running) return false
        flushDetector()
        mutableState.value = CalibrationState.Running(platform, clock.nowMs())
        true
    }

    fun plusOne() = updateRunning { it.copy(manualCount = it.manualCount + 1) }

    /** Undoes a mistaken tap. */
    fun minusOne() = updateRunning { it.copy(manualCount = (it.manualCount - 1).coerceAtLeast(0)) }

    fun onReelViewed(event: ReelViewed) = updateRunning {
        if (event.platform == it.platform) it.copy(autoCount = it.autoCount + 1) else it
    }

    /** Ends the run and returns its result, or null if nothing was running. */
    suspend fun stop(): CalibrationResult? = transitions.withLock {
        val platform = (mutableState.value as? CalibrationState.Running)?.platform ?: return null
        flushDetector()
        val now = clock.nowMs()
        val version = rulesVersion()
        val appVersion = appVersionName(platform)
        var result: CalibrationResult? = null
        // update{} so a tap or detector count arriving right now is not lost.
        mutableState.update { current ->
            if (current !is CalibrationState.Running) return@update current
            val finished = CalibrationResult(
                platform = current.platform,
                startedAtMs = current.startedAtMs,
                durationSec = (now - current.startedAtMs).coerceAtLeast(0) / 1_000,
                autoCount = current.autoCount,
                manualCount = current.manualCount,
                rulesVersion = version,
                appVersionName = appVersion,
            )
            result = finished
            CalibrationState.Idle(finished)
        }
        result
    }

    /** Throws the run away without a result. */
    fun cancel() = mutableState.update { if (it is CalibrationState.Running) CalibrationState.Idle() else it }

    private fun updateRunning(change: (CalibrationState.Running) -> CalibrationState.Running) =
        mutableState.update { if (it is CalibrationState.Running) change(it) else it }
}

/**
 * Local CSV of calibration runs, one row per run, appended on Stop. Holds counts, versions and
 * times only; nothing about the reels themselves.
 */
class CalibrationLog(
    private val file: File,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val writes = Mutex()

    suspend fun append(result: CalibrationResult) = writes.withLock {
        withContext(ioDispatcher) {
            file.parentFile?.mkdirs()
            val newFile = !file.exists() || file.length() == 0L
            file.appendText((if (newFile) HEADER + "\n" else "") + row(result, zone()) + "\n")
        }
    }

    /** Number of runs logged so far. */
    suspend fun count(): Int = withContext(ioDispatcher) {
        if (!file.exists()) 0 else file.useLines { lines -> (lines.count { it.isNotBlank() } - 1).coerceAtLeast(0) }
    }

    /** Copies the log into [directory] for sharing; null when there is nothing logged yet. */
    suspend fun exportTo(directory: File): File? = writes.withLock {
        withContext(ioDispatcher) {
            if (!file.exists() || file.length() == 0L) return@withContext null
            directory.mkdirs()
            file.copyTo(File(directory, EXPORT_NAME), overwrite = true)
        }
    }

    suspend fun delete() = writes.withLock { withContext(ioDispatcher) { file.delete() } }

    companion object {
        const val DIRECTORY = "calibration"
        const val FILE_NAME = "calibration.csv"
        const val EXPORT_NAME = "scroll-sloth-calibration.csv"
        const val HEADER = "started_at,platform,auto,manual,duration_sec,rules_version,app_version_name"

        fun row(result: CalibrationResult, zone: ZoneId): String = listOf(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(result.startedAtMs).atZone(zone)),
            result.platform.name,
            result.autoCount.toString(),
            result.manualCount.toString(),
            result.durationSec.toString(),
            result.rulesVersion.toString(),
            result.appVersionName.orEmpty(),
        ).joinToString(",") { CsvExporter.cell(it) }
    }
}
