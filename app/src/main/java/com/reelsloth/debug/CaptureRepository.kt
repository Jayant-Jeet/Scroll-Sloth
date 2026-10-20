package com.reelsloth.debug

import com.reelsloth.detect.Platform
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class CaptureSummary(
    val fileName: String,
    val platform: Platform,
    val capturedAtEpochMs: Long,
    val sizeBytes: Long,
)

/** Stores inspector captures as JSON files in app-private storage. */
class CaptureRepository(
    private val directory: File,
    private val serializer: SnapshotSerializer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableCaptures = MutableStateFlow<List<CaptureSummary>>(emptyList())
    val captures: StateFlow<List<CaptureSummary>> = mutableCaptures.asStateFlow()

    suspend fun refresh() {
        mutableCaptures.value = withContext(ioDispatcher) { loadSummaries() }
    }

    suspend fun save(capture: InspectorCapture): CaptureSummary {
        val summary = withContext(ioDispatcher) {
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Could not create capture directory")
            }
            val file = uniqueFile(capture.platform, capture.capturedAtEpochMs)
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeText(serializer.encode(capture))
            if (!temporary.renameTo(file)) {
                temporary.delete()
                throw IOException("Could not save capture")
            }
            checkNotNull(summaryOf(file))
        }
        refresh()
        return summary
    }

    suspend fun delete(fileName: String): Boolean {
        val deleted = withContext(ioDispatcher) { fileFor(fileName)?.delete() ?: false }
        refresh()
        return deleted
    }

    suspend fun deleteAll(): Int {
        val deleted = withContext(ioDispatcher) {
            loadSummaries().count { File(directory, it.fileName).delete() }
        }
        refresh()
        return deleted
    }

    /** Returns a capture file only for names this repository created, preventing path traversal. */
    fun fileFor(fileName: String): File? {
        if (FILE_NAME.matchEntire(fileName) == null) return null
        return File(directory, fileName).takeIf { it.isFile }
    }

    private fun loadSummaries(): List<CaptureSummary> =
        directory.listFiles().orEmpty()
            .mapNotNull(::summaryOf)
            .sortedWith(
                compareByDescending<CaptureSummary> { it.capturedAtEpochMs }.thenByDescending { it.fileName },
            )

    private fun uniqueFile(platform: Platform, epochMs: Long): File {
        var suffix = 0
        while (true) {
            val file = File(directory, fileName(platform, epochMs, suffix))
            if (!file.exists()) return file
            suffix++
        }
    }

    private fun summaryOf(file: File): CaptureSummary? {
        if (!file.isFile) return null
        val match = FILE_NAME.matchEntire(file.name) ?: return null
        val platform = Platform.entries.firstOrNull { it.name.lowercase() == match.groupValues[1] } ?: return null
        val epochMs = match.groupValues[2].toLongOrNull() ?: return null
        return CaptureSummary(file.name, platform, epochMs, file.length())
    }

    companion object {
        const val DIRECTORY_NAME = "inspector_captures"
        private val FILE_NAME = Regex("""capture-([a-z]+)-(\d+)(?:-(\d+))?\.json""")

        fun fileName(platform: Platform, epochMs: Long, suffix: Int = 0): String {
            val base = "capture-${platform.name.lowercase()}-$epochMs"
            return if (suffix == 0) "$base.json" else "$base-$suffix.json"
        }
    }
}
