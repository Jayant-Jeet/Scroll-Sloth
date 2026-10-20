package com.reelsloth.data

import com.reelsloth.detect.Clock
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The user's own data: export it as CSV, or erase it. Settings, the downloaded model and the
 * detection rules are kept; they are not about the user.
 */
class UserData(
    private val reelDao: ReelDao,
    private val commentaryDao: CommentaryDao,
    private val exportDirectory: File,
    private val deleteCaptures: suspend () -> Unit,
    private val deleteCalibration: suspend () -> Unit = {},
    private val clock: Clock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Writes every reel event to a fresh CSV in [exportDirectory], replacing older exports. */
    suspend fun exportCsv(): File {
        val events = reelDao.allEvents()
        return withContext(ioDispatcher) {
            clearExports()
            exportDirectory.mkdirs()
            val date = Instant.ofEpochMilli(clock.nowMs()).atZone(zone()).toLocalDate()
            File(exportDirectory, "scroll-sloth-reels-$date.csv").also { file ->
                file.bufferedWriter().use { CsvExporter.write(events, zone(), it) }
            }
        }
    }

    /** Deletes reel events, cached commentary, Rule Inspector captures, the calibration log and old exports. */
    suspend fun eraseAll() {
        reelDao.deleteAll()
        commentaryDao.deleteAll()
        deleteCaptures()
        deleteCalibration()
        withContext(ioDispatcher) { clearExports() }
    }

    private fun clearExports() {
        exportDirectory.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val EXPORT_DIRECTORY = "exports"
    }
}
